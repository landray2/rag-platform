package com.landray.rag.web.controller;

import com.landray.rag.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档管理接口
 */
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    /**
     * 上传文档
     * Phase 0：仅接收文件并返回占位
     * Phase 4：接入 DocumentParser + 异步解析队列
     */
    @PostMapping("/upload")
    public R<String> upload(@RequestParam("file") MultipartFile file,
                            @RequestParam(value = "knowledgeBaseId", required = false) String knowledgeBaseId) {
        // TODO: Phase 4 实现 - 存文件 → 异步解析 → 切片 → 向量化 → Milvus
        return R.ok("文档接收成功，ID: doc_" + System.currentTimeMillis());
    }

    /**
     * 获取文档列表
     */
    @GetMapping
    public R<Object> list(@RequestParam(value = "knowledgeBaseId", required = false) String knowledgeBaseId) {
        // TODO: Phase 4 实现 - MyBatis Plus 查询
        return R.ok("[]");
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{documentId}")
    public R<Boolean> delete(@PathVariable String documentId) {
        // TODO: Phase 4 实现 - MySQL + Milvus 双删
        return R.ok(true);
    }
}
