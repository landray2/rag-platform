package com.landray.rag.rpc.spring;

import com.landray.rag.rpc.RpcService;
import com.landray.rag.rpc.server.NettyRpcServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 服务暴露后置处理器：扫描 @RpcService 注解的 Bean，自动注册到 Netty 服务端
 *
 * 【面试】BeanPostProcessor 的执行时机？
 * 在 Bean 初始化（InitializingBean.afterPropertiesSet / init-method）完成后回调，
 * 此时 Bean 已就绪，可以安全地把它登记到服务表。
 * 它是 Spring 扩展点中最核心的一个 —— AOP 代理对象的生成（AbstractAutoProxyCreator）也是 BeanPostProcessor。
 *
 * 为什么把"扫描注解"和"启动网络服务"拆成两个组件？
 * 职责分离：PostProcessor 只负责"发现"，SmartLifecycle 只负责"启动/停止"，
 * 且 SmartLifecycle.start() 在所有 Bean 初始化完成后才执行 —— 保证注册前所有服务都已收集完毕。
 */
@Slf4j
public class RpcServicePostProcessor implements BeanPostProcessor {

    private final NettyRpcServer rpcServer;

    public RpcServicePostProcessor(NettyRpcServer rpcServer) {
        this.rpcServer = rpcServer;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        RpcService annotation = org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedAnnotation(beanClass, RpcService.class);
        if (annotation == null) {
            return bean;
        }

        // 服务接口：显式指定优先，否则取实现的第一个接口
        Class<?> serviceInterface = annotation.value() != Void.class
                ? annotation.value()
                : firstInterface(beanClass);
        if (serviceInterface == null) {
            throw new IllegalStateException(
                    "@RpcService 标注的类 " + beanClass.getName() + " 未实现任何接口");
        }

        rpcServer.export(serviceInterface, bean);
        return bean;
    }

    private Class<?> firstInterface(Class<?> clazz) {
        Class<?>[] interfaces = clazz.getInterfaces();
        // 跳过 Spring 内部接口（如 InitializingBean）
        for (Class<?> iface : interfaces) {
            if (!iface.getName().startsWith("org.springframework")) {
                return iface;
            }
        }
        return interfaces.length > 0 ? interfaces[0] : null;
    }
}
