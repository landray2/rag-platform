package com.landray.rag.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档切片（向量化和检索的最小单元）
 *
 * <p>多模态预留（Phase 8 启用，当前全部走 TEXT 默认值，文本链路行为不变）：
 * 一个切片 = 模态 + 文本表示(content/caption) + 向量(vector) + 原始二进制引用(blobRef)。</p>
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

    /**
     * 切片文本内容
     * <p>文本切片：正文；图片/音频切片：可为空，文本侧写由 {@link #caption} 承担。</p>
     */
    private String content;

    /** 向量（Phase 3 起：文本 Embedding；Phase 8.3 起：CLIP 双塔向量，按模态走不同 collection） */
    private float[] vector;

    /**
     * 模态（多模态预留，默认文本）
     *
     * 【面试】向后兼容设计：默认值 TEXT + 持久化层旧数据无此列时按 TEXT 兜底，
     * 属于"对扩展开放、对修改封闭"——新能力上线不需要迁移存量切片。
     * 注意 @Builder.Default：Lombok 的 Builder 默认会覆盖字段初始化器，
     * 不加此注解则 builder.build() 得到 null 而不是 TEXT（实际踩坑点）。
     */
    @Builder.Default
    private Modality modality = Modality.TEXT;

    /**
     * 原始二进制在 MinIO 中的对象 key（多模态预留，文本切片为 null）
     * <p>key 规范：{kbId}/{documentId}/{chunkId}.{ext}</p>
     *
     * 【面试·高频】为什么存引用而不是 byte[]/Base64？
     * 三存储分离——原始二进制(MinIO 对象存储)、向量(Milvus)、元数据(MySQL)各归其位：
     * 1) Milvus/MySQL 不适合存大 blob，撑爆页缓存、拖垮向量检索；
     * 2) 二进制经 RPC/JSON 传输要 Base64 膨胀约 33%，用 key + 预签名 URL 按需取；
     * 3) 对象存储天然支持 CDN/分片上传/生命周期。
     */
    private String blobRef;

    /**
     * 图片/音频的文字替身（OCR 文本或图像描述 caption，多模态预留，Phase 8.2 起填充）
     *
     * 【面试】非文本内容为什么需要一段文字？
     * BM25 稀疏检索和 Cross-Encoder Rerank 只能吃文本；caption 让图片也能参与
     * 关键词召回与重排，与 CLIP 向量召回形成"稠密 + 稀疏"双路互补。
     */
    private String caption;

    /** 元数据（来源、作者、时间等，JSON） */
    private String metadata;

    /** 创建时间戳 */
    private long createdAt;
}
