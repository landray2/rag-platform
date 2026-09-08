package com.landray.rag.memory;

import java.util.Map;

/**
 * ⚠️ ARCHITECTURE NOTE — Persona Memory（用户画像记忆）
 *
 * 存储用户稳定、长期的特征信息：
 *   - 用户偏好（语言、专业领域、回答风格偏好）
 *   - 用户身份（职业、角色、常用术语）
 *   - 个性化设置（是否启用流式回答、温度参数等）
 *
 * 架构可变点：
 *   - 存储介质：Redis → MySQL → 向量库（语义画像检索）
 *   - 更新策略：手动维护 → LLM 自动抽取 → 交互式确认
 *   - 重要性衰减：不衰减 → 时间衰减 → 交互频次加权
 *
 * ⚠️ 位置：rag-memory/src/main/java/com/landray/rag/memory/PersonaMemory.java
 */
public interface PersonaMemory {

    /**
     * 获取用户画像（合并所有 persona 字段为 Prompt 片段）
     *
     * @param userId 用户ID
     * @return 用户画像描述，如 "用户是一名 Java 后端工程师，偏好简洁回答"
     */
    String getPersonaPrompt(String userId);

    /**
     * 获取画像字段
     */
    String getAttribute(String userId, String key);

    /**
     * 设置画像字段
     */
    void setAttribute(String userId, String key, String value);

    /**
     * 获取完整画像 Map
     */
    Map<String, String> getAllAttributes(String userId);

    /**
     * 批量更新画像（合并，不覆盖已有字段）
     */
    void mergeAttributes(String userId, Map<String, String> attributes);

    /**
     * 从对话中自动抽取并更新画像（Phase 7 实现，当前 stub）
     *
     * @param userId  用户ID
     * @param message 最新对话消息
     */
    void extractAndUpdate(String userId, String message);
}
