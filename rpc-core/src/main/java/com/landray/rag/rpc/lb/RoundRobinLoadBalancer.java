package com.landray.rag.rpc.lb;

import com.landray.rag.rpc.LoadBalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡器（Round-Robin）
 *
 * 【面试】为什么用 AtomicInteger + 取模，而不用 synchronized 计数器？
 * 轮询是热点路径（每次请求都会执行），CAS 无锁计数在高并发下吞吐远高于互斥锁。
 * 注意取模防负数：counter 用 & Integer.MAX_VALUE 位运算处理，比 Math.abs（Integer.MIN_VALUE 边界）更稳。
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public RpcNode select(String serviceName, List<RpcNode> nodes, String key) {
        List<RpcNode> healthy = nodes.stream().filter(LoadBalancer.RpcNode::healthy).toList();
        if (healthy.isEmpty()) {
            // 全部不健康时退化为原列表（尽力而为）
            healthy = nodes;
        }
        if (healthy.isEmpty()) {
            return null;
        }
        int index = (counter.getAndIncrement() & Integer.MAX_VALUE) % healthy.size();
        return healthy.get(index);
    }

    @Override
    public String algorithm() {
        return "round-robin";
    }
}
