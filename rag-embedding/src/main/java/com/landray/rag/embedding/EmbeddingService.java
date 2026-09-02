package com.landray.rag.embedding;

import java.util.List;

/**
 * 向量化服务接口
 *
 * Phase 0：远程 Embedding API 调用
 * Phase 3：DJL + ONNX 本地推理（替换远程API）
 */
public interface EmbeddingService {

    /**
     * 单条文本向量化
     *
     * @param text 输入文本
     * @return 向量数组
     */
    float[] embed(String text);

    /**
     * 批量文本向量化
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 获取向量维度
     */
    int getDimension();
}