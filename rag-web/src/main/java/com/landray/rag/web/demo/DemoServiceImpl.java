package com.landray.rag.web.demo;

import com.landray.rag.rpc.RpcService;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo 服务实现（服务提供方）
 *
 * 注意：只加 @RpcService，不加 @Service ——
 * 它不需要被 Web 层直接注入，仅作为 RPC Provider 存在。
 * （加 @Service 也没错，但职责上 Provider 就是 Provider。）
 */
@Slf4j
@RpcService(DemoService.class)
public class DemoServiceImpl implements DemoService {

    @Override
    public String sayHello(String name) {
        log.info("[DemoService] 收到 RPC 调用 sayHello, name={}", name);
        return "Hello, " + name + "! 这条消息经由 Netty + Kryo RPC 链路返回。";
    }

    @Override
    public Map<String, Object> getSystemInfo() {
        log.info("[DemoService] 收到 RPC 调用 getSystemInfo");
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("virtualThreads", ManagementFactory.getThreadMXBean().getThreadCount());
        info.put("availableProcessors", runtime.availableProcessors());
        info.put("maxMemoryMB", runtime.maxMemory() / 1024 / 1024);
        info.put("usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        return info;
    }
}
