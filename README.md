# RAG Platform - 分布式智能知识库问答平台

基于 RAG（检索增强生成）的分布式智能知识库问答平台。

## 核心亮点

| 特性 | 技术方案 | 目标指标 |
|---|---|---|
| **自研 RPC 框架** | Netty + Kryo，处理粘包/心跳/负载均衡 | - |
| **自研分布式中间件** | Redis + Lua 令牌桶限流 + 布隆过滤器 | - |
| **JVM 内 AI 推理** | DJL + ONNX Embedding 模型，避免跨语言调用 | 单机 QPS 800+ |
| **高并发优化** | JDK 21 虚拟线程 + NIO 零拷贝文档解析 | 千份文档耗时降低 40% |
| **检索优化** | 手写 LRU 缓存 + 混合排序（向量 + BM25 + RRF） | 检索准确率提升 15% |
| **用户记忆层** | 异步事实提取 + Redis/Milvus 双存储 + 多因素评分 | 个性化推荐 |

## 技术栈

- **JDK 21+**（虚拟线程）
- **Spring Boot 3.2**
- **Netty 4.1 + Kryo 5.6**（自研 RPC）
- **Redis 7 + Lua**（限流/缓存）
- **Milvus 2.4**（向量数据库）
- **MySQL 8**（元数据）
- **Nacos**（注册中心）
- **LangChain4j**（LLM 编排）
- **DJL + ONNX Runtime**（JVM 内推理）
- **Apache Tika**（文档解析）

## 模块结构

```
rag-platform/
├── rag-common/          # 公共模块：响应封装、异常处理
├── rag-web/             # Web启动模块：Spring Boot主程序（虚拟线程）
├── rag-engine/          # RAG编排引擎：检索增强生成
├── rag-parser/          # 文档解析：Tika + 虚拟线程 + NIO
├── rag-search/          # 检索核心：Milvus + LRU缓存 + 混合排序
├── rag-embedding/       # 向量化：DJL + ONNX JVM内推理
└── (Phase 1+)
    ├── rag-rpc/         # 自研RPC框架
    ├── rag-middleware/  # 自研限流+布隆过滤器
    └── rag-memory/      # 用户记忆层
```

## 快速开始

### 环境要求

- JDK 21+（当前：JDK 24）
- Maven 3.9+（当前：3.9.16，已配置阿里云镜像）
- Docker Desktop（WSL2 后端）
- Git

### 启动中间件（Day 2）

```bash
docker compose up -d
```

### 编译运行

```bash
# 编译
mvn clean install

# 启动
cd rag-web
mvn spring-boot:run

# 健康检查
curl http://localhost:8080/api/health
```

### 虚拟线程验证

访问 `http://localhost:8080/api/virtual-thread-test` 验证虚拟线程能力。

## 开发路线图

- [x] **Phase 0**：MVP 链路打通（当前阶段）
- [ ] **Phase 1**：自研 RPC 框架（Netty + Kryo）
- [ ] **Phase 2**：自研分布式中间件（限流 + 布隆过滤器）
- [ ] **Phase 3**：DJL + ONNX 向量化推理
- [ ] **Phase 4**：文档解析性能优化（虚拟线程 + NIO）
- [ ] **Phase 5**：检索优化（LRU 缓存 + 混合排序）
- [ ] **Phase 6**：集成 + 前端 + 部署
- [ ] **Phase 7**：用户记忆层（个性化）

## License

Apache License 2.0