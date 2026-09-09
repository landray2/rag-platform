package com.landray.rag.rpc.handler;

import com.landray.rag.rpc.message.RpcProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务端心跳空闲检测
 *
 * 【面试】心跳机制解决什么问题？
 * TCP 的 keepalive 检测的是"连接存活"，无法检测"应用僵死"（进程假死、Full GC 长停顿、
 * 半打开连接——对端宕机但没发 FIN）。应用层心跳通过"读写超时"主动探测对端是否还能响应。
 *
 * 本框架的心跳策略（与 Dubbo 相同方向）：
 * - 客户端：25s 写空闲 → 主动发 PING；
 * - 服务端：90s 读空闲 → 说明客户端连续 3 个周期没有发来任何字节，判定假死，主动 close 释放资源。
 * 服务端不能依赖 PING 才回复 PONG 来保持自己不超时，读空闲就关闭可以快速清理僵尸连接。
 */
@Slf4j
public class ServerHeartbeatHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("[RPC] 连接 {} 超过 90s 未收到数据（疑似客户端假死/半开连接），关闭连接",
                        ctx.channel().remoteAddress());
                ctx.close();
                return;
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("[RPC] 服务端连接异常: {}", cause.getMessage());
        ctx.close();
    }
}
