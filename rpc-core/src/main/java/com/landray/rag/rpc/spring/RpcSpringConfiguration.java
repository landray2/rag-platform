package com.landray.rag.rpc.spring;

import com.landray.rag.rpc.LoadBalancer;
import com.landray.rag.rpc.Registry;
import com.landray.rag.rpc.client.NettyRpcClient;
import com.landray.rag.rpc.lb.LoadBalancerFactory;
import com.landray.rag.rpc.proxy.RpcClientProxy;
import com.landray.rag.rpc.registry.LocalRegistry;
import com.landray.rag.rpc.registry.NacosRegistry;
import com.landray.rag.rpc.server.NettyRpcServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RPC 框架 Spring 装配（纯 spring-context，不依赖 spring-boot autoconfigure）
 *
 * 配置项（application.yml 的 rpc.* 前缀，均带默认值）：
 *   rpc.server.enabled        是否启动 RPC 服务端（默认 true）
 *   rpc.server.port           服务端端口（默认 9999）
 *   rpc.provider.host         对外暴露 IP（留空自动探测本机地址）
 *   rpc.nacos.server-addr     Nacos 地址（留空则降级为 LocalRegistry）
 *   rpc.nacos.group           Nacos 分组（默认 DEFAULT_GROUP）
 *   rpc.nacos.namespace       Nacos 命名空间（默认 public）
 *   rpc.nacos.username/password  Nacos 鉴权（默认关闭鉴权，留空即可）
 *   rpc.consumer.load-balancer 默认负载均衡算法（round-robin | weighted-random）
 */
@Slf4j
@Configuration
public class RpcSpringConfiguration {

    @Value("${rpc.server.enabled:true}")
    private boolean serverEnabled;

    @Value("${rpc.server.port:9999}")
    private int serverPort;

    @Value("${rpc.provider.host:}")
    private String providerHost;

    @Value("${rpc.nacos.server-addr:}")
    private String nacosServerAddr;

    @Value("${rpc.nacos.group:DEFAULT_GROUP}")
    private String nacosGroup;

    @Value("${rpc.nacos.namespace:}")
    private String nacosNamespace;

    @Value("${rpc.nacos.username:}")
    private String nacosUsername;

    @Value("${rpc.nacos.password:}")
    private String nacosPassword;

    @Value("${rpc.consumer.load-balancer:round-robin}")
    private String loadBalancerName;

    @Bean
    public NettyRpcServer nettyRpcServer() {
        return new NettyRpcServer();
    }

    /**
     * 注册中心：配置了 Nacos 地址用 Nacos，否则降级本地注册中心
     * destroyMethod 自动推断：NacosRegistry 实现了 AutoCloseable，容器关闭时自动 shutDown
     */
    @Bean
    public Registry rpcRegistry() {
        if (nacosServerAddr == null || nacosServerAddr.isBlank()) {
            log.warn("[RPC] 未配置 rpc.nacos.server-addr, 使用本地注册中心（仅限单机演示/测试）");
            return new LocalRegistry();
        }
        return new NacosRegistry(nacosServerAddr, nacosGroup, nacosNamespace,
                nacosUsername, nacosPassword);
    }

    @Bean
    public LoadBalancer rpcLoadBalancer() {
        return LoadBalancerFactory.create(loadBalancerName);
    }

    @Bean
    public NettyRpcClient nettyRpcClient(Registry registry, LoadBalancer rpcLoadBalancer) {
        return new NettyRpcClient(registry, rpcLoadBalancer);
    }

    @Bean
    public RpcClientProxy rpcClientProxy(NettyRpcClient nettyRpcClient, LoadBalancer rpcLoadBalancer) {
        return new RpcClientProxy(nettyRpcClient, rpcLoadBalancer);
    }

    /** static：BeanPostProcessor 必须尽早注册，避免所在配置类被延迟初始化 */
    @Bean
    public static RpcServicePostProcessor rpcServicePostProcessor(NettyRpcServer nettyRpcServer) {
        return new RpcServicePostProcessor(nettyRpcServer);
    }

    @Bean
    public static RpcReferencePostProcessor rpcReferencePostProcessor(RpcClientProxy rpcClientProxy) {
        return new RpcReferencePostProcessor(rpcClientProxy);
    }

    @Bean
    public RpcLifecycle rpcLifecycle(NettyRpcServer nettyRpcServer, Registry rpcRegistry) {
        String host = RpcLifecycle.resolveHost(providerHost);
        log.info("[RPC] 配置完成: 端口={}, 服务地址={}, 注册中心={}",
                serverPort, host, rpcRegistry.getClass().getSimpleName());
        return new RpcLifecycle(nettyRpcServer, rpcRegistry, host, serverPort, serverEnabled);
    }
}
