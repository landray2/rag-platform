package com.landray.rag.web.demo;

import java.util.Map;

/**
 * Demo 服务接口（RPC 全链路验证用）
 *
 * Provider 和 Consumer 都在 rag-web 中（自调用），
 * 但调用链路是完整的：代理 → Nacos 发现 → Netty 连接 → Kryo 序列化 → 服务端反射调用 → 响应。
 * 未来拆分为独立进程（如 rag-provider 模块）时，代码零修改。
 */
public interface DemoService {

    /** 打招呼 */
    String sayHello(String name);

    /** 返回系统信息（验证复杂对象跨网络传输） */
    Map<String, Object> getSystemInfo();
}
