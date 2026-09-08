package com.landray.rag.memory;

/**
 * ⚠️ ARCHITECTURE NOTE — MemoryFacade（记忆层统一门面）
 *
 * RagEngine 通过此门面访问三种记忆，不直接依赖具体实现。
 * 这样当底层存储或算法变更时，RagEngine 无需修改。
 *
 * 架构可变点：
 *   - 当前：Facade 模式，委托给 Persona/Working/Episodic 三个子接口
 *   - 未来：可改为 Composite / Chain of Responsibility / 事件驱动
 *   - 甚至可以替换为 Memory Server（独立微服务，RPC 调用）
 *
 * ⚠️ 位置：rag-memory/src/main/java/com/landray/rag/memory/MemoryFacade.java
 */
public interface MemoryFacade {

    /**
     * 获取用户画像 Prompt（注入到系统提示中）
     */
    String getPersonaPrompt(String userId);

    /**
     * 获取当前会话上下文（注入到对话历史中）
     */
    String getWorkingContext(String sessionId, int maxTokens);

    /**
     * 检索相关情景记忆（注入到 RAG 检索结果中）
     */
    String getRelevantEpisodicMemory(String userId, String query, int topK);

    /**
     * 完整的记忆增强 Prompt（RagEngine 调用入口）
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param question  当前问题
     * @return 可直接拼接到 Prompt 的记忆片段
     */
    String buildMemoryAugmentedPrompt(String userId, String sessionId, String question);

    /**
     * 通知记忆层：本轮对话结束，需要更新记忆
     *
     * @param userId      用户ID
     * @param sessionId   会话ID
     * @param userMessage 用户消息
     * @param botReply    机器人回复
     */
    void onTurnComplete(String userId, String sessionId, String userMessage, String botReply);
}
