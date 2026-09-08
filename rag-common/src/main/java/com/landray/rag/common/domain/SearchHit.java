package com.landray.rag.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索结果（重排序前的原始检索输出）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHit {

    /** 切片ID */
    private String chunkId;

    /** 文档ID */
    private String documentId;

    /** 切片内容 */
    private String content;

    /** 元数据 */
    private String metadata;

    /** 原始相似度分数 */
    private float rawScore;

    /** 重排序后的分数（Phase 5 填充） */
    private float rerankScore;

    /** 融合后的最终分数 */
    private float finalScore;
}
