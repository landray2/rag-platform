package com.landray.rag.rpc.server;

import com.landray.rag.rpc.RpcServer;
import com.landray.rag.rpc.codec.RpcProtocolDecoder;
import com.landray.rag.rpc.codec.RpcProtocolEncoder;
import com.landray.rag.rpc.handler.RpcRequestHandler;
import com.landray.rag.rpc.handler.ServerHeartbeatHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Netty 的 RPC 服务端
 *
 * 线程模型：【面试·高频】Reactor 主从多线程模型
 * - boss Group（1 线程）：只负责 accept 新连接，注册到 worker；
 * - worker Group（默认 2×CPU 核数）：每个 EventLoop 绑定若干 Channel，负责其全部读写事件；
 * - 业务执行：不在 EventLoop 中，派发到虚拟线程池（见 RpcRequestHandler）。
 *
 * pipeline 顺序（入站自上而下，出站自下而上）：
 *   RpcProtocolDecoder → ServerHeartbeatHandler → RpcRequestHandler（+ 出站 RpcProtocolEncoder）
 */
@Slf4j
public class NettyRpcServer implements RpcServer {

    /** 服务实例表：interfaceName → 接口 Class + 实现 */
    private final Map<String, RpcRequestHandler.ServiceHolder> services = new ConcurrentHashMap<>();

    private final RpcRequestHandler requestHandler = new RpcRequestHandler(services);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @Override
    public void export(Class<?> serviceInterface, Object serviceImpl) {
        String name = serviceInterface.getName();
        services.put(name, new RpcRequestHandler.ServiceHolder(serviceInterface, serviceImpl));
        log.info("[RPC] 暴露服务: {} -> {}", name, serviceImpl.getClass().getName());
    }

    @Override
    public void start(int port) {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // 【面试】backlog：内核全连接队列长度，突发建连时可缓存的未 accept 连接数
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.TCP_NODELAY, true)   // 禁用 Nagle，小包立即发送，降低 RPC 延迟
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    // 1. 粘包/拆包：按协议头 BodyLen 切帧
                                    .addLast("decoder", new RpcProtocolDecoder())
                                    // 2. 出站编码：RpcProtocol → 字节流
                                    .addLast("encoder", new RpcProtocolEncoder())
                                    // 3. 空闲检测：90s 读不到数据则关闭（防客户端假死）
                                    .addLast("idle", new IdleStateHandler(90, 0, 0, TimeUnit.SECONDS))
                                    // 4. 空闲事件处理
                                    .addLast("heartbeat", new ServerHeartbeatHandler())
                                    // 5. 业务分发（内部切虚拟线程）
                                    .addLast("handler", requestHandler);
                        }
                    });

            Channel channel = bootstrap.bind(port).syncUninterruptibly().channel();
            this.serverChannel = channel;
            log.info("[RPC] Netty 服务端启动成功, 端口: {}", port);
        } catch (Exception e) {
            shutdown();
            throw new RuntimeException("Netty 服务端启动失败, 端口: " + port, e);
        }
    }

    @Override
    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 3, TimeUnit.SECONDS);
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 3, TimeUnit.SECONDS);
            workerGroup = null;
        }
        log.info("[RPC] Netty 服务端已关闭");
    }

    /** 已暴露的服务名（供注册中心批量注册） */
    public Set<String> exportedServices() {
        return services.keySet();
    }
}
