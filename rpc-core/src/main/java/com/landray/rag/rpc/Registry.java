package com.landray.rag.rpc;

import java.util.List;

/**
 * 服务注册发现接口
 *
 * Phase 1：基于 Nacos
 * 后续可扩展：ZooKeeper / Etcd / 纯本地列表
 */
public interface Registry {

    /**
     * 注册服务
     */
    void register(String serviceName, String host, int port);

    /**
     * 注销服务
     */
    void deregister(String serviceName, String host, int port);

    /**
     * 发现服务节点
     */
    List<LoadBalancer.RpcNode> discover(String serviceName);

    /**
     * 订阅服务变更（节点上下线通知）
     */
    void subscribe(String serviceName, ServiceChangeListener listener);

    @FunctionalInterface
    interface ServiceChangeListener {
        void onChange(String serviceName, List<LoadBalancer.RpcNode> nodes);
    }
}
