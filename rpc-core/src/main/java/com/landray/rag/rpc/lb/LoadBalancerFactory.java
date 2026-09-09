package com.landray.rag.rpc.lb;

import com.landray.rag.rpc.LoadBalancer;

/**
 * 负载均衡器工厂（按名称创建，对应 @RpcReference.loadBalancer 属性）
 */
public final class LoadBalancerFactory {

    private LoadBalancerFactory() {}

    public static LoadBalancer create(String algorithm) {
        return switch (algorithm == null ? "" : algorithm) {
            case "round-robin", "rr" -> new RoundRobinLoadBalancer();
            case "weighted-random", "random" -> new WeightedRandomLoadBalancer();
            default -> new RoundRobinLoadBalancer();
        };
    }
}
