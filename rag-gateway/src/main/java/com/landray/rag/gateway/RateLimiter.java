package com.landray.rag.gateway;

/**
 * 分布式限流器接口
 *
 * Phase 2：Redis + Lua 令牌桶实现
 * 后续可扩展：漏桶 / 滑动窗口 / 固定窗口
 */
public interface RateLimiter {

    /**
     * 尝试获取令牌
     *
     * @param key       限流 key（如 "user:123" 或 "api:/chat"）
     * @param permits   需要的令牌数
     * @return true=获取成功，false=被限流
     */
    boolean tryAcquire(String key, int permits);

    /**
     * 尝试获取令牌（带等待时间）
     *
     * @param key         限流 key
     * @param permits     需要的令牌数
     * @param timeoutMs   最长等待毫秒数
     */
    boolean tryAcquire(String key, int permits, long timeoutMs);

    /**
     * 获取当前剩余令牌数（用于监控）
     */
    long getRemainingTokens(String key);
}
