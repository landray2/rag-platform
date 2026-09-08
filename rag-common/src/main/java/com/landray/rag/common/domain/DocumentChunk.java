package com.landray.rag.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档切片（向量化和检索的最小单元）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    /** 切片唯一ID */
    private String chunkId;

    /** 所属文档ID */
    private String documentId;

    /** 切片序号（同一文档内的顺序） */
    private int index;

    /** 切片文本内容 */
    private String content;

    /** 向量（Phase 3 填充） */
    private float[] vector;

    /** 元数据（来源、作者、时间等，JSON） */
    private String metadata;

    /** 创建时间戳 */
    private long createdAt;
}
