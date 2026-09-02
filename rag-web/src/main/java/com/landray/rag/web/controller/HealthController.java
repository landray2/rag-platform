package com.landray.rag.web.controller;

import com.landray.rag.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查 + 虚拟线程验证
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> info = new HashMap<>();
        info.put("status", "UP");
        info.put("service", "rag-platform");
        info.put("threadName", Thread.currentThread().getName());
        info.put("isVirtualThread", Thread.currentThread().isVirtual());
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("timestamp", System.currentTimeMillis());
        log.info("健康检查 - 线程: {} (虚拟线程: {})",
                Thread.currentThread().getName(),
                Thread.currentThread().isVirtual());
        return R.ok(info);
    }

    /**
     * 虚拟线程压力测试接口
     * 并发1000次调用，验证虚拟线程能力
     */
    @GetMapping("/virtual-thread-test")
    public R<Map<String, Object>> virtualThreadTest() {
        long start = System.currentTimeMillis();
        int count = 1000;
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            threads[i] = Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        long cost = System.currentTimeMillis() - start;

        Map<String, Object> result = new HashMap<>();
        result.put("virtualThreads", count);
        result.put("costMs", cost);
        result.put("currentThreadIsVirtual", Thread.currentThread().isVirtual());
        return R.ok(result);
    }
}