# 终局架构：从 Pipeline RAG 到 Agentic RAG

> 本文档是**架构目标态（Target Architecture）设计**，不是当前实现说明。
> 当前代码处于 Phase 1（RPC 已完成）/ Phase 2~8 未开工状态。
> 作用：把"城市总体规划图"先挂到墙上——后续每个 Phase 开发时对照本文档检查接口方向，
> 保证自底向上的施工顺序不偏离 Agentic 终局，任何已建组件都不被推倒重来。
>
> 约束：本文档只定义边界、数据结构契约与演进步骤；具体实现到对应 Phase 才落地。

---

## 1. 为什么需要这份文档：两种控制流

当前规划（Phase 3~6）是**流水线式 RAG**，LLM 只在最后一步出场；
终局是 **Agentic RAG**，LLM 在循环中动态决策。两者的根本差异是**控制流在谁手里**。

| 维度 | Pipeline RAG（Phase 6 交付态） | Agentic RAG（Phase 9 目标态） |
|---|---|---|
| 流程决定者 | 程序员写死的固定步骤 | LLM 运行时动态决定下一步 |
| LLM 角色 | 末端"答题器" | 全程决策者（观察 → 思考 → 行动） |
| 检索的地位 | 硬编码的必经步骤 | 众多**工具之一**，模型可决定调/不调/调几次 |
| 失败处理 | 检索差则答案差，无补救 | 可纠错：换 query、换库、反问（CRAG/Self-RAG） |
| 一次请求的调用图 | 一条直线 | 带循环与分支的有向图（有护栏） |
| 多能力协作 | 无 | Planner 拆任务，多个专家 Agent 经 RPC 协作 |

【面试】为什么不一开始就做 Agent？
1) Agent 循环的每一环都依赖底层能力（检索、向量化、文档摄入）先存在且质量可量化，
   没有 RAG 基线（Recall@K、QPS）就无法证明"Agent 纠错带来了提升"；
2) Agent 每次问答 5~10 次 LLM/工具调用，成本与延迟高，简单问题走 Pipeline 更优——
   生产系统是**双通道并存**，不是 Agent 全面替代 Pipeline；
3) 先建地基再上决策层，是 AWS（EC2/S3 早于 Bedrock Agents）式的常规演进路径。

---

## 2. 终局架构图（模块连接图）

```
                            ┌──────────────────── rag-web (8080) ────────────────────┐
   HTTP/SSE ─────────────▶  │ 令牌桶限流 · 布隆过滤器 · Controller                      │
                            └────────────────────────────┬─────────────────────────────┘
                                                         │
                            ┌────────────────────────────▼─────────────────────────────┐
                            │                 rag-agent（Phase 9 新增模块）              │
                            │                                                            │
                            │   Router ──┬──▶ 简单问题 ──▶ Pipeline 快速通道(rag-engine)  │
                            │            │                                              │
                            │            └──▶ 复杂问题 ──▶ Agent 主循环（ReAct）         │
                            │                              │                            │
                            │              Planner(任务拆分) │ ToolRegistry(工具注册表)   │
                            │                       │      │  - knowledge_search        │
                            │                       ▼      │  - doc_status / kb_list    │
                            │              ┌────────────┐ │  - multimodal_search (P8)  │
                            │              │ 专家 Agent │ │  - memory_recall (P7)      │
                            │              │ Retriever  │ │  - calculator / web ...    │
                            │              │ Writer     │ └────────────┬───────────────┘
                            │              │ Critic(审) │              │ function-calling
                            │              └─────┬──────┘              ▼
                            └────────────────────┼───────────────────────────────────────┘
                                                 │  @RpcReference（自研 RPC，Agent 服务化后跨进程）
              ┌──────────────────┬───────────────┼────────────────┬─────────────────────┐
              ▼                  ▼               ▼                ▼                     ▼
        rag-parser         rag-embedding     rag-search       rag-engine            rag-memory
     (Tika/OCR/抽图)      (DJL+ONNX:       (Milvus 混合       Pipeline 快速通道     Persona/Working
      文档摄入工具         bge / CLIP双塔)   检索+RRF+Rerank    (检索→Prompt→LLM)     /Episodic
              │                  │               │                │                     │
              ▼                  ▼               ▼                ▼                     ▼
         MinIO 原始二进制      Milvus 向量库     Redis LRU        LLM API             Redis/MySQL
```

三条关键连线（读图要点）：

1. **Router 分流**：简单/高频问题走 `rag-engine` 的 Pipeline 通道（一次检索、一次生成，低延迟省 token）；
   复杂问题进 Agent 循环（多轮工具调用）。
2. **工具化方向（向下的箭头）**：现有模块不是被重写，而是被 `ToolRegistry` **包一层 Tool 接口**后注册——
   `rag-search` → `knowledge_search`；`rag-parser` → 文档状态/触发解析工具；
   `rag-memory` → `memory_recall`；Phase 8 的多模态检索 → `multimodal_search`。
3. **Agent 服务化（横向 RPC）**：Planner/Retriever/Writer/Critic 拆成独立 Agent 后，
   它们之间的通信直接走 **rpc-core**——多智能体协作不需要引入第二个通信框架。

Mermaid 版本（可在支持 Mermaid 的 Markdown 查看器中渲染）：

```mermaid
flowchart TD
    WEB[rag-web<br/>限流/布隆/Controller] --> AGENT

    subgraph AGENTBOX[rag-agent · Phase 9 新增]
        ROUTER[Router 意图路由]
        PIPE[Pipeline 快速通道<br/>rag-engine]
        LOOP[Agent 主循环 ReAct<br/>思考→工具→观察]
        PLANNER[Planner 任务拆分]
        REG[ToolRegistry 工具注册表]
        EXPERTS[专家 Agent<br/>Retriever / Writer / Critic]
        ROUTER -->|简单问题| PIPE
        ROUTER -->|复杂问题| LOOP
        LOOP --> PLANNER
        LOOP --> REG
        PLANNER --> EXPERTS
    end

    AGENT -->|@RpcReference| RPC(rpc-core<br/>Netty+Kryo+Nacos)

    RPC --> PARSER[rag-parser<br/>摄入工具]
    RPC --> EMB[rag-embedding<br/>DJL+ONNX]
    RPC --> SEARCH[rag-search<br/>knowledge_search 工具]
    RPC --> MEM[rag-memory<br/>memory_recall 工具]
    PARSER --> MINIO[(MinIO)]
    EMB --> MILVUS[(Milvus)]
    SEARCH --> MILVUS
    SEARCH --> REDIS[(Redis LRU)]
    PIPE --> LLM[(LLM API)]
    LOOP --> LLM
    MEM --> REDIS
```

---

## 3. 现有地基 → Agent 终局的映射（零废弃验证）

| 现有/规划组件 | Phase 9 中的角色 | 改造方式 | 是否重写 |
|---|---|---|---|
| rpc-core | 专家 Agent 之间的通信底座 | 直接复用 | 否 |
| rag-gateway 令牌桶 | Agent 循环扇出调用的限流（更刚需：一次问答 5~10 次下游调用） | 直接复用 | 否 |
| 布隆过滤器 | 工具调用前判空（知识库/文档不存在则不发起检索） | 直接复用 | 否 |
| rag-parser + MinIO 通道 | 文档摄入/状态查询工具 | 套 Tool 接口 | 否 |
| rag-embedding（bge/CLIP） | Agent 的"感知层"，query/图片向量化 | 直接复用 | 否 |
| rag-search（混合检索+RRF+Rerank） | 第一个工具 `knowledge_search` / `multimodal_search` | 套 Tool 接口 | 否 |
| LRU 缓存 | 工具结果缓存（key 含工具名+参数哈希） | 扩展 key 结构 | 极小 |
| rag-memory 三接口 | Agent 记忆系统 + `memory_recall` 工具 | 实现按 Phase 7 计划 | 否 |
| rag-engine Pipeline | 降级为 Router 下的**快速通道**，并实现统一 Capability 契约 | 加一层接口 | 变薄，不废 |
| rag-web | Agent 对话/SSE 出口，展示工具调用过程（"思考链可见"） | 加 DTO 字段 | 否 |

结论：九个现有模块全部在终局图中有明确位置，没有沉没组件。

---

## 4. 新增模块 rag-agent 的内部结构（Phase 9 才实现）

包结构预留设想（本文档不创建代码）：

```
com.landray.rag.agent
├── tool/
│   ├── Tool.java              # 工具契约：name / description / JSON Schema 参数 / execute()
│   ├── ToolRegistry.java      # 工具注册与发现（Agent 据此生成 function-calling schema）
│   ├── ToolResult.java        # 统一结果：success/data/error/可重试标记
│   └── builtin/               # KnowledgeSearchTool / MemoryRecallTool ...（对现有模块的包装）
├── loop/
│   ├── AgentLoop.java         # 主循环：thought → action(call tool) → observation → 再思考
│   └── GuardRail.java         # 护栏：max-iterations / 循环检测 / 输出 schema 校验 / 预算(token)
├── plan/
│   ├── Planner.java           # 复杂问题拆子任务
│   └── TaskDAG.java           # 子任务依赖图（可并行的扇出走虚拟线程）
├── multi/
│   ├── Orchestrator.java      # 多智能体调度（对应 Dubbo 体系里的服务编排）
│   └── agents/                # RetrieverAgent / WriterAgent / CriticAgent
└── route/
    └── QueryRouter.java       # 双通道分流：Pipeline vs Agent；闲聊直接回复
```

核心数据结构契约（跨语言可讨论、Java 落地时的字段目标）：

- `Tool`：`{ name, description, parametersJsonSchema }` + `execute(ToolArgs) -> ToolResult`
- `AgentStep`：`{ iteration, thought, toolName, toolArgs, observation, tokenCost }`
- `AgentTrace`：一次请求的完整步骤链——**必须可观测**，SSE 推给前端 + 落日志，
  这是 Agent 系统区别于黑盒 Pipeline 的调试生命线（【面试】高频：Agent 出了错怎么排查？）。

---

## 5. 现在（Phase 2~6）必须遵守的两条接口约束

为让 Phase 9 零重构接入，后续开发只需守住两条规则，不增加任何当期工作量：

1. **统一可调用契约**：Phase 6 的 RagEngine 对外只暴露标准化输入输出
   （`ChatRequest -> ChatResponse`，已是现状），内部不散落 HTTP/SDK 特有类型；
   未来它与每个 Tool 对 Agent 而言是同构的 Capability。
2. **依赖只到接口、不到实现**：engine/web 永远依赖 `VectorStore`、`EmbeddingService`、
   `HybridSearch`、`MemoryFacade` 这些接口（项目开局就是这么拆的，继续保持），
   不允许直接 new Milvus client / DJL Predictor 到业务层——
   Tool 包装层只会注入接口，接口稳定则包装层永远是薄的。

反模式（Phase 9 会返工的信号）：engine 里写死 retrieve→generate 的步骤且不可旁路；
业务层直接依赖中间件客户端；检索结果 DTO 与 Milvus 响应结构耦合。

---

## 6. Phase 9 演进步骤（文本主链路验收 + Phase 7 之后启动）

| 子阶段 | 内容 | 验收/量化指标 |
|---|---|---|
| **9.1 工具化 + 单 Agent** | Tool/ToolRegistry；把 rag-search 包成 `knowledge_search`，再加 2~3 个简单工具；实现 ReAct 主循环 + 护栏 + AgentTrace | 同一批问题，Agent 通道与 Pipeline 通道答案一致率；工具调用准确率（选对工具+参数） |
| **9.2 CRAG 纠错式 RAG** | Agent 评估检索质量，不相关则 query 改写重检/换库/反问；Router 双通道上线 | 标注集上困难问题准确率相对 Pipeline 基线的提升（必须有 Phase 5/6 的基线才能算） |
| **9.3 多智能体协作** | Planner 拆任务；Retriever/Writer/Critic 独立为 Agent 服务，经 **rpc-core** 互调；Critic 打回重写闭环 | 复杂多跳问题准确率；单步 Agent 的 QPS/延迟（复用 RPC 长连接与限流数据） |
| **9.4（可选）多智能体 + 多模态合流** | Phase 8 的 `multimodal_search` 作为工具接入；Agent 自主决定图文检索比例 | 图文混合问题可回答率 |

顺序原则：**9.1 是"有没有 Agent"的分界线，9.2 是"有没有比 RAG 强"的证据线，
9.3 是"多智能体协作 + 自研 RPC"的差异化亮点线。** 简历上在 9.1 完成前不写"多智能体"。

---

## 7. 面试自查清单（对照检查点）

- [ ] 能讲清 Pipeline RAG 与 Agentic RAG 的控制流差异，而非罗列名词
- [ ] 能解释为什么生产环境要保留 Pipeline 快速通道（成本/延迟/确定性）
- [ ] 能画出 ToolRegistry 如何把现有检索服务包成工具（适配器模式）
- [ ] Agent 主循环的死循环/超预算怎么防（护栏四件套：轮次/循环检测/schema/预算）
- [ ] Agent 出错如何排查（AgentTrace 全链路步骤可见）
- [ ] 多智能体之间怎么通信、怎么发现彼此（本项目答：自研 RPC + Nacos，而非 LangGraph）
- [ ] 为什么 RagEngine 不废弃（Router 双通道）
- [ ] CRAG 相对固定流水线，准确率提升用什么数据证明（标注集 + Phase 5/6 基线）

---

## 8. 与现有文档的关系

- [phase1-rpc-interview.md](phase1-rpc-interview.md)：rpc-core 自身设计与面试点（多智能体通信的底座细节在那里）
- 本文档：目标态架构，Phase 9 立项时以本文档第 4、5、6 节为直接输入
- README 路线图：Phase 9 的简版条目与本文档对应
