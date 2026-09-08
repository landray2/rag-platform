package com.landray.rag.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** 用户ID */
    private String userId;

    /** 会话ID（同一用户多轮对话共享） */
    private String sessionId;

    /** 用户问题 */
    private String question;

    /** 知识库ID（可选，为空则检索全部） */
    private String knowledgeBaseId;

    /** 检索 topK */
    private int topK;

    /** 是否启用记忆 */
    @Builder.Default
    private boolean memoryEnabled = true;

    /** 是否流式输出 */
    @Builder.Default
    private boolean stream = false;
}
