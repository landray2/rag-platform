package com.landray.rag.memory;

import java.util.List;

/**
 * ⚠️ ARCHITECTURE NOTE — Episodic Memory（情景记忆 / 历史交互片段）
 *
 * 存储用户过去的交互片段，用于跨会话的个性化检索和"记得之前聊过什么"。
 *
 * 架构可变点：
 *   - 存储介质：MySQL（结构化存储） → Milvus（向量化 + 语义检索）→ 混合
 *   - 检索方式：关键词匹配 → 向量相似度 → 混合 RRF 融合
 *   - 遗忘机制：时间衰减 → 重要性权重 → 用户显式删除
 *   - 压缩策略：原始片段 → 摘要 → 关键事实提取
 *
 * ⚠️ 位置：rag-memory/src/main/java/com/landray/rag/memory/EpisodicMemory.java
 */
public interface EpisodicMemory {

    /**
     * 存储一段情景记忆
     *
     * @param userId     用户ID
     * @param sessionId  会话ID
     * @param content    记忆内容（通常是一段对话摘要或关键事实）
     * @param importance 重要性 0.0 ~ 1.0（用于遗忘排序）
     * @param tags       标签（如 ["技术", "Java", "RPC"]）
     */
    void store(String userId, String sessionId, String content, double importance, List<String> tags);

    /**
     * 语义检索相关记忆（用于 RAG 增强）
     *
     * @param userId  用户ID
     * @param query   当前问题
     * @param topK    返回数量
     * @return 相关记忆列表，按相关度降序
     */
    List<EpisodicEntry> search(String userId, String query, int topK);

    /**
     * 按标签检索
     */
    List<EpisodicEntry> searchByTags(String userId, List<String> tags, int topK);

    /**
     * 衰减过期记忆（定时任务调用）
     *
     * @param maxAgeDays 最大保留天数
     */
    void decayExpired(String userId, int maxAgeDays);

    /**
     * 情景记忆条目
     */
    record EpisodicEntry(
            String id,
            String sessionId,
            String content,
            double importance,
            List<String> tags,
            long timestamp,
            float relevanceScore  // 检索时计算的相关度
    ) {}
}
