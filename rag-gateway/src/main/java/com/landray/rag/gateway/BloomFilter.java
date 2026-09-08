package com.landray.rag.gateway;

/**
 * 布隆过滤器接口
 *
 * Phase 2：手写 BitSet + MurmurHash3，集成 Redis 做分布式版
 * 用途：快速判断"肯定不存在"，减少向量库无效查询
 */
public interface BloomFilter {

    /**
     * 添加元素
     */
    void add(String key);

    /**
     * 判断元素是否可能存在（存在误判，但不会漏判）
     */
    boolean mightContain(String key);

    /**
     * 清空
     */
    void clear();

    /**
     * 当前估计元素数量
     */
    long estimateCount();
}
