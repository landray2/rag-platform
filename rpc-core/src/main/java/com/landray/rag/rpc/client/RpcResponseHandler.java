package com.landray.rag.rpc.client;

import com.landray.rag.rpc.message.RpcProtocol;
import com.landray.rag.rpc.message.RpcResponse;
import com.landray.rag.rpc.serialize.KryoSerializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * RPC 响应处理器（客户端）
 *
 * 【面试·高频】异步转同步（sync-over-async）的经典实现：
 * 1. 发送前：以 requestId 为 key 把 CompletableFuture 放入 pending 表；
 * 2. 收到响应：按 requestId 从 pending 表取出 Future 并 complete —— 网络线程与调用线程解耦；
 * 3. 调用线程 future.get(timeout) 阻塞等待 —— 对使用者呈现同步语义。
 * 为什么需要 requestId？一条 TCP 连接上同时挂着多个未返回请求（多路复用），
 * 响应到达顺序与请求发出顺序无保证，必须靠 requestId 配对。
 */
@Slf4j
public class RpcResponseHandler extends SimpleChannelInboundHandler<RpcProtocol> {

    /** 等待响应的请求表（客户端全局共享） */
    private final ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> pendingRequests;

    /** 空闲时发送心跳的回调（由 client 提供 requestId 生成） */
    private final Runnable heartbeatSender;

    private final KryoSerializer serializer = new KryoSerializer();

    public RpcResponseHandler(ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> pendingRequests,
                              Runnable heartbeatSender) {
        this.pendingRequests = pendingRequests;
        this.heartbeatSender = heartbeatSender;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcProtocol protocol) {
        switch (protocol.getMessageType()) {
            case com.landray.rag.rpc.message.MessageType.RESPONSE -> {
                CompletableFuture<RpcResponse> future = pendingRequests.remove(protocol.getRequestId());
                if (future != null) {
                    RpcResponse response = serializer.deserialize(protocol.getBody(), RpcResponse.class);
                    future.complete(response);
                } else {
                    // 可能已超时被移除，丢弃即可（不做补偿）
                    log.debug("[RPC] 响应迟到或重复, requestId={}", protocol.getRequestId());
                }
            }
            case com.landray.rag.rpc.message.MessageType.PONG ->
                    log.debug("[RPC] 收到心跳 PONG, 连接存活: {}", ctx.channel().remoteAddress());
            default -> log.warn("[RPC] 客户端收到未处理的消息类型: {}", protocol.getMessageType());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event && event.state() == IdleState.WRITER_IDLE) {
            // 25s 没有任何出站数据 → 发送心跳，同时顺带探测链路活性
            log.debug("[RPC] 写空闲, 发送 PING: {}", ctx.channel().remoteAddress());
            heartbeatSender.run();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("[RPC] 客户端连接异常: {}", cause.getMessage());
        ctx.close();
    }
}
