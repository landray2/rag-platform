package com.landray.rag.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RAG平台启动类
 *
 * 配置说明：
 * - spring.threads.virtual.enabled=true 开启虚拟线程（JDK 21+）
 * - 所有 @RestController 请求将自动使用虚拟线程处理
 */
@SpringBootApplication(scanBasePackages = "com.landray.rag")
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
        System.out.println("""
                
                ============================================
                🚀 RAG Platform Started Successfully!
                🌐 Health: http://localhost:8080/actuator/health
                💬 API:   http://localhost:8080/api/chat
                ============================================
                """);
    }
}