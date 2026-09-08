package com.landray.rag.rpc;

import java.util.List;

/**
 * 负载均衡器接口
 *
 * Phase 1：轮询 / 加权轮询
 * 后续可扩展：一致性哈希 / 最小连接数 / 自适应权重
 */
public interface LoadBalancer {

    /**
     * 从可用节点中选择一个
     *
     * @param serviceName 服务名
     * @param nodes       可用节点列表
     * @param key         可选的粘性 key（一致性哈希时使用）
     * @return 选中的节点
     */
    RpcNode select(String serviceName, List<RpcNode> nodes, String key);

    /**
     * 算法名称
     */
    String algorithm();

    /**
     * RPC 服务节点
     */
    record RpcNode(String host, int port, int weight, boolean healthy) {}
}
