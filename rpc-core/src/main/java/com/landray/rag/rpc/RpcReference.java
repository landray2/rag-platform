package com.landray.rag.rpc;

import java.lang.annotation.*;

/**
 * 引用 RPC 服务（标注在消费端字段上）
 *
 * Phase 1：自定义 BeanPostProcessor 自动注入代理
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcReference {

    /**
     * 服务接口
     */
    Class<?> value();

    /**
     * 超时时间（毫秒）
     */
    long timeoutMs() default 3000;

    /**
     * 负载均衡算法
     */
    String loadBalancer() default "round-robin";

    /**
     * 服务版本
     */
    String version() default "1.0.0";
}
