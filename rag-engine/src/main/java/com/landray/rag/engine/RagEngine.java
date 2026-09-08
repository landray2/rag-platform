package com.landray.rag.engine;

import com.landray.rag.common.domain.ChatRequest;
import com.landray.rag.common.domain.ChatResponse;

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
     * 问答（完整版，含检索引用、耗时统计）
     *
     * @param request 聊天请求
     * @return 聊天响应
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 问答（简化版，仅返回回答文本）
     */
    default String chat(String userId, String question) {
        ChatRequest request = ChatRequest.builder()
                .userId(userId)
                .question(question)
                .sessionId("session_" + userId)
                .build();
        return chat(request).getAnswer();
    }

    /**
     * 流式问答（SSE）
     */
    String chatStream(ChatRequest request);

    /**
     * 流式问答（简化版）
     */
    default String chatStream(String userId, String question) {
        ChatRequest request = ChatRequest.builder()
                .userId(userId)
                .question(question)
                .sessionId("session_" + userId)
                .build();
        return chatStream(request);
    }
}
