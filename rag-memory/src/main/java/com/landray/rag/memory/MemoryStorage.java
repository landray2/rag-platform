package com.landray.rag.memory;

import java.util.List;

/**
 * ⚠️ ARCHITECTURE NOTE — 记忆存储接口
 *
 * 这是所有记忆层的底层存储抽象。当前仅定义接口，实现留空。
 * 后续可能替换的存储介质：
 *   - Redis Hash/String（轻量快速，当前默认方案）
 *   - MySQL + MyBatis-Plus（持久化 + 复杂查询）
 *   - Milvus 向量库（记忆向量化，语义检索）
 *   - 混合方案（热数据 Redis + 冷数据 MySQL + 向量化检索 Milvus）
 *
 * 替换存储时，只需新增一个 MemoryStorage 实现类，无需改动上层接口。
 *
 * ⚠️ 位置：rag-memory/src/main/java/com/landray/rag/memory/MemoryStorage.java
 */
public interface MemoryStorage {

    /**
     * 存储一条记忆
     *
     * @param userId   用户ID
     * @param type     记忆类型（persona / working / episodic）
     * @param key      记忆 key（如 "name"、"lastTopic"）
     * @param value    记忆值（JSON 序列化后的字符串）
     */
    void put(String userId, String type, String key, String value);

    /**
     * 获取一条记忆
     *
     * @return 记忆值，不存在返回 null
     */
    String get(String userId, String type, String key);

    /**
     * 获取某用户某类型下所有记忆
     *
     * @return key → value 映射
     */
    List<MemoryEntry> list(String userId, String type);

    /**
     * 删除一条记忆
     */
    void delete(String userId, String type, String key);

    /**
     * 清空某用户某类型下所有记忆
     */
    void clear(String userId, String type);

    /**
     * 记忆条目
     */
    record MemoryEntry(String key, String value, long timestamp) {}
}
