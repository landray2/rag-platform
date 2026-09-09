package com.landray.rag.rpc.client;

import com.landray.rag.rpc.LoadBalancer;
import com.landray.rag.rpc.Registry;
import com.landray.rag.rpc.RpcClient;
import com.landray.rag.rpc.message.MessageType;
import com.landray.rag.rpc.message.RpcProtocol;
import com.landray.rag.rpc.message.RpcRequest;
import com.landray.rag.rpc.message.RpcResponse;
import com.landray.rag.rpc.codec.RpcProtocolDecoder;
import com.landray.rag.rpc.codec.RpcProtocolEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Netty 的 RPC 客户端
 *
 * 核心设计：
 * 1. 连接复用：同一节点只建一条 TCP 连接，全部请求多路复用（requestId 配对响应）；
 * 2. 异步转同步：pendingRequests 表 + CompletableFuture（见 RpcResponseHandler）；
 * 3. 失败切换：单节点连接失败自动尝试下一个节点（最多 3 次）；
 * 4. 心跳保活：25s 写空闲发 PING，探测链路并防止被服务端的 90s 读空闲判定踢掉。
 */
@Slf4j
public class NettyRpcClient implements RpcClient {

    /** 等待响应的请求表：requestId → Future */
    private final ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> pendingRequests =
            new ConcurrentHashMap<>();

    /** 连接缓存：host:port → Channel */
    private final Map<String, Channel> channelCache = new ConcurrentHashMap<>();

    /** 请求 ID 生成器 */
    private final AtomicLong idGenerator = new AtomicLong(0);

    private final Registry registry;
    private final LoadBalancer defaultLoadBalancer;
    private final EventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;
    /** Kryo 非线程安全 → 内部 ThreadLocal 隔离，单实例可全局复用 */
    private final com.landray.rag.rpc.serialize.KryoSerializer serializer =
            new com.landray.rag.rpc.serialize.KryoSerializer();

    public NettyRpcClient(Registry registry, LoadBalancer defaultLoadBalancer) {
        this.registry = registry;
        this.defaultLoadBalancer = defaultLoadBalancer;
        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("decoder", new RpcProtocolDecoder())
                                .addLast("encoder", new RpcProtocolEncoder())
                                // 25s 写空闲 → 触发 PING（客户端心跳发起方）
                                .addLast("idle", new IdleStateHandler(0, 25, 0, TimeUnit.SECONDS))
                                .addLast("handler", new RpcResponseHandler(pendingRequests,
                                        () -> sendPing(ch)));
                    }
                });
    }

    @Override
    public <T> T createProxy(Class<T> serviceClass, long timeoutMs) {
        throw new UnsupportedOperationException("请通过 RpcClientProxy.createProxy 使用");
    }

    @Override
    public CompletableFuture<Object> asyncCall(String serviceName, String methodName,
                                               Object[] args, long timeoutMs) {
        String[] paramTypes = new String[args == null ? 0 : args.length];
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i] == null ? Object.class.getName() : args[i].getClass().getName();
            }
        }
        RpcRequest request = RpcRequest.builder()
                .requestId(nextRequestId())
                .interfaceName(serviceName)
                .version("1.0.0")
                .methodName(methodName)
                .parameterTypes(paramTypes)
                .args(args)
                .build();
        return sendRequest(request, timeoutMs, defaultLoadBalancer)
                .thenApply(rpcResponse -> {
                    if (rpcResponse.getCode() != RpcResponse.CODE_SUCCESS) {
                        throw new com.landray.rag.rpc.RpcRemoteException(
                                Objects.requireNonNullElse(rpcResponse.getException(),
                                        new com.landray.rag.rpc.message.RpcExceptionInfo(
                                                "RpcRemoteException", rpcResponse.getMessage(), "")));
                    }
                    return rpcResponse.getData();
                });
    }

    /**
     * 发送请求（支持指定负载均衡器，供 @RpcReference(loadBalancer="...") 逐引用定制）
     */
    public CompletableFuture<RpcResponse> sendRequest(RpcRequest request, long timeoutMs,
                                                      LoadBalancer loadBalancer) {
        String serviceName = request.getInterfaceName();
        List<LoadBalancer.RpcNode> nodes = registry.discover(serviceName);
        if (nodes == null || nodes.isEmpty()) {
            CompletableFuture<RpcResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(
                    new IllegalStateException("无可用服务节点: " + serviceName + "（检查服务是否已注册）"));
            return failed;
        }

        // 失败切换：最多尝试 3 个节点
        int maxAttempts = Math.min(3, nodes.size());
        List<LoadBalancer.RpcNode> remaining = nodes;
        Throwable lastError = null;

        for (int i = 0; i < maxAttempts; i++) {
            LoadBalancer.RpcNode node = loadBalancer.select(serviceName, remaining, null);
            if (node == null) {
                break;
            }
            try {
                Channel channel = getOrCreateChannel(node);
                return doSend(channel, request, timeoutMs);
            } catch (Exception e) {
                lastError = e;
                log.warn("[RPC] 节点调用失败, 切换重试: {}:{}, 原因: {}",
                        node.host(), node.port(), e.getMessage());
                channelCache.remove(node.host() + ":" + node.port());
                remaining = nodes.stream().filter(n -> n != node).toList();
            }
        }

        CompletableFuture<RpcResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException(
                "RPC 调用失败（已尝试 " + maxAttempts + " 个节点）: " + serviceName, lastError));
        return failed;
    }

    /** 注册 pending Future 并写出请求 */
    private CompletableFuture<RpcResponse> doSend(Channel channel, RpcRequest request, long timeoutMs) {
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(request.getRequestId(), future);
        // orTimeout：超时后 future 以 TimeoutException 完成并从 pending 表清理
        future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .whenComplete((r, e) -> pendingRequests.remove(request.getRequestId()));

        RpcProtocol protocol = RpcProtocol.request(
                RpcProtocol.SERIALIZER_KRYO, request.getRequestId(), serializer.serialize(request));
        channel.writeAndFlush(protocol).addListener(f -> {
            if (!f.isSuccess()) {
                // 写失败（连接已断）：直接失败，不再等超时
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    /** 发送心跳 PING */
    private void sendPing(Channel channel) {
        if (channel.isActive()) {
            channel.writeAndFlush(RpcProtocol.ping(nextRequestId()));
        }
    }

    /** 获取或建立到指定节点的连接（缓存复用） */
    private Channel getOrCreateChannel(LoadBalancer.RpcNode node) throws InterruptedException {
        String key = node.host() + ":" + node.port();
        Channel cached = channelCache.get(key);
        if (cached != null && cached.isActive()) {
            return cached;
        }
        channelCache.remove(key);
        Channel channel = bootstrap.connect(node.host(), node.port()).sync().channel();
        // 连接关闭时清理缓存，下次调用自动重连
        channel.closeFuture().addListener(f -> channelCache.remove(key));
        channelCache.put(key, channel);
        log.info("[RPC] 建立连接: {}", key);
        return channel;
    }

    /** 生成全局唯一请求 ID */
    public long nextRequestId() {
        return idGenerator.incrementAndGet();
    }

    @Override
    public void shutdown() {
        channelCache.values().forEach(Channel::close);
        channelCache.clear();
        eventLoopGroup.shutdownGracefully(0, 3, TimeUnit.SECONDS);
        log.info("[RPC] 客户端已关闭");
    }
}
