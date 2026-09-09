# Phase 1 · 自研 RPC 框架 —— 面试问题标注

> 本文档配合 `rpc-core` 模块源码阅读。每个问题都标注了【面试频率】和**代码位置**，
> 答案全部对应本项目的真实实现（不是背的通用答案），面试官追问时可以直接打开代码讲。

---

## 0. 一句话总览

消费端面向**接口**编程，`@RpcReference` 注入的是一个 **JDK 动态代理**对象；
调用方法时代理把调用信息（接口名/方法名/参数类型/参数）封装成请求，经 **Kryo 序列化**、
通过 **Netty** 长连接发给服务端；服务端按**自定义二进制协议**解码、**反射**调用本地实现，
结果原路返回。服务地址通过 **Nacos** 注册发现，客户端用**负载均衡**选节点、**CompletableFuture**
把异步网络回调转成同步调用语义，业务跑在 **JDK 21 虚拟线程**上。

### 一次 RPC 调用的完整链路（务必能默画）

```
Controller 调 demoService.sayHello("x")
   │  demoService 是 JDK 动态代理 $Proxy0
   ▼
RpcInvocationHandler.invoke()
   │  组装 RpcRequest{requestId, interfaceName, methodName, paramTypes, args}
   ▼
NettyRpcClient.sendRequest()
   │  ① registry.discover() 从 Nacos 拉节点列表（本地缓存）
   │  ② loadBalancer.select() 选一个节点（轮询/加权随机）
   │  ③ getOrCreateChannel() 取/建 TCP 长连接（连接池复用）
   │  ④ Kryo 序列化 → RpcProtocol 帧 → writeAndFlush
   ▼
┌─────────────── TCP 长连接（多路复用，靠 requestId 配对）───────────────┐
   客户端 IdleStateHandler 25s 写空闲发 PING  ←──心跳──→ 服务端 90s 读空闲关连接
└──────────────────────────────────────────────────────────────────────┘
   ▼
NettyRpcServer pipeline:
   RpcProtocolDecoder (LengthFieldBasedFrameDecoder 切帧 + 魔数/版本校验)
   → ServerHeartbeatHandler (读空闲关连接)
   → RpcRequestHandler  (PING 回 PONG；REQUEST 丢到【虚拟线程】)
        │  Kryo 反序列化 → 查服务表 → 反射 method.invoke(impl, args)
        ▼
   RpcResponse 经 RpcProtocolEncoder 写回
   ▼
客户端 RpcResponseHandler：按 requestId 找到 pending 的 CompletableFuture → complete
   ▼
代理线程 future.get(timeout) 被唤醒 → 拿到结果返回（异常则抛 RpcRemoteException）
```

---

## 一、网络与协议

### Q1.【面试·高频】什么是粘包/拆包？你怎么解决的？

**现象**：TCP 是面向字节流的，没有消息边界。
- 粘包：多个小请求被 Nagle/缓冲合并成一个 TCP 段发出，接收方一次读到多条消息；
- 拆包：一个大消息被分成多个 TCP 段到达，接收方一次读不全。

**解法**：在协议头里放一个**长度字段**，用 Netty 的 `LengthFieldBasedFrameDecoder` 按长度切帧。

代码位置：[RpcProtocolDecoder.java](../rpc-core/src/main/java/com/landray/rag/rpc/codec/RpcProtocolDecoder.java)

五个构造参数（面试常让逐个解释）：

| 参数 | 值 | 含义 |
|---|---|---|
| maxFrameLength | 16MB | 单帧上限，**防御恶意超长帧导致 OOM** |
| lengthFieldOffset | 13 | 长度字段在协议头中的偏移（跳过 MAGIC2+VER1+SER1+TYPE1+REQID8） |
| lengthFieldLength | 4 | 长度字段自身占 4 字节（int） |
| lengthAdjustment | 0 | 长度字段的值 = 其后 body 的字节数（不含长度字段本身） |
| initialBytesToStrip | 0 | 不跳过协议头，后续 handler 还要读 type/requestId |

> 追问：除了长度字段还有哪些拆包方案？
> 定长消息（FixedLengthFrameDecoder）、分隔符（DelimiterBasedFrameDecoder）。
> RPC 二进制协议通用做法是「固定头 + 长度字段 + 体」，本项目即此方案。

### Q2.【面试】为什么要自定义协议？协议头里各字段的作用？

代码位置：[RpcProtocol.java](../rpc-core/src/main/java/com/landray/rag/rpc/message/RpcProtocol.java)

```
┌───────────┬─────────┬────────────┬──────────┬─────────────┬───────────┬────────┐
│ Magic (2) │ Ver (1) │ Serializer │ Type (1) │ RequestId(8)│ BodyLen(4)│ Body   │
│ 0xCAFE    │ 1       │ 1=Kryo     │ 1请求/2响应│ long        │ int       │ 变长    │
│           │         │ (1)        │ /3PING/4PONG│           │           │        │
└───────────┴─────────┴────────────┴──────────┴─────────────┴───────────┴────────┘
```

- **魔数 Magic**：类似 class 文件的 `0xCAFEBABE`，第一时间识别非法流量/错误端口探测，直接拒绝；
- **版本号 Ver**：协议升级兼容（老客户端/新服务端协商）；
- **序列化器 Serializer**：一个字节标识序列化方式，将来可切换 Protobuf/JSON 而协议不变；
- **消息类型 Type**：区分请求/响应/心跳，心跳复用同一条连接，不用额外开端口；
- **RequestId**：多路复用的关键（见 Q8）；
- **BodyLen**：解决粘包拆包。

> 追问：为什么魔数用 2 字节而不是 4 字节？省头部字节；0xCAFE 向 Java 的 0xCAFEBABE 致敬。

### Q3.【面试·踩坑真题】魔数 0xCAFE 校验为什么一开始总是失败？

> 这是本项目联调时**真实踩到的坑**，讲出来非常加分（说明真的写过 Netty）。

`byteBuf.readShort()` 返回的是**有符号 short**。`0xCAFE = 51966 > Short.MAX_VALUE(0x7FFF=32767)`，
读出来是负数 `-13570`。拿它和 `int` 常量 `0xCAFE(51966)` 比较时，short 自动**符号扩展**为
`0xFFFFCAFE(-13570)`，永远不等于 `0x0000CAFE(51966)` —— 于是合法帧被误判为非法帧。

修复：读出来后 `& 0xFFFF` 抹平符号位（或用 `readUnsignedShort()`）：

```java
int magic = frame.readShort() & 0xFFFF;   // 正确
if (magic != RpcProtocol.MAGIC) { ... }
```

**教训**：Java 的 byte/short 都是有符号的，处理二进制协议时凡涉及 `0x80~0xFF` 的字节，
一律 `& 0xFF` / `& 0xFFFF` 转成无符号 int 再比较。

---

## 二、序列化

### Q4.【面试·高频】Kryo 线程安全吗？你是怎么解决的？

**Kryo 对象本身不是线程安全的**——内部持有引用解析表、类缓存等可变状态，多线程共用一个实例会串数据。

代码位置：[KryoSerializer.java](../rpc-core/src/main/java/com/landray/rag/rpc/serialize/KryoSerializer.java)

三种解法对比：

| 方案 | 优点 | 缺点 | 本项目 |
|---|---|---|---|
| **ThreadLocal\<Kryo\>** | 无锁、实现简单、线程天然隔离 | 线程数多时实例数多（Kryo 很轻，可接受） | ✅ 采用 |
| Kryo 自带 Pool\<Kryo\> 对象池 | 可控实例上限、obtain/free 复用 | 要手动归还，略繁琐 | 备选 |
| synchronized 方法 | 最简单 | 串行化，高并发吞吐崩塌 | ❌ |

> 追问：为什么 Kryo 比 JDK 序列化快/小？
> JDK 序列化把全限定类名、字段名等元信息全写进去，还要求实现 Serializable；
> Kryo 对类结构做缓存、用变长编码、可注册类后只写 int 编号，体积小速度快。

### Q5.【面试·加分】序列化方案选型？为什么不用 Protobuf？

- **Kryo**：快、体积小、零 schema 配置；缺点是**不跨语言**、类结构演进兼容性弱——适合**纯 Java 内部 RPC**；
- **Protobuf**：跨语言、字段编号天然支持向后兼容；但要写 .proto 文件、有代码生成成本——适合跨语言/对外；
- **JSON**：可读、跨语言；但体积大、无类型信息、最慢。

本项目是 JVM 内部服务间调用，选 Kryo 性价比最高；协议头里保留了 serializer 字节，
将来 embedding/search 等若需跨语言可平滑切 Protobuf。

> 安全点：代码里 `setRegistrationRequired(false)` 方便开发，生产建议 `true` + 预注册，
> 既减小消息体（写编号而非类名），又防止反序列化**任意类**的攻击面（对应 Fastjson 反序列化漏洞那一类问题）。

---

## 三、Netty 线程模型

### Q6.【面试·高频】讲一下 Netty 的 Reactor 线程模型？

代码位置：[NettyRpcServer.java](../rpc-core/src/main/java/com/landray/rag/rpc/server/NettyRpcServer.java)

主从 Reactor 多线程模型：
- **bossGroup（1 个线程）**：只负责 `accept` 新连接，拿到连接后注册给 worker；
- **workerGroup（默认 2×CPU 核数）**：每个 EventLoop 绑定若干 Channel，负责这些 Channel 的**全部读写事件**；
- 一个 Channel 在整个生命周期里只属于一个 EventLoop 线程，所以 handler 回调天然无并发（单线程串行）。

> `SO_BACKLOG=1024`：内核**全连接队列**长度，突发建连时暂存已完成三次握手但还没被 accept 的连接；
> `TCP_NODELAY=true`：关闭 Nagle 算法，小包立即发，降低 RPC 延迟（RPC 多为小请求，低延迟优先）。

### Q7.【面试·高频】业务逻辑为什么不能在 EventLoop 里执行？你怎么做的？

EventLoop 线程负责它名下**所有 Channel** 的读写。如果在里面跑慢业务（一次 DB 查询 100ms、一次向量检索），
这 100ms 内同线程上**其他所有连接**的读写都被卡住 → 整条 Reactor 吞吐雪崩。

三种隔离方案：
1. 传统业务线程池（ThreadPoolExecutor）；
2. 独立业务 EventLoopGroup 隔离；
3. **JDK 21 虚拟线程**（本项目采用）。

代码位置：[RpcRequestHandler.java](../rpc-core/src/main/java/com/landray/rag/rpc/handler/RpcRequestHandler.java)

```java
private static final ExecutorService VIRTUAL_EXECUTOR =
    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("rpc-worker-", 0).factory());

// 请求来了丢给虚拟线程，EventLoop 立刻返回去处理下一个 IO 事件
case REQUEST -> VIRTUAL_EXECUTOR.execute(() -> invokeService(ctx, protocol));
```

> 追问：虚拟线程为什么适合这里？
> 反射调用/JDBC/HTTP 都是**阻塞**操作。平台线程阻塞时会占住 OS 线程（昂贵，几千个就到顶）；
> 虚拟线程在阻塞时会 **unmount** 让出载体线程，载体线程能去跑别的虚拟线程，
> 于是可以"每个请求一个线程"地写同步代码，却有接近异步的吞吐，还**免去线程池大小调优**。

### Q8.【面试】@Sharable 注解是干什么的？不加会怎样？

> 本项目真实踩坑：第二个连接进来时直接抛
> `ChannelPipelineException: RpcRequestHandler is not a @Sharable handler`。

Netty 默认把每个 ChannelHandler 当作**有状态**的，禁止同一个 handler 实例被加入多个 Channel 的
pipeline（防止你在 handler 里放了非线程安全的成员变量被并发踩坏）。

本项目的 `RpcRequestHandler` 是**单例**，要被每条新连接复用，必须加 `@ChannelHandler.Sharable`，
这相当于向 Netty **承诺该 handler 线程安全**。我们能承诺的依据：
- `services` 服务表是 `ConcurrentHashMap`，且启动后只读；
- `serializer`（KryoSerializer）内部是 ThreadLocal 隔离；
- 业务执行派发到虚拟线程，handler 自身不持有请求级可变状态。

> 对比：pipeline 里的 Decoder/Encoder/IdleStateHandler/心跳 handler 都是 `new` 出来的新实例
> （每次连接独立），所以它们不需要 @Sharable。

---

## 四、心跳与连接管理

### Q9.【面试·高频】心跳机制解决什么问题？TCP 不是有 keepalive 吗？

TCP keepalive 检测的是**连接是否存活**（对端主机/网络是否可达），但检测不了**应用假死**：
进程还在、TCP 连接没断，但应用卡住了（Full GC 长时间 STW、死循环、线程池打满），
或者对端**异常宕机没来得及发 FIN**（半打开连接 half-open），本端以为连接还好好的。

应用层心跳靠**读写超时 + 主动探测**解决。

代码位置：[ServerHeartbeatHandler.java](../rpc-core/src/main/java/com/landray/rag/rpc/handler/ServerHeartbeatHandler.java)、
[RpcResponseHandler.java](../rpc-core/src/main/java/com/landray/rag/rpc/client/RpcResponseHandler.java)

本项目策略（与 Dubbo 同方向）：
- **客户端**：`IdleStateHandler(0, 25, 0)` —— 25s 没有出站数据就发一个 PING，顺便探测链路；
- **服务端**：`IdleStateHandler(90, 0, 0)` —— 90s 没读到任何数据（≈3 个客户端心跳周期都没动静），
  判定客户端假死/半开，**主动 close** 释放连接资源。

> 追问：为什么服务端读空闲要主动关，而不是等 PING？
> 快速清理僵尸连接，防止 fd/内存泄漏；半开连接留在服务端没有任何价值。
>
> 追问：心跳为什么复用业务连接而不是单独端口/连接？省连接资源，且心跳本身就该走真实链路才探测得准。

### Q10.【面试】客户端连接是怎么管理的？

代码位置：[NettyRpcClient.java](../rpc-core/src/main/java/com/landray/rag/rpc/client/NettyRpcClient.java)

- **连接复用**：`channelCache: host:port → Channel`，同一节点只建一条 TCP 连接，所有请求**多路复用**；
- 取连接时检查 `channel.isActive()`，失效则移除重连；
- 在 `channel.closeFuture()` 上挂监听，连接断开自动从缓存清除，下次调用透明重连。

---

## 五、异步转同步与请求配对

### Q11.【面试·高频】Netty 是异步的，为什么业务代码能像本地调用一样同步拿到结果？

经典 **sync-over-async** 模式，核心是 `CompletableFuture` + requestId 配对。

代码位置：[RpcResponseHandler.java](../rpc-core/src/main/java/com/landray/rag/rpc/client/RpcResponseHandler.java)

1. 发送前：以 `requestId` 为 key，把一个 `CompletableFuture` 放进 `pendingRequests` 表；
2. 调用线程：`future.get(timeout)` 阻塞等待（对使用者呈现同步语义）；
3. Netty IO 线程收到响应：按响应里的 requestId 从表里**取出对应 Future** 并 `complete(response)`；
4. 调用线程被唤醒，拿到结果。

> 关键：为什么一定要 requestId？
> 一条 TCP 连接上同时挂着很多个未返回的请求（多路复用），**响应到达顺序和请求发出顺序没有保证**
> （服务端各请求处理耗时不同）。没有 requestId 就无法知道这个响应属于哪个调用。
>
> 超时：`future.orTimeout(timeoutMs)`，超时后 Future 以 TimeoutException 完成并从 pending 表清理，避免内存泄漏。

### Q12.【面试】调用失败了怎么办？（容错/重试）

代码位置：`NettyRpcClient.sendRequest()`

- **失败切换（failover）**：选中的节点连接/发送失败，自动从列表剔除该节点，用负载均衡器选下一个重试，最多 3 次；
- 节点级失败会清掉该节点的坏连接，触发下次重连；
- 全部节点失败才抛异常。

> 可扩展（面试可主动说"后续会加"）：超时重试要保证**幂等**（只对查询类重试，写操作不自动重试）、
> 熔断（连续失败快速失败不再打过去）、限流降级。Dubbo 还有 cluster 层的 failfast/failsafe/forking 等策略。

---

## 六、动态代理

### Q13.【面试·高频】RPC 为什么用动态代理？JDK 代理和 CGLIB 区别？

代码位置：[RpcClientProxy.java](../rpc-core/src/main/java/com/landray/rag/rpc/proxy/RpcClientProxy.java)

消费端只持有接口 `DemoService`，容器里并没有它的实现类 Bean。代理在运行时生成一个
**"假实现" `$Proxy0`**，它实现了业务接口，每个方法内部都转调 `InvocationHandler.invoke()`，
在 invoke 里把方法调用翻译成网络请求。于是业务方 `demoService.sayHello(x)` **完全无感知**这是一次远程调用。

- **JDK 动态代理**：基于接口，运行时用 `ProxyGenerator` 拼字节码 → `ClassLoader.defineClass` 生成
  `$Proxy0`（继承 `Proxy`、实现目标接口）。**只能代理接口**；
- **CGLIB/ByteBuddy**：通过**生成目标类的子类**、重写方法来代理，能代理类（不能代理 final 类/方法）。

本项目服务都面向接口定义，用 JDK 代理足够。

> 细节：为什么 invoke 里要特判 `toString/hashCode/equals`？
> 代理对象也会被日志打印、放进集合，这些 Object 方法不该被当成 RPC 请求发出去，直接本地处理。

---

## 七、注册中心与负载均衡

### Q14.【面试】服务注册发现是怎么做的？Nacos 挂了怎么办？

代码位置：[NacosRegistry.java](../rpc-core/src/main/java/com/landray/rag/rpc/registry/NacosRegistry.java)

- 服务端启动时把 `ip:port + 服务名` 注册到 Nacos（**临时实例 ephemeral**，靠心跳续约，宕机自动摘除）；
- 客户端 `discover(服务名)` 拉取实例列表，Nacos 客户端**本地缓存 + 订阅推送**：
  服务列表变化时 Nacos 主动 push 更新。

> Nacos 挂了怎么办？客户端有本地缓存快照，短时间内仍能按缓存地址调用（AP 高可用思想）。
>
> 追问：Nacos 用 AP 还是 CP？
> Nacos 临时实例用 **Distro（AP）**——保证可用性、最终一致，服务发现场景优先可用；
> 持久实例/配置用 Raft（CP）。服务注册发现选 AP 是合理的（注册中心短暂不一致可接受，不能因为它不可用就调不通）。

### Q15.【面试】负载均衡算法？轮询怎么保证线程安全？

代码位置：[lb 包](../rpc-core/src/main/java/com/landray/rag/rpc/lb/)

- **轮询 RoundRobin**：`AtomicInteger` 计数 + 取模。用 **CAS 无锁自增**而非 synchronized，
  因为这是每次请求都走的热点路径。取模前 `& Integer.MAX_VALUE` 防负数
  （比 `Math.abs` 稳，因为 `Math.abs(Integer.MIN_VALUE)` 还是负数）。
- **加权随机 WeightedRandom**：把各节点权重铺成一条数轴，落在哪段就选谁。权重来自 Nacos 实例的
  weight 字段，可在控制台**动态调权做灰度**。

> 追问：加权随机有什么缺点？知道平滑加权轮询吗？
> 加权随机短时间内可能连续命中同一节点（突发不均）。
> **Nginx 平滑加权轮询（smooth WRR）**：每轮 `currentWeight += effectiveWeight`，选 currentWeight
> 最大的，再把它 `currentWeight -= totalWeight`，能让 A=5,B=1,C=1 这种比例均匀展开而不是连打 5 次 A。
> 后续可扩展；还有**一致性哈希**用于需要会话粘滞/缓存亲和的场景。

---

## 八、Spring 集成

### Q16.【面试】@RpcService / @RpcReference 是怎么生效的？

代码位置：[spring 包](../rpc-core/src/main/java/com/landray/rag/rpc/spring/)

- **服务提供方 `@RpcService(DemoService.class)`**：注解上**元标注了 `@Component`**，
  所以实现类先被 Spring 扫描成 Bean；`RpcServicePostProcessor`（BeanPostProcessor）在 Bean 初始化后
  把它登记进 RPC 服务表，并在启动后统一 `export` + 注册到 Nacos。
- **消费方 `@RpcReference`**：字段类型是业务接口，容器里**没有这个 Bean**，常规 @Autowired 注入不了。
  `RpcReferencePostProcessor` 在每个 Bean 初始化后遍历它的字段（含父类字段），
  发现 @RpcReference 就用 `RpcClientProxy.createProxy()` 生成代理，再**反射 set 进字段**。

> 追问：为什么用 BeanPostProcessor 而不是 BeanFactoryPostProcessor？
> BFPP 阶段 Bean 还没实例化，无法操作字段；BPP 的 afterInitialization 阶段 Bean 已创建好，正适合往字段里塞代理。
> （Dubbo 老版本用 ReferenceBean/FactoryBean 方案，思路类似。）
>
> 反射注入 private 字段性能：`setAccessible(true)` 后首次有开销，但这是**启动阶段一次性**的，可接受；
> 追求极致可用 MethodHandle/VarHandle。

---

## 九、本次联调真实踩坑复盘（面试讲故事素材）

> 面试官问"你做的时候遇到什么难点"，按下面三个真实 Bug 讲，比泛泛而谈有说服力。

| # | 现象 | 根因 | 修复 | 知识点 |
|---|---|---|---|---|
| 1 | 服务端日志报 `非法协议魔数: 0xcafe`，请求全超时 | `readShort()` 返回**有符号 short**，0xCAFE 读成负数，与 int 魔数比较时符号扩展永不相等 | `readShort() & 0xFFFF` 抹平符号位 | Java 有符号类型、二进制协议处理 |
| 2 | 第二个连接报 `not a @Sharable handler` | 单例 handler 被加入多个 pipeline，Netty 默认禁止有状态 handler 共享 | 加 `@ChannelHandler.Sharable`（并确认无状态） | Netty handler 生命周期与线程安全 |
| 3 | HTTP 接口一调就 500，报 `parameter name information not available` | Spring Boot 3 省略 `@RequestParam(name=...)` 时靠反射读参数名，需编译加 `-parameters` | 父 POM maven-compiler-plugin 开 `<parameters>true</parameters>` | Java 编译参数、反射参数名 |

---

## 十、和 Dubbo 的对比 / "为什么自研 RPC"

> 面试官大概率问："Dubbo 这么成熟为什么自己写？"
> 标准答法（诚实且有深度）：

**生产我会选 Dubbo**——它有完备的集群容错、服务治理、监控生态，自研重造这些不划算。
但这个项目的定位是**基础设施学习与性能掌控**：自研让我把 RPC 的每个核心机制都亲手实现并踩了坑
（协议编解码、粘包、心跳、异步转同步、动态代理、注册发现、负载均衡），这些正是面试和实际排障
最需要的底层功底；同时自研框架能和项目的**虚拟线程、Kryo、后续 DJL 推理**做深度定制，
没有历史包袱。可以理解为"用一个能跑的简化版 Dubbo，把 Dubbo 的核心原理全部吃透"。

本项目对应 Dubbo 的概念映射：

| 本项目 | Dubbo 对应 |
|---|---|
| RpcProtocol + Decoder/Encoder | Dubbo 协议 + Codec |
| KryoSerializer | Serialization（Kryo 是 Dubbo 支持的序列化之一） |
| NettyRpcServer/Client | Netty Transporter |
| RpcClientProxy（JDK Proxy） | Proxy + Invoker |
| NacosRegistry | Registry（Nacos 是 Dubbo 官方注册中心之一） |
| RoundRobin/WeightedRandom | LoadBalance（Dubbo 还缺 ConsistentHash/LeastActive） |
| failover 最多 3 次 | Cluster 的 FailoverClusterInvoker |
| 暂无（规划中） | 熔断、限流、过滤器链、泛化调用、泛化调用、线程池隔离 |

---

## 十一、可继续打磨的点（面试可主动提，体现规划能力）

1. **过滤器/拦截器链**：埋点、traceId 透传、鉴权（对应 Dubbo Filter）；
2. **熔断降级**：连续失败快速失败（Resilience4j 思路 / 自研滑动窗口计数）；
3. **一致性哈希 LB**：embedding 缓存亲和、会话粘滞；
4. **异步 RPC**：接口直接返回 `CompletableFuture`，全链路非阻塞（Dubbo 的 async 调用）；
5. **泛化调用**：消费端不依赖接口 jar 也能调（网关场景需要）；
6. **线程池隔离**：不同服务用不同业务线程池，防止慢服务拖垮全部；
7. **压测报告**：给出虚拟线程 vs 平台线程在不同并发下的 QPS/P99 对比（本项目主打性能，数据最有说服力）。

---

*Phase 1 交付物：`rpc-core` 模块（30 个类）+ `rag-web/demo` 全链路验证（HTTP → 动态代理 → Netty → Kryo → Nacos → 反射 → 虚拟线程 → 原路返回，已实测通过）。*
