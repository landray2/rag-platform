package com.landray.rag.rpc.codec;

import com.landray.rag.rpc.message.RpcProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * RPC 协议解码器：二进制流 → RpcProtocol（入站）
 *
 * 【面试·高频】LengthFieldBasedFrameDecoder 五个参数的含义（粘包/拆包的关键）：
 * - maxFrameLength = 16MB：单帧上限，防御恶意超长帧（OOM 攻击）；
 * - lengthFieldOffset = 13：长度字段的偏移（跳过 MAGIC2+VER1+SER1+TYPE1+REQID8）；
 * - lengthFieldLength = 4：长度字段自身占 4 字节（int）；
 * - lengthAdjustment = 0：长度字段的值 = 后面 body 的字节数（不含长度字段自身）；
 * - initialBytesToStrip = 0：不丢弃协议头，后续 handler 还要读 header 里的 type/requestId。
 *
 * 收到多个请求"粘"在一个 TCP 段里时，本解码器会按 BodyLen 循环切出多个完整帧分别传递；
 * 收到半包时则缓存 ByteBuf 等剩余字节到达后再切 —— 这就是 Netty 对粘包/拆包的完整解法。
 */
public class RpcProtocolDecoder extends LengthFieldBasedFrameDecoder {

    /** 单帧上限 16MB，防止恶意大包 OOM */
    private static final int MAX_FRAME_LENGTH = 16 * 1024 * 1024;

    public RpcProtocolDecoder() {
        super(MAX_FRAME_LENGTH, RpcProtocol.HEADER_LENGTH, 4, 0, 0);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // super.decode 完成"切帧"：拿到一个完整帧（含协议头），没有完整帧则返回 null 等待下一个包
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }
        try {
            // 【面试·踩坑】readShort() 返回【有符号】short：0xCAFE 超过 Short.MAX_VALUE(0x7FFF)，
            // 读出为负数 -13570；直接与 int 魔数 0xCAFE 比较时，short 符号扩展为 0xFFFFCAFE，永不相等。
            // 必须 & 0xFFFF 抹平符号位（或用 readUnsignedShort()）。
            int magic = frame.readShort() & 0xFFFF;
            if (magic != RpcProtocol.MAGIC) {
                throw new IllegalArgumentException("非法协议魔数: 0x" + Integer.toHexString(magic));
            }
            byte version = frame.readByte();
            if (version != RpcProtocol.VERSION) {
                throw new IllegalArgumentException("不支持的协议版本: " + version);
            }
            byte serializerType = frame.readByte();
            byte messageType = frame.readByte();
            long requestId = frame.readLong();
            int bodyLength = frame.readInt();

            byte[] body = new byte[bodyLength];
            frame.readBytes(body);

            return new RpcProtocol(serializerType, messageType, requestId, body);
        } finally {
            // LengthFieldBasedFrameDecoder 切出的 frame 是独立 retain 的 ByteBuf，必须手动释放
            frame.release();
        }
    }
}
