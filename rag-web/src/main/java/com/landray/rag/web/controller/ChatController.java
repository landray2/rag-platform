package com.landray.rag.web.controller;

import com.landray.rag.common.domain.ChatRequest;
import com.landray.rag.common.domain.ChatResponse;
import com.landray.rag.common.result.R;
import com.landray.rag.engine.RagEngine;
import com.landray.rag.gateway.RateLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * RAG 问答接口
 *
 * RagEngine 实现属于 Phase 6（编排引擎），当前用 ObjectProvider 可选注入，
 * 引擎缺失时返回友好提示而不是阻塞整个应用启动。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ObjectProvider<RagEngine> ragEngineProvider;

    private RagEngine engine() {
        RagEngine engine = ragEngineProvider.getIfAvailable();
        if (engine == null) {
            throw new IllegalStateException("RAG 引擎尚未实现（Phase 6 交付），敬请期待");
        }
        return engine;
    }

    /**
     * 问答（非流式）
     */
    @PostMapping
    @RateLimit(key = "#request.userId", qps = 10, capacity = 20)
    public R<ChatResponse> chat(@RequestBody @Validated ChatRequest request) {
        return R.ok(engine().chat(request));
    }

    /**
     * 流式问答（SSE）
     */
    @PostMapping("/stream")
    @RateLimit(key = "#request.userId", qps = 5, capacity = 10)
    public R<String> chatStream(@RequestBody @Validated ChatRequest request) {
        return R.ok(engine().chatStream(request));
    }
}
