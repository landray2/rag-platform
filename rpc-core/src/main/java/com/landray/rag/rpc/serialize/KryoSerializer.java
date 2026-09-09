package com.landray.rag.rpc.serialize;

import com.esotericsoftware.kryo.Kryo;
import com.landray.rag.rpc.Serializer;
import com.landray.rag.rpc.message.RpcExceptionInfo;
import com.landray.rag.rpc.message.RpcRequest;
import com.landray.rag.rpc.message.RpcProtocol;
import com.landray.rag.rpc.message.RpcResponse;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Kryo 序列化器实现
 *
 * 【面试·高频】Kryo 为什么不是线程安全的，怎么解决？
 * Kryo 内部持有可变状态（引用解析、缓存），并发调用会互相污染。
 * 三种解法：
 * 1. ThreadLocal<Kryo> —— 本框架采用，实现最简单，每线程一个实例；
 * 2. Kryo 5.x 自带的 Pool<Kryo>（对象池，obtain/free）—— 可控容量上限；
 * 3. synchronized 方法 —— 串行化，高并发下吞吐骤降，不推荐。
 *
 * 【面试·加分】Kryo vs JDK 序列化 vs Protobuf：
 * - Kryo：快、体积小、无需 Serializable，但不跨语言、类演进兼容性弱；
 * - JDK：慢且体积大（全限定类名冗长），优点是零依赖；
 * - Protobuf：跨语言 + schema 演进兼容性最好，但需要写 proto 文件，开发成本高。
 */
public class KryoSerializer implements Serializer {

    /**
     * 每个线程独享一个 Kryo 实例（ThreadLocal 天然线程隔离，无锁）
     * initialCapacity=1024, maxCapacity=64MB：防止单条超大消息撑爆内存
     */
    private static final ThreadLocal<Kryo> KRYO_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        // 不强制注册：写类名而非注册 ID，接入新类型零配置
        // 【面试】生产环境建议 registrationRequired(true) + 预注册编号：
        // 消息体更小（写 int 编号而不是全限定类名），且避免反序列化任意类的安全风险
        kryo.setRegistrationRequired(false);
        // 常用消息类型预注册，减小消息体
        kryo.register(RpcRequest.class);
        kryo.register(RpcResponse.class);
        kryo.register(RpcExceptionInfo.class);
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        return kryo;
    });

    @Override
    public byte[] serialize(Object obj) {
        Kryo kryo = KRYO_THREAD_LOCAL.get();
        try (var output = new com.esotericsoftware.kryo.io.Output(1024, -1)) {
            kryo.writeClassAndObject(output, obj);
            output.flush();
            return output.toBytes();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        Kryo kryo = KRYO_THREAD_LOCAL.get();
        try (var input = new com.esotericsoftware.kryo.io.Input(bytes)) {
            Object obj = kryo.readClassAndObject(input);
            return clazz.cast(obj);
        }
    }

    @Override
    public byte getProtocolId() {
        return RpcProtocol.SERIALIZER_KRYO;
    }
}
