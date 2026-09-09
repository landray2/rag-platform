package com.landray.rag.rpc.registry;

import com.landray.rag.rpc.LoadBalancer;
import com.landray.rag.rpc.Registry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 本地内存注册中心（无 Nacos 环境时的降级实现）
 *
 * 【面试】为什么要有 LocalRegistry 这种降级实现？
 * 1. 单元测试/本地调试不需要起 Nacos，开箱即用；
 * 2. 注册中心故障时应用内部调用仍可降级（虽然跨进程调用此时不可用）；
 * 3. 面向接口编程的收益：Registry 接口不变，实现可插拔（Nacos/ZK/本地）。
 */
@Slf4j
public class LocalRegistry implements Registry {

    /** serviceName -> 节点集合（CopyOnWriteArraySet：读多写少，遍历无锁） */
    private final Map<String, Set<LoadBalancer.RpcNode>> serviceMap = new ConcurrentHashMap<>();

    @Override
    public void register(String serviceName, String host, int port) {
        serviceMap.computeIfAbsent(serviceName, k -> new CopyOnWriteArraySet<>())
                .add(new LoadBalancer.RpcNode(host, port, 1, true));
        log.info("[LocalRegistry] 注册服务: {} -> {}:{}", serviceName, host, port);
    }

    @Override
    public void deregister(String serviceName, String host, int port) {
        Set<LoadBalancer.RpcNode> nodes = serviceMap.get(serviceName);
        if (nodes != null) {
            nodes.removeIf(n -> n.host().equals(host) && n.port() == port);
        }
        log.info("[LocalRegistry] 注销服务: {} -> {}:{}", serviceName, host, port);
    }

    @Override
    public List<LoadBalancer.RpcNode> discover(String serviceName) {
        Set<LoadBalancer.RpcNode> nodes = serviceMap.get(serviceName);
        return nodes == null ? List.of() : List.copyOf(nodes);
    }

    @Override
    public void subscribe(String serviceName, ServiceChangeListener listener) {
        // 本地注册中心无变更推送；如需要，可在 register/deregister 时回调 listener
    }
}
