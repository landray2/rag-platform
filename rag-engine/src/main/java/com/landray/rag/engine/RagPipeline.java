package com.landray.rag.engine;

import com.landray.rag.common.domain.ChatRequest;
import com.landray.rag.common.domain.ChatResponse;

/**
 * RAG 处理管线接口
 *
 * 标准管线：
 *   QueryRewrite → Embedding → HybridSearch → Rerank → MemoryAugment → PromptBuild → LLMGenerate
 *
 * 每个阶段可独立替换实现，方便调试和性能优化。
 * Phase 0：串行实现
 * Phase 5：Embedding + 检索阶段并行（虚拟线程）
 */
public interface RagPipeline {

    /**
     * 执行完整管线
     *
     * @param request 聊天请求
     * @return 聊天响应
     */
    ChatResponse execute(ChatRequest request);
}
