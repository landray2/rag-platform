package com.landray.rag.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聊天响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 回答文本 */
    private String answer;

    /** 引用的检索片段 */
    private List<SearchHit> references;

    /** Prompt 中的记忆片段（用于调试） */
    private String memoryPrompt;

    /** 总耗时（毫秒） */
    private long totalLatencyMs;

    /** 各阶段耗时（毫秒） */
    private LatencyBreakdown latencyBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatencyBreakdown {
        private long embeddingMs;    // 向量化
        private long retrievalMs;    // 检索
        private long rerankMs;       // 重排序
        private long memoryMs;       // 记忆增强
        private long llmMs;          // LLM 生成
    }
}
