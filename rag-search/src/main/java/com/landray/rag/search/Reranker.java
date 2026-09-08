package com.landray.rag.search;

import com.landray.rag.common.domain.SearchHit;

import java.util.List;

/**
 * 重排序接口
 *
 * Phase 5：手写归并排序 + TopK 堆优化
 * 后续可替换为 CrossEncoder / ColBERT 等深度重排模型
 */
public interface Reranker {

    /**
     * 对检索结果重排序
     *
     * @param query  当前问题
     * @param hits   初始检索结果
     * @param topK   重排序后保留的数量
     * @return 重排序后的结果
     */
    List<SearchHit> rerank(String query, List<SearchHit> hits, int topK);
}
