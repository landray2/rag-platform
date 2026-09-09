package com.landray.rag.rpc.lb;

import com.landray.rag.rpc.LoadBalancer;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 加权随机负载均衡器（Weighted Random）
 *
 * 权重越大被选中概率越高。Nacos 注册的 Instance 自带 weight（默认 1.0），
 * 可在 Nacos 控制台动态调权实现流量灰度。
 *
 * 【面试】加权随机 vs 平滑加权轮询（Nginx smooth WRR）：
 * - 加权随机：实现简单、概率正确，但短时间内可能连续命中同一节点（突发不均）；
 * - 平滑加权轮询：按 (当前权重 += 配置权重，选最大者并减去总权重) 轮转，
 *   使选中序列均匀展开（如 A=5,B=1,C=1 不会连着 5 次都打 A）。
 * 后续 Phase 可扩展平滑加权轮询 / 一致性哈希（粘性会话）。
 */
public class WeightedRandomLoadBalancer implements LoadBalancer {

    @Override
    public RpcNode select(String serviceName, List<RpcNode> nodes, String key) {
        List<RpcNode> healthy = nodes.stream().filter(LoadBalancer.RpcNode::healthy).toList();
        if (healthy.isEmpty()) {
            return null;
        }
        int totalWeight = healthy.stream().mapToInt(n -> Math.max(n.weight(), 0)).sum();
        if (totalWeight <= 0) {
            // 全部权重为 0 → 退化为均匀随机
            return healthy.get(ThreadLocalRandom.current().nextInt(healthy.size()));
        }
        int offset = ThreadLocalRandom.current().nextInt(totalWeight);
        for (RpcNode node : healthy) {
            offset -= Math.max(node.weight(), 0);
            if (offset < 0) {
                return node;
            }
        }
        return healthy.get(healthy.size() - 1);
    }

    @Override
    public String algorithm() {
        return "weighted-random";
    }
}
