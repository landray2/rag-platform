package com.landray.rag.rpc.spring;

import com.landray.rag.rpc.Registry;
import com.landray.rag.rpc.server.NettyRpcServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RPC 生命周期管理：容器就绪后启动 Netty + 注册到注册中心；容器关闭时反注册 + 停机
 *
 * 【面试】为什么用 SmartLifecycle 而不是 ApplicationRunner / @PostConstruct？
 * 1. 执行时机：SmartLifecycle.start() 在所有单例 Bean 完全初始化（含 PostProcessor 处理）之后才执行，
 *    保证此时所有 @RpcService 服务都已被收集；@PostConstruct 则可能在其它 Bean 未就绪时执行；
 * 2. 优雅停机：stop() 在容器关闭时被回调（早于 Bean 销毁），
 *    可以先把服务从 Nacos 摘除（让流量切走）再关闭 Netty —— 经典的"先下线再停机"发布姿势；
 * 3. 支持 phase 排序与自动启动开关。
 */
@Slf4j
public class RpcLifecycle implements SmartLifecycle {

    private final NettyRpcServer rpcServer;
    private final Registry registry;
    private final String providerHost;
    private final int providerPort;
    private final boolean enabled;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public RpcLifecycle(NettyRpcServer rpcServer, Registry registry,
                        String providerHost, int providerPort, boolean enabled) {
        this.rpcServer = rpcServer;
        this.registry = registry;
        this.providerHost = providerHost;
        this.providerPort = providerPort;
        this.enabled = enabled;
    }

    @Override
    public void start() {
        if (!enabled) {
            log.info("[RPC] 服务端已禁用 (rpc.server.enabled=false)");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        // 用虚拟线程启动，bind 完成后再注册到注册中心（保证注册即可用）
        Thread.startVirtualThread(() -> {
            try {
                rpcServer.start(providerPort);
                Set<String> services = rpcServer.exportedServices();
                for (String serviceName : services) {
                    registry.register(serviceName, providerHost, providerPort);
                }
                log.info("[RPC] 启动完成, 已注册 {} 个服务到注册中心: {}", services.size(), services);
            } catch (Exception e) {
                log.error("[RPC] 启动失败", e);
                running.set(false);
            }
        });
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        // 先从注册中心摘除（流量切走）→ 再关闭 Netty（等存量请求执行完）
        for (String serviceName : rpcServer.exportedServices()) {
            registry.deregister(serviceName, providerHost, providerPort);
        }
        rpcServer.shutdown();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /** 优先级：默认 0 即可（无其它 Lifecycle 组件需要排序） */
    @Override
    public int getPhase() {
        return 0;
    }

    /** 推断服务对外暴露的 IP：显式配置 > 本机网卡地址 > 回环地址 */
    public static String resolveHost(String configuredHost) {
        if (configuredHost != null && !configuredHost.isBlank()) {
            return configuredHost;
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
