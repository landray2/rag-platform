package com.landray.rag.web.controller;

import com.landray.rag.common.domain.ChatRequest;
import com.landray.rag.common.domain.ChatResponse;
import com.landray.rag.common.result.R;
import com.landray.rag.engine.RagEngine;
import com.landray.rag.gateway.RateLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * RAG 问答接口
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagEngine ragEngine;

    /**
     * 问答（非流式）
     */
    @PostMapping
    @RateLimit(key = "#request.userId", qps = 10, capacity = 20)
    public R<ChatResponse> chat(@RequestBody @Validated ChatRequest request) {
        ChatResponse response = ragEngine.chat(request);
        return R.ok(response);
    }

    /**
     * 流式问答（SSE）
     */
    @PostMapping("/stream")
    @RateLimit(key = "#request.userId", qps = 5, capacity = 10)
    public R<String> chatStream(@RequestBody @Validated ChatRequest request) {
        String stream = ragEngine.chatStream(request);
        return R.ok(stream);
    }
}
