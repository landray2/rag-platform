package com.landray.rag.engine;

/**
 * 管线阶段枚举（用于调试日志和耗时统计）
 */
public enum PipelineStage {
    QUERY_REWRITE("查询改写"),
    EMBEDDING("向量化"),
    HYBRID_SEARCH("混合检索"),
    RERANK("重排序"),
    MEMORY_AUGMENT("记忆增强"),
    PROMPT_BUILD("Prompt构造"),
    LLM_GENERATE("LLM生成");

    private final String label;

    PipelineStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
