package com.landray.rag.rpc.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RPC 请求对象
 *
 * 【面试】为什么 RpcRequest 不实现 Serializable？
 * Kryo 序列化不依赖 Java 的 Serializable 接口（无继承开销、体积更小、速度更快）；
 * 这也是 Kryo 相比 JDK 原生序列化的核心优势之一。
 * 代价：跨语言兼容性差，所以如果未来要支持多语言客户端，应换 Protobuf。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcRequest {

    /** 请求 ID（全局唯一，用于将响应与 pending Future 配对） */
    private long requestId;

    /** 服务接口全限定名，如 com.landray.rag.web.demo.DemoService */
    private String interfaceName;

    /** 服务版本（预留多版本路由） */
    private String version;

    /** 方法名 */
    private String methodName;

    /** 参数类型全限定名数组（服务端用于精确反射查找 Method） */
    private String[] parameterTypes;

    /** 实际参数值 */
    private Object[] args;

    /** 附加信息（链路追踪 traceId、灰度标记等，预留） */
    private java.util.Map<String, Object> attachments;
}
