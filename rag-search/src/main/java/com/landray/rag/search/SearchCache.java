package com.landray.rag.search;

import com.landray.rag.common.domain.SearchHit;

import java.util.List;
import java.util.Map;

/**
 * ⚠️ ARCHITECTURE NOTE — 检索缓存接口
 *
 * Phase 5 实现：手写 LRU 缓存（双向链表 + HashMap）
 * 缓存"查询向量 → 检索结果"的映射，避免重复向量化和检索
 *
 * 架构可变点：
 *   - 存储介质：本地内存 LRU → Redis 分布式缓存 → 两级缓存
 *   - 淘汰策略：LRU → LFU → TinyLFU → 自适应
 *   - 预热机制：无 → 定时预热 → 访问驱动
 *
 * ⚠️ 位置：rag-search/src/main/java/com/landray/rag/search/SearchCache.java
 */
public interface SearchCache {

    /**
     * 获取缓存
     *
     * @param cacheKey 缓存 key（通常是 query 的 hash）
     * @return 缓存的检索结果，未命中返回 null
     */
    Map<String, Object> get(String cacheKey);

    /**
     * 写入缓存
     *
     * @param cacheKey  缓存 key
     * @param hits      检索结果
     * @param ttlSeconds 过期时间（秒）
     */
    void put(String cacheKey, List<SearchHit> hits, int ttlSeconds);

    /**
     * 清除缓存
     */
    void invalidate(String cacheKey);

    /**
     * 缓存统计
     */
    CacheStats stats();

    record CacheStats(long hitCount, long missCount, long size) {
        public double hitRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) hitCount / total;
        }
    }

    /** 默认空实现（Phase 0 无缓存） */
    SearchCache NOOP = new SearchCache() {
        @Override public Map<String, Object> get(String cacheKey) { return null; }
        @Override public void put(String cacheKey, List<SearchHit> hits, int ttlSeconds) {}
        @Override public void invalidate(String cacheKey) {}
        @Override public CacheStats stats() { return new CacheStats(0, 0, 0); }
    };
}
