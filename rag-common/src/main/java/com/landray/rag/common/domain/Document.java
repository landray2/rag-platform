package com.landray.rag.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    /** 文档唯一ID */
    private String documentId;

    /** 文件名 */
    private String fileName;

    /** 文件类型（pdf / docx / md / txt；多模态阶段扩展 png / jpg / 图片型 pdf 等） */
    private String fileType;

    /** 文件大小（字节） */
    private long fileSize;

    /**
     * 原始上传文件在 MinIO 中的对象 key（多模态预留，Phase 8.1 起填充）
     * <p>key 规范：{kbId}/{documentId}/origin.{ext}。本地文件解析阶段可为空。</p>
     */
    private String blobRef;

    /** 所属知识库ID */
    private String knowledgeBaseId;

    /** 上传者ID */
    private String uploaderId;

    /** 切片数量 */
    private int chunkCount;

    /** 解析状态：PENDING → PARSING → COMPLETED / FAILED */
    private String status;

    /** 关联切片 */
    private List<DocumentChunk> chunks;

    /** 创建时间戳 */
    private long createdAt;
}
