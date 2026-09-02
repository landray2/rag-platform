package com.landray.rag.search;

import java.util.List;

/**
 * 向量存储接口
 *
 * Phase 0：Milvus SDK 封装
 * Phase 5：混合排序 + LRU 缓存优化
 */
public interface VectorStore {

    /**
     * 插入向量
     *
     * @param id      文档切片ID
     * @param vector  向量
     * @param text    原文文本
     * @param metadata 元数据（来源、时间等）
     */
    void insert(String id, float[] vector, String text, String metadata);

    /**
     * 批量插入
     */
    void insertBatch(List<String> ids, List<float[]> vectors, List<String> texts, List<String> metadatas);

    /**
     * 相似度检索
     *
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @return 检索结果
     */
    List<SearchResult> search(float[] queryVector, int topK);

    /**
     * 按过滤条件检索
     */
    List<SearchResult> search(float[] queryVector, int topK, String filterExpr);

    /**
     * 删除文档
     */
    void delete(String id);

    record SearchResult(String id, String text, String metadata, float score) {}
}