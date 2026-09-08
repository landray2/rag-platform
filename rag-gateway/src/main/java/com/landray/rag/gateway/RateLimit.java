package com.landray.rag.gateway;

import java.lang.annotation.*;

/**
 * 限流注解（标注在 Controller / Service 方法上）
 *
 * Phase 2：AOP 切面拦截，调用 RateLimiter
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流 key（支持 SpEL 表达式，如 "#userId"）
     */
    String key() default "";

    /**
     * 每秒 QPS
     */
    int qps() default 10;

    /**
     * 桶容量（最大突发量）
     */
    int capacity() default 20;

    /**
     * 预热时间（毫秒），0 表示无预热
     */
    long warmupMs() default 0;
}
