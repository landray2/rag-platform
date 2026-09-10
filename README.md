# RAG Platform - 分布式智能知识库问答平台

基于 RAG（检索增强生成）的分布式智能知识库问答平台：自研底层基础设施、Java 全链路 AI 推理，并在文本主链路跑通后演进为支持图文跨模态检索的多模态 RAG。

## 核心亮点

| 特性 | 技术方案 | 目标指标 |
|---|---|---|
| **自研 RPC 框架** ✅ | Netty + Kryo，自定义协议解决粘包/心跳/负载均衡，JDK 21 虚拟线程承载业务调用 | 全链路已验证 |
| **自研分布式中间件** | Redis + Lua 令牌桶限流 + 布隆过滤器 | - |
| **JVM 内 AI 推理** | DJL + ONNX Embedding 模型，避免跨语言调用 | 单机 QPS 800+ |
| **高并发优化** | JDK 21 虚拟线程 + NIO 零拷贝文档解析 | 千份文档耗时降低 40% |
| **检索优化** | 手写 LRU 缓存 + 混合排序（向量 + BM25 + RRF + Rerank） | 检索准确率提升 15% |
| **用户记忆层** | 异步事实提取 + Redis/Milvus 双存储 + 多因素评分 | 个性化问答 |
| **多模态 RAG**（演进） | MinIO 存原始二进制 + CLIP/BGE-Visual 图文双塔（DJL 加载 ONNX），图文同向量空间互搜 | 跨模态 Recall@5 |

## 技术栈

- **JDK 21+**（当前 JDK 24 编译，目标 Java 21；虚拟线程）
- **Spring Boot 3.2**
- **Netty 4.1 + Kryo 5.6**（自研 RPC）
- **Redis 7 + Lua**（限流/缓存）、**Redisson**
- **Milvus 2.4**（向量数据库）
- **MySQL 8**（元数据）、**MinIO**（多模态原始二进制对象存储）
- **Nacos 2.3**（注册中心）、**RabbitMQ**（文档解析异步任务）
- **DJL + ONNX Runtime**（JVM 内推理：文本 Embedding / CLIP 图文双塔 / OCR）
- **Apache Tika**（文档解析）、**LangChain4j**（LLM 编排）

## 模块结构

```
rag-platform/
├── rag-common/     # 公共模块：统一响应、异常、领域模型（Document/DocumentChunk/ChatRequest）
├── rpc-core/       # 自研 RPC 框架：协议编解码/Kryo/Netty/动态代理/负载均衡/Nacos/Spring 集成
├── rag-gateway/    # 网关能力：Redis+Lua 令牌桶限流、布隆过滤器（Phase 2）
├── rag-parser/     # 文档解析：Tika + 虚拟线程 + NIO（Phase 4，多模态阶段扩展 OCR/抽图）
├── rag-embedding/  # 向量化：DJL+ONNX 文本 Embedding（Phase 3）→ CLIP 图文双塔（Phase 8）
├── rag-search/     # 检索：Milvus + LRU 缓存 + 混合排序 + Rerank（Phase 5/8）
├── rag-memory/     # 用户记忆层：Persona/Working/Episodic（接口已预留，Phase 7）
├── rag-engine/     # RAG 编排引擎：检索 → 组装 Prompt → LLM（Phase 6）
└── rag-web/        # Web 启动模块：Spring Boot 主程序、Controller、RPC Demo
```

## 架构概览

```
                         ┌─────────────── rag-web (8080) ───────────────┐
   HTTP/SSE ───────────▶ │ 限流(令牌桶) · 布隆过滤器 · Controller         │
                         └───────┬───────────────────────────┬───────────┘
                                 │ @RpcReference (JDK 动态代理)
                                 ▼                            │
                    ┌─────────── rpc-core (Netty:9999) ───────┴──────────┐
                    │ 自定义协议 · Kryo · 心跳 · failover · Nacos 注册发现 │
                    └───┬──────────┬──────────┬──────────┬───────────────┘
                        ▼          ▼          ▼          ▼
                  rag-parser  rag-embedding rag-search  rag-engine ──▶ LLM
                  (Tika/OCR)   (DJL+ONNX)   (Milvus)      │
                        │          │          │           ├─▶ rag-memory
                        ▼          ▼          ▼           ▼
              MinIO ◀── 原始文件/图片    Milvus 向量库   Redis/MySQL
```

多模态阶段（Phase 8）：图片/文件原始二进制存 MinIO，CLIP 双塔把图文映射到同一向量空间存入 Milvus 独立 collection，实现 text↔image 跨模态检索。

## 快速开始

### 环境要求

- JDK 21+（当前：JDK 24）
- Maven 3.9+（当前：3.9.16，已配置阿里云镜像）
- Docker Desktop（WSL2 后端）
- Git

### 启动中间件

```bash
docker compose up -d
# 7 个容器：redis:6379 | mysql:3307 | milvus:19530 | minio:9000/9001
#          etcd:2379 | nacos:8848 | rabbitmq:5672/15672
```

### 编译运行

```bash
# 编译全部模块
mvn clean package -DskipTests

# 启动
java -jar rag-web/target/rag-web-1.0.0-SNAPSHOT.jar

# 健康检查
curl http://localhost:8080/actuator/health
```

### RPC 全链路验证（Phase 1 已交付）

```bash
# HTTP → 动态代理 → Netty → Kryo → Nacos 发现 → 服务端反射 → 虚拟线程执行 → 原路返回
curl "http://localhost:8080/api/rpc/hello?name=test"
curl "http://localhost:8080/api/rpc/system-info"
```

## 开发路线图

### 基础设施与文本 RAG 主链路

- [x] **Phase 0**：项目骨架 + 中间件栈（9 模块 Maven 工程、docker-compose 7 容器）
- [x] **Phase 1**：自研 RPC 框架（Netty + Kryo + Nacos），全链路实测通过
  - 自定义二进制协议（魔数/版本/requestId/长度字段），`LengthFieldBasedFrameDecoder` 解决粘包拆包
  - Kryo ThreadLocal 隔离、CompletableFuture 异步转同步、长连接多路复用
  - 轮询/加权随机负载均衡、failover、`@RpcService`/`@RpcReference` Spring 集成
  - 详见 [docs/phase1-rpc-interview.md](docs/phase1-rpc-interview.md)（面试题标注 + 踩坑复盘）
- [ ] **Phase 2**：自研分布式中间件（rag-gateway）
  - Redis + Lua 原子令牌桶限流（`@RateLimit` 注解 + AOP，注解已预留）
  - 手写布隆过滤器（Redis bitmap 存储，判空拦截穿透）
  - 限流降级响应、网关层压测
- [ ] **Phase 3**：DJL + ONNX 文本向量化（rag-embedding）
  - DJL 加载 bge 中文 Embedding ONNX，JVM 内推理，先远程 API 后本地模型灰度切换
  - 批量推理、Tokenizer、向量归一化；QPS/延迟基线测试（目标 800+ QPS）
- [ ] **Phase 4**：文档解析（rag-parser）
  - Tika 解析 PDF/Word/MD/TXT，虚拟线程并发 + NIO 零拷贝
  - 分块策略（定长/语义/重叠），RabbitMQ 异步解析任务 + 状态机
  - 千份文档吞吐对比（目标耗时降低 40%）
- [ ] **Phase 5**：检索优化（rag-search）
  - Milvus 向量检索 + BM25 稀疏检索 + RRF 融合 + Cross-Encoder Rerank
  - 手写 LRU 缓存、查询改写；标注集评估 Recall@K/准确率（目标提升 15%）
- [ ] **Phase 6**：RAG 编排引擎与部署（rag-engine / rag-web）
  - Pipeline 编排（接口已预留）、Prompt 组装、SSE 流式输出、引用溯源
  - 端到端联调、Docker 镜像化部署、全链路压测 —— **文本主链路验收线**
- [ ] **Phase 7**：用户记忆层（rag-memory，接口已预留）
  - Persona（画像）/ Working（会话上下文）/ Episodic（情景记忆）+ MemoryFacade
  - 异步事实提取、Redis/Milvus 双存储、记忆衰减与多因素评分

### Phase 8：多模态 RAG（文本主链路跑通后启动）

> 原则：先扩数据通道、后接模型；每一步都有标注集评估，与纯文本基线对比。

- [~] **Phase 8.1（M1）统一内容模型**（纯重构，无 AI 模型）
  - ✅ 字段预留已提前落地（Phase 1 收尾）：`Modality` 枚举（text/image/audio），`DocumentChunk` 增加 `modality`(默认 text) / `blobRef` / `caption`，`Document` 增加 `blobRef`
  - [ ] 接入 MinIO SDK：原始文件/图片二进制对象存储（key 规范 `{kbId}/{docId}/{assetId}`）
  - [ ] MySQL、Milvus schema 增加 modality 列（建表/建 collection 时一次性带上）
  - 验收：文本链路行为逐字节不变（全部 modality=text），回归测试全绿
- [ ] **Phase 8.2（M2）图片摄入 + OCR**
  - 图片型 PDF/扫描件渲染抽图，DJL 加载 OCR ONNX（保持 Java 全链路）
  - 图片落 MinIO、OCR 文本入 chunk（caption），RabbitMQ 异步消费
- [ ] **Phase 8.3（M3）CLIP 图文双塔**（多模态技术核心）
  - DJL 加载 Chinese-CLIP / BGE-Visual ONNX：`embedText` / `embedImage` 双塔，L2 归一化
  - Milvus 独立 `chunk_v2` collection（向量空间与文本模型隔离，严禁混算）
  - 图文对标注集评估 text→image / image→text Recall@5，图像预处理对齐训练参数
- [ ] **Phase 8.4（M4）多模态混合检索**
  - 向量（CLIP）+ BM25（caption）+ RRF 融合，按 modality 分数归一化
  - Rerank 图片走 caption 兜底，SearchResult 返回预签名图片 URL
- [ ] **Phase 8.5（M5）查询侧多模态 + VLM 应答**
  - `ChatRequest` 支持图片引用（传 blobRef，不传大 base64）
  - 图片问题 → 视觉塔检索 + VLM（Qwen-VL 类）理解 → 生成带图片引用的回答
- [ ] **Phase 8.6（M6）固化与性能**
  - 批量图像推理 + 预处理并行（虚拟线程）、图像 embedding 独立限流
  - MinIO/Milvus/MySQL 三存储一致删除与补偿；图文混合 QPS/P99 压测

## 文档

- [docs/phase1-rpc-interview.md](docs/phase1-rpc-interview.md)：Phase 1 RPC 面试问题标注（16 题 + 踩坑复盘 + Dubbo 对比）

## License

Apache License 2.0
