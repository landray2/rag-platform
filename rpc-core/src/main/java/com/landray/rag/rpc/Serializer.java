package com.landray.rag.rpc;

/**
 * 序列化器接口
 *
 * Phase 1：Kryo 实现
 * 后续可扩展：Protobuf / Hessian / JSON
 */
public interface Serializer {

    /**
     * 序列化：对象 → 字节数组
     */
    byte[] serialize(Object obj);

    /**
     * 反序列化：字节数组 → 对象
     *
     * @param bytes 字节数组
     * @param clazz 目标类型
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);

    /**
     * 获取序列化协议标识（用于服务端识别选择解码器）
     */
    byte getProtocolId();
}
