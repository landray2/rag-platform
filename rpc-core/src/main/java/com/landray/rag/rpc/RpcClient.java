package com.landray.rag.rpc;

import java.util.concurrent.CompletableFuture;

/**
 * RPC 客户端接口
 *
 * Phase 1：Netty + Kryo 实现
 * 功能：动态代理 Stub、异步调用、超时控制、自动重连
 */
public interface RpcClient {

    /**
     * 获取服务代理（JDK 动态代理）
     *
     * @param serviceClass 服务接口
     * @param timeoutMs    调用超时（毫秒）
     */
    <T> T createProxy(Class<T> serviceClass, long timeoutMs);

    /**
     * 异步调用
     *
     * @param serviceName 服务名
     * @param methodName  方法名
     * @param args        参数
     * @param timeoutMs   超时
     */
    CompletableFuture<Object> asyncCall(String serviceName, String methodName, Object[] args, long timeoutMs);

    /**
     * 关闭客户端
     */
    void shutdown();
}
