package com.landray.rag.parser;

import java.io.InputStream;
import java.util.List;

/**
 * 文档解析器接口
 *
 * Phase 4 实现：
 * - Apache Tika 支持 PDF/Word/TXT/Markdown
 * - 虚拟线程并发解析
 * - NIO 零拷贝大文件读取
 */
public interface DocumentParser {

    /**
     * 解析文档
     *
     * @param inputStream 文件输入流
     * @param fileName    文件名（用于识别类型）
     * @return 解析出的文本内容
     */
    String parse(InputStream inputStream, String fileName);

    /**
     * 解析并切片
     *
     * @param inputStream 文件输入流
     * @param fileName    文件名
     * @param chunkSize   切片大小（字符数）
     * @param overlap     重叠大小
     * @return 切片列表
     */
    List<String> parseAndChunk(InputStream inputStream, String fileName, int chunkSize, int overlap);
}