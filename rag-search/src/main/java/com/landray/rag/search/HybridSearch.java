package com.landray.rag.search;

import com.landray.rag.common.domain.SearchHit;

import java.util.List;

/**
 * 混合检索接口
 *
 * Phase 5：向量检索（Milvus）+ 关键词检索（BM25）→ RRF 融合
 * 默认实现委托给 VectorStore，Phase 5 替换为真正的混合逻辑
 */
public interface HybridSearch {

    /**
     * 混合检索
     *
     * @param query       原始问题文本
     * @param queryVector 问题向量（由 EmbeddingService 提前生成）
     * @param topK        返回数量
     * @param vectorWeight 向量检索权重
     * @param keywordWeight 关键词检索权重
     */
    List<SearchHit> search(String query, float[] queryVector, int topK, float vectorWeight, float keywordWeight);

    /**
     * RRF 倒数排名融合
     *
     * @param list1 列表1（按排名升序）
     * @param list2 列表2（按排名升序）
     * @param k     RRF 参数（通常 60）
     */
    List<SearchHit> rrfFusion(List<SearchHit> list1, List<SearchHit> list2, int k);
}
