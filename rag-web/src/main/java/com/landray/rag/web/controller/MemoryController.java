package com.landray.rag.web.controller;

import com.landray.rag.common.result.R;
import com.landray.rag.memory.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ⚠️ MEMORY 接口位置
 *
 * 记忆层的 Web 接口在这里。底层实现委托给 rag-memory 模块的接口。
 * 当 rag-memory 内部架构变更时，这里只需换依赖注入的实现类。
 *
 * 各记忆接口实现位置：
 *   - PersonaMemory     → rag-memory/src/main/java/com/landray/rag/memory/PersonaMemory.java
 *   - WorkingMemory     → rag-memory/src/main/java/com/landray/rag/memory/WorkingMemory.java
 *   - EpisodicMemory    → rag-memory/src/main/java/com/landray/rag/memory/EpisodicMemory.java
 *   - MemoryFacade      → rag-memory/src/main/java/com/landray/rag/memory/MemoryFacade.java（RagEngine 调用入口）
 *   - MemoryStorage     → rag-memory/src/main/java/com/landray/rag/memory/MemoryStorage.java（底层存储抽象）
 *
 * 实现尚未落地（Phase 7），用 ObjectProvider 可选注入保证应用可启动。
 */
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final ObjectProvider<PersonaMemory> personaMemoryProvider;
    private final ObjectProvider<WorkingMemory> workingMemoryProvider;
    private final ObjectProvider<EpisodicMemory> episodicMemoryProvider;
    private final ObjectProvider<MemoryFacade> memoryFacadeProvider;

    private <T> T require(ObjectProvider<T> provider) {
        T impl = provider.getIfAvailable();
        if (impl == null) {
            throw new IllegalStateException("Memory 层实现尚未落地（Phase 7 交付），接口已预留");
        }
        return impl;
    }

    // ==================== Persona ====================

    /**
     * 获取用户画像
     */
    @GetMapping("/persona/{userId}")
    public R<Map<String, String>> getPersona(@PathVariable String userId) {
        return R.ok(require(personaMemoryProvider).getAllAttributes(userId));
    }

    /**
     * 设置画像字段
     */
    @PutMapping("/persona/{userId}")
    public R<Boolean> setPersona(@PathVariable String userId,
                                  @RequestBody Map<String, String> attributes) {
        require(personaMemoryProvider).mergeAttributes(userId, attributes);
        return R.ok(true);
    }

    /**
     * 获取 Persona Prompt（调试用）
     */
    @GetMapping("/persona/{userId}/prompt")
    public R<String> getPersonaPrompt(@PathVariable String userId) {
        return R.ok(require(personaMemoryProvider).getPersonaPrompt(userId));
    }

    // ==================== Working Memory ====================

    /**
     * 获取会话上下文
     */
    @GetMapping("/working/{sessionId}")
    public R<Object> getWorkingContext(@PathVariable String sessionId,
                                       @RequestParam(defaultValue = "4000") int maxTokens) {
        return R.ok(require(workingMemoryProvider).getRecentContext(sessionId, maxTokens));
    }

    /**
     * 清空会话记忆
     */
    @DeleteMapping("/working/{sessionId}")
    public R<Boolean> clearWorking(@PathVariable String sessionId) {
        require(workingMemoryProvider).clear(sessionId);
        return R.ok(true);
    }

    // ==================== Episodic ====================

    /**
     * 检索情景记忆
     */
    @GetMapping("/episodic/{userId}/search")
    public R<Object> searchEpisodic(@PathVariable String userId,
                                     @RequestParam String query,
                                     @RequestParam(defaultValue = "5") int topK) {
        return R.ok(require(episodicMemoryProvider).search(userId, query, topK));
    }

    // ==================== Facade ====================

    /**
     * 完整记忆增强 Prompt（调试用）
     */
    @GetMapping("/facade/prompt")
    public R<String> buildMemoryPrompt(@RequestParam String userId,
                                       @RequestParam String sessionId,
                                       @RequestParam String question) {
        return R.ok(require(memoryFacadeProvider)
                .buildMemoryAugmentedPrompt(userId, sessionId, question));
    }
}
