package com.landray.rag.common.domain;

/**
 * 内容模态（多模态 RAG 预留，Phase 8 启用）
 *
 * <p>当前阶段只有 {@link #TEXT} 真正在跑；IMAGE/AUDIO 为架构预留：
 * 数据模型先认识"模态"这个概念，图片/音频的处理能力在 Phase 8 按需交付
 * （OCR → CLIP 图文双塔 → VLM），避免上线后再改表结构和做数据迁移。</p>
 *
 * 【面试】为什么用枚举而不是 String 魔法值？
 * 1) 编译期约束，modality 只能取合法值，防止 "img"/"IMAGE"/"pic" 等脏数据；
 * 2) 枚举自带 switch 穷举检查，未来加 VIDEO 模态时编译器会提示所有分支；
 * 3) 持久化统一走 code（小写字符串），与 Milvus/MySQL 中存储值解耦——
 *    枚举名重命名不影响存量数据。
 */
public enum Modality {

    /** 文本（当前唯一实际使用的模态，也是默认值） */
    TEXT("text"),

    /** 图片（截图 / 照片 / 扫描页 / 图文混排 PDF 抽出的图） */
    IMAGE("image"),

    /** 音频（语音问答、会议录音转写等，远期） */
    AUDIO("audio");

    /** 持久化/传输用的稳定编码，不要用 ordinal()（重排即错位） */
    private final String code;

    Modality(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 从存储值解析，非法值兜底为 TEXT，保证旧数据可读 */
    public static Modality fromCode(String code) {
        if (code != null) {
            for (Modality m : values()) {
                if (m.code.equals(code)) {
                    return m;
                }
            }
        }
        return TEXT;
    }
}
