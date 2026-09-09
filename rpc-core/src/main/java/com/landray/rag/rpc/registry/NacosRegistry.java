package com.landray.rag.rpc.registry;

import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.landray.rag.rpc.LoadBalancer;
import com.landray.rag.rpc.Registry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos 注册中心实现
 *
 * 设计要点：
 * 1. 注册用【临时实例】(ephemeral=true)：客户端 gRPC 心跳保活，进程崩溃后 Nacos 秒级摘除 —— 无需人工下线；
 * 2. 发现走【订阅推送】而不是每次轮询：Nacos gRPC 长连接推送变更，
 *    消费端本地缓存最新节点列表 + ServiceChangeListener 回调（可联动清空连接缓存）；
 * 3. discover() 优先读本地缓存，缓存未命中时同步拉取一次（首次调用稍慢，之后零 Nacos 往返）。
 *
 * 【面试·高频】Nacos 的 AP 与 CP 之争：
 * - 临时实例（本框架用法）：走 Distro 协议，AP 模型 —— 注册中心分区不可用时仍可返回旧节点列表，
 *   牺牲"某节点已下线"的强一致换取注册服务的高可用，符合服务发现的典型诉求；
 * - 永久实例：走 Raft（JRaft），CP 模型，适合 DNS/配置类强一致场景。
 * 【面试】为什么不选 ZooKeeper？
 * ZK 是 CP：master 选举期间不可用，且会话过期会瞬间摘除全部节点造成惊群；
 * 服务发现要的是"宁可旧一点，不能没有"—— 所以互联网公司多选 Nacos/Eureka（AP）。
 */
@Slf4j
public class NacosRegistry implements Registry, AutoCloseable {

    private final NamingService namingService;
    private final String groupName;

    /** 本地节点缓存：serviceName -> 节点列表（订阅推送更新） */
    private final Map<String, List<LoadBalancer.RpcNode>> cache = new ConcurrentHashMap<>();

    public NacosRegistry(String serverAddr, String group, String namespace,
                         String username, String password) {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        if (group != null && !group.isBlank()) {
            this.groupName = group;
        } else {
            this.groupName = "DEFAULT_GROUP";
        }
        if (namespace != null && !namespace.isBlank()) {
            properties.setProperty("namespace", namespace);
        }
        if (username != null && !username.isBlank()) {
            properties.setProperty("username", username);
            properties.setProperty("password", password == null ? "" : password);
        }
        try {
            this.namingService = NamingFactory.createNamingService(properties);
            log.info("[Nacos] 注册中心连接成功: {} (group={})", serverAddr, groupName);
        } catch (Exception e) {
            throw new RuntimeException("Nacos 注册中心初始化失败: " + serverAddr, e);
        }
    }

    @Override
    public void register(String serviceName, String host, int port) {
        try {
            Instance instance = new Instance();
            instance.setIp(host);
            instance.setPort(port);
            instance.setWeight(1.0);
            // 临时实例：进程断开 gRPC 心跳后 Nacos 自动摘除，无需人工下线
            instance.setEphemeral(true);
            namingService.registerInstance(serviceName, groupName, instance);
            log.info("[Nacos] 注册服务: {} -> {}:{}", serviceName, host, port);
        } catch (Exception e) {
            throw new RuntimeException("Nacos 注册失败: " + serviceName, e);
        }
    }

    @Override
    public void deregister(String serviceName, String host, int port) {
        try {
            namingService.deregisterInstance(serviceName, groupName, host, port);
            log.info("[Nacos] 注销服务: {} -> {}:{}", serviceName, host, port);
        } catch (Exception e) {
            log.warn("[Nacos] 注销失败（进程退出即可自动摘除）: {}", e.getMessage());
        }
    }

    @Override
    public List<LoadBalancer.RpcNode> discover(String serviceName) {
        // 优先读本地缓存（由订阅推送维护，无 Nacos 网络往返）
        List<LoadBalancer.RpcNode> cached = cache.get(serviceName);
        if (cached != null) {
            return cached;
        }
        try {
            List<Instance> instances = namingService.getAllInstances(serviceName, groupName);
            List<LoadBalancer.RpcNode> nodes = toNodes(instances);
            cache.put(serviceName, nodes);
            // 首次发现时建立订阅，后续变更加自动推送
            subscribe(serviceName, (name, updated) -> {});
            return nodes;
        } catch (Exception e) {
            throw new RuntimeException("Nacos 服务发现失败: " + serviceName, e);
        }
    }

    @Override
    public void subscribe(String serviceName, ServiceChangeListener listener) {
        try {
            namingService.subscribe(serviceName, groupName, event -> {
                if (event instanceof NamingEvent namingEvent) {
                    List<LoadBalancer.RpcNode> nodes = toNodes(namingEvent.getInstances());
                    cache.put(serviceName, nodes);
                    listener.onChange(serviceName, nodes);
                    log.info("[Nacos] 服务节点变更: {} -> {} 个节点", serviceName, nodes.size());
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Nacos 订阅失败: " + serviceName, e);
        }
    }

    private List<LoadBalancer.RpcNode> toNodes(List<Instance> instances) {
        if (instances == null) {
            return List.of();
        }
        return instances.stream()
                .map(i -> new LoadBalancer.RpcNode(
                        i.getIp(), i.getPort(), (int) Math.max(i.getWeight(), 0),
                        i.isEnabled() && i.isHealthy()))
                .toList();
    }

    @Override
    public void close() {
        try {
            namingService.shutDown();
        } catch (Exception e) {
            log.warn("[Nacos] 关闭异常: {}", e.getMessage());
        }
    }
}
