package com.landray.rag.rpc.spring;

import com.landray.rag.rpc.RpcReference;
import com.landray.rag.rpc.proxy.RpcClientProxy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;

/**
 * 服务引用后置处理器：为 @RpcReference 字段注入 RPC 代理对象
 *
 * 【面试】为什么用 BeanPostProcessor 而不是 @Autowired + BeanFactoryPostProcessor？
 * @RpcReference 字段类型是"业务接口"，容器里并没有这个接口的 Bean，
 * 无法用常规依赖注入完成 —— 必须在 Bean 初始化后由我们自己 new 出代理对象再反射塞进去。
 * （另一种做法：实现 FactoryBean + 自定义 Scope，Dubbo 老版本用的 ReferenceBean 即此类方案。）
 *
 * 【面试】反射注入 private 字段的性能问题？
 * setAccessible(true) 后首次调用较慢（JIT 前），Dubbo/MyBatis 都这么干，初始化阶段一次性开销可接受；
 * 也可用 MethodHandle / VarHandle 获得更接近直接调用的性能。
 */
@Slf4j
public class RpcReferencePostProcessor implements BeanPostProcessor {

    private final RpcClientProxy clientProxy;

    public RpcReferencePostProcessor(RpcClientProxy clientProxy) {
        this.clientProxy = clientProxy;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        // 遍历本类 + 父类的全部字段（@RpcReference 可能标在父类上）
        for (Class<?> clazz = beanClass; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                RpcReference reference = field.getAnnotation(RpcReference.class);
                if (reference == null) {
                    continue;
                }
                Object proxy = clientProxy.createProxy(
                        reference.value(), reference.timeoutMs(), reference.loadBalancer());
                try {
                    field.trySetAccessible();
                    field.set(bean, proxy);
                    log.info("[RPC] 注入服务引用: {} @ {}", field.getType().getName(), beanClass.getSimpleName());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("@RpcReference 字段注入失败: " + field, e);
                }
            }
        }
        return bean;
    }
}
