package com.landray.rag.rpc.proxy;

import com.landray.rag.rpc.LoadBalancer;
import com.landray.rag.rpc.client.NettyRpcClient;
import com.landray.rag.rpc.message.RpcRequest;
import com.landray.rag.rpc.message.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * RPC 客户端动态代理：把接口调用翻译成网络请求
 *
 * 【面试·高频】动态代理在 RPC 里的作用与原理：
 * 作用：消费端拿到的是"接口的假实现"，调用方法时被拦截并转发为一次 RPC —— 使用方无感知。
 * 原理（JDK Proxy）：
 * 1. 运行时动态生成 $Proxy0 类，继承 Proxy 并实现目标接口，每个方法内部调用 InvocationHandler.invoke；
 * 2. 生成方式：字节码拼接（ProxyGenerator）→ ClassLoader.defineClass；
 * 3. 只能代理接口（类代理需要 CGLIB / ByteBuddy，通过子类字节码实现）。
 *
 * 为什么 Object 的 toString/hashCode/equals 也要处理？
 * 代理对象也会被日志、集合等使用，拦截 Object 方法避免它们被当成 RPC 请求发出去。
 */
@Slf4j
public class RpcClientProxy {

    private final NettyRpcClient client;
    private final LoadBalancer defaultLoadBalancer;

    public RpcClientProxy(NettyRpcClient client, LoadBalancer defaultLoadBalancer) {
        this.client = client;
        this.defaultLoadBalancer = defaultLoadBalancer;
    }

    /** 生成指定接口的代理对象（使用默认负载均衡器） */
    public <T> T createProxy(Class<T> serviceInterface, long timeoutMs) {
        return createProxy(serviceInterface, timeoutMs, null);
    }

    /** 生成指定接口的代理对象（可指定负载均衡算法名，对应 @RpcReference.loadBalancer） */
    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> serviceInterface, long timeoutMs, String loadBalancerName) {
        LoadBalancer balancer = loadBalancerName == null || loadBalancerName.isBlank()
                ? defaultLoadBalancer
                : com.landray.rag.rpc.lb.LoadBalancerFactory.create(loadBalancerName);

        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[]{serviceInterface},
                new RpcInvocationHandler(serviceInterface, timeoutMs, balancer));
    }

    /** InvocationHandler：方法调用 → RpcRequest → 网络发送 → 等待响应 → 返回结果 */
    private class RpcInvocationHandler implements InvocationHandler {

        private final Class<?> serviceInterface;
        private final long timeoutMs;
        private final LoadBalancer loadBalancer;

        RpcInvocationHandler(Class<?> serviceInterface, long timeoutMs, LoadBalancer loadBalancer) {
            this.serviceInterface = serviceInterface;
            this.timeoutMs = timeoutMs;
            this.loadBalancer = loadBalancer;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Object 方法不发起远程调用
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "RpcProxy{" + serviceInterface.getName() + "}";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }

            RpcRequest request = RpcRequest.builder()
                    .requestId(client.nextRequestId())
                    .interfaceName(serviceInterface.getName())
                    .version("1.0.0")
                    .methodName(method.getName())
                    .parameterTypes(Arrays.stream(method.getParameterTypes())
                            .map(Class::getName).toArray(String[]::new))
                    .args(args)
                    .build();

            long start = System.nanoTime();
            CompletableFuture<RpcResponse> future =
                    client.sendRequest(request, timeoutMs, loadBalancer);
            RpcResponse response = future.get(timeoutMs + 500, TimeUnit.MILLISECONDS);
            long costMs = (System.nanoTime() - start) / 1_000_000;

            if (response.getCode() != RpcResponse.CODE_SUCCESS) {
                if (response.getException() != null) {
                    throw new com.landray.rag.rpc.RpcRemoteException(response.getException());
                }
                throw new com.landray.rag.rpc.RpcRemoteException(
                        "RPC 调用失败 [" + response.getCode() + "]: " + response.getMessage());
            }
            log.debug("[RPC] {}#{} 耗时 {}ms", serviceInterface.getSimpleName(),
                    method.getName(), costMs);
            return response.getData();
        }
    }
}
