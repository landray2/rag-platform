package com.landray.rag.rpc;

/**
 * RPC 服务端接口
 *
 * Phase 1：Netty ServerBootstrap + 自定义编解码器 + 心跳 Handler
 * 功能：服务暴露、粘包处理、心跳检测、优雅停机
 */
public interface RpcServer {

    /**
     * 启动服务端
     *
     * @param port 监听端口
     */
    void start(int port);

    /**
     * 暴露服务实现
     *
     * @param serviceInterface 服务接口
     * @param serviceImpl      服务实现
     */
    void export(Class<?> serviceInterface, Object serviceImpl);

    /**
     * 优雅停机
     */
    void shutdown();
}
