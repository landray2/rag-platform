package com.landray.rag.rpc.message;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * RPC 传输协议（协议头 + 消息体）
 *
 * <pre>
 * 协议布局（共 17 字节协议头 + 变长消息体）：
 * ┌───────────┬─────────┬───────────────┬───────────────┬──────────────┬──────────────┬──────────┐
 * │ Magic (2) │ Ver (1) │ Serializer(1) │ Type (1)      │ RequestId(8) │ BodyLen (4)  │ Body     │
 * │ 0xCAFE    │ 1       │ 1=Kryo        │ 1请求/2响应... │ long         │ int          │ 变长      │
 * └───────────┴─────────┴───────────────┴───────────────┴──────────────┴──────────────┴──────────┘
 * </pre>
 *
 * 【面试】粘包/拆包的三种主流解法：
 * 1. 固定长度：浪费带宽，几乎不用；
 * 2. 分隔符（如 \r\n，Redis 协议）：消息体中不能出现分隔符，需转义；
 * 3. 长度字段 + 消息体（本框架/Dubbo/Redis 大批量传输均用此法）：
 *    Netty 的 LengthFieldBasedFrameDecoder 按协议头里的 BodyLen 精确切帧。
 * Magic 魔数的作用：快速甄别非本协议的连接（如 HTTP 请求打到 RPC 端口），防止脏数据干扰解码。
 */
@Data
@AllArgsConstructor
public class RpcProtocol {

    /** 魔数（2 字节）：用于快速识别非法连接 */
    public static final int MAGIC = 0xCAFE;

    /** 协议版本（1 字节） */
    public static final byte VERSION = 1;

    /** 序列化算法标识：Kryo */
    public static final byte SERIALIZER_KRYO = 1;

    /**
     * 协议头长度：MAGIC(2) + VERSION(1) + SERIALIZER(1) + TYPE(1) + REQUEST_ID(8) = 13
     * BODY_LEN 字段位于第 13 字节偏移处（0 起算），占 4 字节 —— LengthFieldBasedFrameDecoder 的关键参数
     */
    public static final int HEADER_LENGTH = 13;

    /** 序列化算法标识 */
    private byte serializerType;

    /** 消息类型：@see MessageType */
    private byte messageType;

    /** 请求 ID */
    private long requestId;

    /** 消息体字节数组（REQUEST → RpcRequest，RESPONSE → RpcResponse，心跳为空） */
    private byte[] body;

    /** 构造请求协议包 */
    public static RpcProtocol request(byte serializerType, long requestId, byte[] body) {
        return new RpcProtocol(serializerType, MessageType.REQUEST, requestId, body);
    }

    /** 构造响应协议包 */
    public static RpcProtocol response(byte serializerType, long requestId, byte[] body) {
        return new RpcProtocol(serializerType, MessageType.RESPONSE, requestId, body);
    }

    /** 构造心跳 PING */
    public static RpcProtocol ping(long requestId) {
        return new RpcProtocol(SERIALIZER_KRYO, MessageType.PING, requestId, new byte[0]);
    }

    /** 构造心跳 PONG */
    public static RpcProtocol pong(long requestId) {
        return new RpcProtocol(SERIALIZER_KRYO, MessageType.PONG, requestId, new byte[0]);
    }
}
