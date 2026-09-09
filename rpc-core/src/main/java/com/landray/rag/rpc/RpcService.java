package com.landray.rag.rpc;

import java.lang.annotation.*;

/**
 * 暴露 RPC 服务（标注在服务实现类上）
 *
 * Phase 1：自定义 BeanPostProcessor 自动扫描注册
 *
 * 【面试】为什么元注解 @Component？
 * 让 @RpcService 同时具备组件扫描能力（Spring 组合注解机制，同 @RestController）：
 * 服务实现类只标一个注解 = 注册为 Bean + 暴露为 RPC Provider，避免使用者还要加 @Service。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@org.springframework.stereotype.Component
public @interface RpcService {

    /**
     * 服务接口（默认取实现的第一个接口）
     */
    Class<?> value() default Void.class;

    /**
     * 服务版本（用于多版本共存）
     */
    String version() default "1.0.0";
}
