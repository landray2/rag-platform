package com.landray.rag.memory;

import java.util.List;

/**
 * ⚠️ ARCHITECTURE NOTE — Working Memory（工作记忆 / 上下文窗口）
 *
 * 存储当前会话的短期上下文，是 RAG Prompt 构造的核心输入。
 * 典型内容：最近 N 轮对话、当前话题、未解决的子问题。
 *
 * 架构可变点：
 *   - 窗口策略：固定轮数 → Token 计数 → 滑动窗口 + 摘要压缩
 *   - 压缩方式：直接截断 → LLM 摘要 → 关键句提取
 *   - 存储介质：Redis（会话级，会话结束可丢弃）
 *
 * ⚠️ 位置：rag-memory/src/main/java/com/landray/rag/memory/WorkingMemory.java
 */
public interface WorkingMemory {

    /**
     * 添加一条对话消息
     *
     * @param sessionId 会话ID
     * @param role      角色（user / assistant）
     * @param content   消息内容
     */
    void addMessage(String sessionId, String role, String content);

    /**
     * 获取最近的对话历史（用于 Prompt 构造）
     *
     * @param sessionId 会话ID
     * @param maxTokens 最大 Token 数（软上限）
     * @return 对话历史列表
     */
    List<ConversationTurn> getRecentContext(String sessionId, int maxTokens);

    /**
     * 设置当前话题（用于检索时的上下文定向）
     */
    void setCurrentTopic(String sessionId, String topic);

    /**
     * 获取当前话题
     */
    String getCurrentTopic(String sessionId);

    /**
     * 清空会话记忆
     */
    void clear(String sessionId);

    /**
     * 对话轮次
     */
    record ConversationTurn(String role, String content, long timestamp) {}
}
