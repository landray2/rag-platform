package com.landray.rag.web.demo;

import com.landray.rag.common.result.R;
import com.landray.rag.rpc.RpcReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RPC 验证控制器（服务消费方）
 *
 * 字段上的 @RpcReference 会被 RpcReferencePostProcessor 自动替换为动态代理，
 * 调用 demoService.sayHello(...) 实际走的是：
 * 动态代理 → Nacos 服务发现 → 负载均衡选节点 → Netty 长连接 → Kryo 序列化 →
 * 服务端虚拟线程反射执行 → Kryo 反序列化 → Future 配对返回。
 */
@Slf4j
@RestController
@RequestMapping("/api/rpc")
public class RpcDemoController {

    @RpcReference(value = DemoService.class, timeoutMs = 3000, loadBalancer = "round-robin")
    private DemoService demoService;

    /** GET /api/rpc/hello?name=Tom */
    @GetMapping("/hello")
    public R<String> hello(@RequestParam(defaultValue = "World") String name) {
        String result = demoService.sayHello(name);
        return R.ok(result);
    }

    /** GET /api/rpc/system-info */
    @GetMapping("/system-info")
    public R<java.util.Map<String, Object>> systemInfo() {
        return R.ok(demoService.getSystemInfo());
    }
}
