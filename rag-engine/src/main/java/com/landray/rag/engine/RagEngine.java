package com.landray.rag.engine;

/**
 * RAG 编排引擎接口
 *
 * 完整流程：
 * 用户问题 → 查询改写（可选）→ Embedding → 多路检索 → 重排序
 *          → Prompt 构造（含用户记忆 + 知识库上下文）→ LLM 生成 → 返回
 *
 * Phase 0：基础链路
 * Phase 7：注入用户记忆（rag-memory 模块）
 */
public interface RagEngine {

    /**
     * 问答
     *
     * @param userId  用户ID（用于个性化记忆）
     * @param question 用户问题
     * @return 回答
     */
    String chat(String userId, String question);

    /**
     * 流式问答（SSE）
     *
     * @param userId   用户ID
     * @param question 用户问题
     * @return 流式输出
     */
    String chatStream(String userId, String question);
}