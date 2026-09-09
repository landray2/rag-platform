package com.landray.rag.rpc.codec;

import com.landray.rag.rpc.message.RpcProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * RPC 协议编码器：RpcProtocol → 二进制帧（出站）
 *
 * 写出顺序与 {@link RpcProtocol} 协议头布局严格一致：
 * MAGIC(2) + VERSION(1) + SERIALIZER(1) + TYPE(1) + REQUEST_ID(8) + BODY_LEN(4) + BODY(n)
 */
public class RpcProtocolEncoder extends MessageToByteEncoder<RpcProtocol> {

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcProtocol msg, ByteBuf out) {
        out.writeShort(RpcProtocol.MAGIC);
        out.writeByte(RpcProtocol.VERSION);
        out.writeByte(msg.getSerializerType());
        out.writeByte(msg.getMessageType());
        out.writeLong(msg.getRequestId());
        byte[] body = msg.getBody();
        out.writeInt(body == null ? 0 : body.length);
        if (body != null && body.length > 0) {
            out.writeBytes(body);
        }
    }
}
