package com.landray.rag.rpc.handler;

import com.landray.rag.rpc.message.RpcExceptionInfo;
import com.landray.rag.rpc.message.RpcProtocol;
import com.landray.rag.rpc.message.RpcRequest;
import com.landray.rag.rpc.message.RpcResponse;
import com.landray.rag.rpc.serialize.KryoSerializer;
import com.landray.rag.rpc.util.TypeUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * RPC 请求处理器（服务端业务核心）
 *
 * 职责：
 * 1. 心跳：PING → 回 PONG；
 * 2. 请求：按 interfaceName 查本地服务表 → 反射调用 → 写回响应；
 * 3. 业务执行在【虚拟线程】中，不阻塞 Netty 的 EventLoop。
 *
 * 【面试·高频】为什么业务逻辑不能直接在 EventLoop 里执行？
 * EventLoop 负责该 Channel 上所有 IO 事件的读写，如果在其中执行慢业务（如 DB 查询 100ms），
 * 同一 EventLoop 线程上的其它 Channel 全部被阻塞，吞吐雪崩。
 * 方案：a) 业务线程池（传统）；b) 业务 EventLoopGroup 隔离；c) 虚拟线程（本项目，JDK 21）。
 * 虚拟线程在阻塞式反射调用上开销极小（unmount 不占用 OS 线程），且免去池化容量调优。
 *
 * 【面试】@Sharable 的作用：
 * Netty 默认把每个 ChannelHandler 视为【有状态】，禁止同一实例被加入多个 pipeline（防并发）。
 * 本 handler 作为单例被每个新连接复用，必须标注 @ChannelHandler.Sharable，相当于承诺线程安全：
 * services 是启动后只读的并发 Map，serializer 内部用 ThreadLocal 隔离 Kryo，故可安全共享。
 */
@Slf4j
@ChannelHandler.Sharable
public class RpcRequestHandler extends SimpleChannelInboundHandler<RpcProtocol> {

    /** 服务实例表：key = interfaceName，value = 接口 Class + 实现对象 */
    private final Map<String, ServiceHolder> services;

    /** Kryo 非线程安全 → 每个处理器实例持有独立序列化器（本 handler 在单 EventLoop 中回调，无并发写问题） */
    private final KryoSerializer serializer = new KryoSerializer();

    /** 业务执行器：JDK 21 虚拟线程（面试亮点：unmount 机制 + 免池化） */
    private static final java.util.concurrent.ExecutorService VIRTUAL_EXECUTOR =
            java.util.concurrent.Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("rpc-worker-", 0).factory());

    public RpcRequestHandler(Map<String, ServiceHolder> services) {
        this.services = services;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcProtocol protocol) {
        switch (protocol.getMessageType()) {
            // 心跳：立即回 PONG，请求 ID 原样带回
            case com.landray.rag.rpc.message.MessageType.PING ->
                    ctx.writeAndFlush(RpcProtocol.pong(protocol.getRequestId()));
            // 请求：交给虚拟线程执行，绝不阻塞 EventLoop
            case com.landray.rag.rpc.message.MessageType.REQUEST ->
                    VIRTUAL_EXECUTOR.execute(() -> invokeService(ctx, protocol));
            default -> log.warn("[RPC] 服务端收到未处理的消息类型: {}", protocol.getMessageType());
        }
    }

    /** 反射调用本地服务并写回响应 */
    private void invokeService(ChannelHandlerContext ctx, RpcProtocol protocol) {
        RpcRequest request = serializer.deserialize(protocol.getBody(), RpcRequest.class);
        RpcResponse response;
        try {
            ServiceHolder holder = services.get(request.getInterfaceName());
            if (holder == null) {
                response = RpcResponse.fail(request.getRequestId(),
                        RpcResponse.CODE_SERVICE_NOT_FOUND,
                        "服务未注册: " + request.getInterfaceName());
            } else {
                Class<?>[] paramTypes = new Class<?>[request.getParameterTypes().length];
                for (int i = 0; i < paramTypes.length; i++) {
                    paramTypes[i] = TypeUtils.resolve(request.getParameterTypes()[i]);
                }
                Method method = holder.interfaceClass.getMethod(request.getMethodName(), paramTypes);
                Object result = method.invoke(holder.implementation, request.getArgs());
                response = RpcResponse.success(request.getRequestId(), result);
            }
        } catch (InvocationTargetException e) {
            // 业务方法自身抛出的异常：取真实异常，序列化后传回消费端
            Throwable target = e.getTargetException();
            log.error("[RPC] 服务调用失败: {}#{}, 原因: {}",
                    request.getInterfaceName(), request.getMethodName(), target.getMessage());
            response = RpcResponse.fail(request.getRequestId(), toExceptionInfo(target));
        } catch (Exception e) {
            log.error("[RPC] 服务调用框架异常", e);
            response = RpcResponse.fail(request.getRequestId(), toExceptionInfo(e));
        }

        byte[] body = serializer.serialize(response);
        ctx.writeAndFlush(RpcProtocol.response(
                protocol.getSerializerType(), request.getRequestId(), body));
    }

    private RpcExceptionInfo toExceptionInfo(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return new RpcExceptionInfo(t.getClass().getName(), t.getMessage(), sw.toString());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event && event.state() == IdleState.ALL_IDLE) {
            log.debug("[RPC] 连接 {} 空闲", ctx.channel().remoteAddress());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("[RPC] 处理请求异常: {}", cause.getMessage(), cause);
        ctx.close();
    }

    /** 服务持有者 */
    public record ServiceHolder(Class<?> interfaceClass, Object implementation) {}
}
