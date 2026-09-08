package com.landray.rag.rpc;

import java.lang.annotation.*;

/**
 * 暴露 RPC 服务（标注在服务实现类上）
 *
 * Phase 1：自定义 BeanPostProcessor 自动扫描注册
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
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
