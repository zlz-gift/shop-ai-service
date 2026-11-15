# shop-ai-service · 电商售后智能客服

把电商主站的售后环节做成智能体：在一个对话里完成 **查订单 → 依据政策条款回答问题 → 提交退款并落库回传工单号**。

重点不在「接了一个大模型」，而在三个工程问题：

1. **工具调用的正确性**——模型可能传错参数、可能重复调用，不能因此写出脏数据；
2. **上下文规模可控**——对话轮数上去以后，prompt 不能线性膨胀；
3. **云端不可用时不中断**——模型网关抖动、超时、限流时服务仍要能答。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / 框架 | Java 21、Spring Boot 3.5.11 |
| 模型接入 | Spring AI 1.1.7：本地 Ollama（`deepseek-r1:1.5b`）+ 云端 Qwen（OpenAI 兼容网关，`qwen3.8-flash` 对话 / `qwen3.7-text-embedding` 1024 维） |
| 业务数据 | MySQL（与电商主站共用 `java_shop` 库）、MyBatis-Plus 3.5.10 |
| 交互形态 | `ChatClient.stream()` + Flux，SSE 逐 Token 返回 |
| 知识库 | `PagePdfDocumentReader` + `TokenTextSplitter` + `SimpleVectorStore` |

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/ai/service` | 售后客服：云端模型 + 三个业务工具，流式 |
| POST | `/ai/chat` | 通用对话：本地 Ollama，无工具，流式 |
| POST | `/ai/pdf/upload` | 上传政策 PDF 入库，按 `userId` 隔离 |
| POST | `/ai/pdf/chat` | 政策知识库问答（RAG），流式 |
| GET | `/ai/history/{type}` | 某业务下的会话列表（`chat` / `service` / `pdf`） |
| GET | `/ai/history/{type}/{chatId}` | 某会话的历史消息 |

## 三个核心设计

### 1. 工具调用的正确性

`ShopTools` 用 `@Tool` + `@ToolParam` 定义了三个工具，入参描述里写清字段含义、示例和边界（例如「订单号，纯数字，例如 1001」），返回统一收敛为 `String`：成功给 JSON，失败给自然语言原因，工具内部异常不会抛进对话链路。

退款这条链路做了三层保护（`AfterSaleServiceImpl`）：

- **状态机**：只放行「已下单 / 已发货」，「已完成 / 已取消」直接拒绝，并把原因回给模型；
- **幂等**：`refund_apply.order_id` 唯一索引 + `INSERT IGNORE`，靠影响行数判断是否重复，而不是「先查后插」——后者在并发下两个请求都会查不到；
- **不用异常做分支**：捕获 `DuplicateKeyException` 会把事务标记成 rollback-only，后续正常返回也会在提交时抛 `UnexpectedRollbackException`。

订单详情用一条 `order_items JOIN products` 取回全部明细，避免按明细逐条查商品的 N+1。

### 2. RAG 知识库链路

`PagePdfDocumentReader` 解析 → 按中文标点切分（**300 字/片**、最少 80 字）→ 1024 维向量化 → `SimpleVectorStore` → `QuestionAnswerAdvisor` 检索（**阈值 0.6 + topK=2**）。

为什么是 300 字：默认 800 token 一片时，一份两页的售后政策只能切出 3 片，`topK=2` 等于把两页糊成一坨；按中文句子切成 300 字后切出 6 片，命中才准。这个数字是按样例效果调的，没有做评测集。

多用户隔离：分片写入时把 `userId` 打进元数据，检索时通过 `QuestionAnswerAdvisor.FILTER_EXPRESSION` 传过滤表达式；`userId` 先做 `[A-Za-z0-9_-]{1,32}` 校验再拼接，防止过滤表达式被注入。

`SimpleVectorStore` 是内存实现，`VectorStoreManager` 负责启动 `load`、关闭 `save`、每次上传后立即 `save`，否则重启就丢向量。

### 3. 上下文与稳定性

`LayeredChatMemory` 是分层会话记忆，替换掉默认的 `MessageWindowChatMemory`：

| 参数 | 值 | 作用 |
|---|---|---|
| `KEEP` | 20 | 每轮进上下文的原文条数 |
| `TRIGGER` | 30 | 累计超过就触发一次 LLM 压缩 |
| 缓冲带 | 10 | `TRIGGER - KEEP`；两者相等会每轮都压缩，成本翻倍 |

原文落 `chat_message`（索引 `(conversation_id, id)`），摘要落 `chat_summary`，状态全在 MySQL 里，重启不丢。压缩失败只打日志、保留原文、下一轮重试——不影响本轮回复，也不让缓冲区无限增长。

`FallbackChatModel` 做降级：云端异常回落本地 Ollama。两个细节：

- 降级时**丢掉 `ChatOptions`**（也就丢掉工具定义），退化成纯对话——本地小模型不支持工具调用，带着工具再请求只会继续报错；
- 流式用 `AtomicBoolean` 记录**是否已发出首包**，已发出就把异常抛给前端而不是重放，否则用户会看到「半句话 + 从头再来一遍」。

## 快速开始

**前置**：JDK 21、MySQL 8（需已存在 `java_shop` 库，建库脚本在电商主站仓库）、可选本地 Ollama（`ollama pull deepseek-r1:1.5b`）。

```bash
# 1. 建表（按顺序执行，都在 java_shop 库里）
mysql -u root -p -e "source src/main/resources/db/refund.sql"
mysql -u root -p -e "source src/main/resources/db/chat_memory.sql"
mysql -u root -p -e "source src/main/resources/db/demo-orders.sql"   # 可选：3 笔演示订单

# 2. 环境变量（仓库里不存任何凭据）
export MYSQL_USER=root
export MYSQL_PASSWORD=你的密码
export DASHSCOPE_API_KEY=你的百炼key

# 3. 启动（默认 8081，避开电商主站的 8080）
./mvnw spring-boot:run
```

Windows PowerShell：

```powershell
$env:MYSQL_PASSWORD='你的密码'
$env:DASHSCOPE_API_KEY='你的百炼key'
.\mvnw.cmd spring-boot:run
```

调用示例：

```bash
# 售后客服（流式）
curl -N "http://localhost:8081/ai/service?prompt=我的订单1001到哪了&chatId=demo1"

# 整条链路：查单 → 问政策 → 提交退款
curl -N "http://localhost:8081/ai/service?prompt=订单1002不想要了，能退吗&chatId=demo2"

# 上传政策文档后提问
curl -X POST "http://localhost:8081/ai/pdf/upload?userId=u1" -F "file=@docs/星野商城售后政策.pdf"
curl -N "http://localhost:8081/ai/pdf/chat?prompt=七天无理由怎么算&chatId=doc1&userId=u1"
```

`docs/星野商城售后政策.pdf` 是自带的演示语料，含七天无理由、运费承担、退款时效、换货、价保、争议处理六节。

## 目录结构

```
src/main/java/com/zlz/agent
├── config/       ChatClient 与向量库装配（含降级模型的包装）
├── constants/    三段提示词：通用对话 / 售后客服 / 知识库问答
├── controller/   对话、客服、知识库、历史四个入口
├── entity/       po（订单/商品/退款/会话）与 vo（工具返回给模型的视图）
├── mapper/       注解 SQL：JOIN 取明细、INSERT IGNORE 幂等、按会话读写记忆
├── memory/       LayeredChatMemory：窗口 + 摘要的分层记忆
├── model/        FallbackChatModel：云端异常回落本地
├── rag/          PDF 入库与向量库落盘
├── repository/   会话列表（当前为内存实现）
├── service/      订单、商品、售后三块业务
└── tools/        ShopTools：交给模型调用的三个工具
```

## 测试

```bash
mvn test
```

- `RagFilterOfflineTest`：用桩 `EmbeddingModel` + 捕获型 `ChatModel`，**不联网**验证两件事——① `QuestionAnswerAdvisor` 是否真的把 `FILTER_EXPRESSION` 作用到检索上（对照组两篇文档都进上下文，实验组只剩本用户的文档）；② `SimpleVectorStore` 落盘后由新实例 `load` 仍能检索。
- `ShopAiApplicationTests`：只验证 Spring 上下文能起来。

分层记忆的压缩逻辑同样用桩模型验过：32 条消息 → 压缩到 20 条、摘要落库、摘要模型只被调用 1 次（说明缓冲带生效、不会每轮都压）。该用例依赖本地 `java_shop` 库，未纳入仓库。

## 已知限制

这些是明确知道、但当前没做的取舍，列出来是为了划清边界：

- **没有鉴权**：`chatId` 由前端生成且未绑定用户，拿到别人的 `chatId` 就能读该会话历史；
- **会话列表在内存**：`InMemoryChatHistoryRepository` 重启即丢，应换成 `chat_session` 表；
- **向量库是单机内存 + 文件落盘**：不支持多实例，量大了要换 pgvector / Milvus；
- **摘要压缩与检索阈值没有评测集**：0.6 / topK=2 / 300 字都是按样例效果调的，没有召回率、准确率指标；
- **降级后不可用工具**：退回纯对话，只能回答不需要查库的问题；
- **只能解析文本型 PDF**：扫描件需要先做 OCR。

## 与电商主站的关系

本服务与电商系统（`java-ecommerce`）共用 `java_shop` 库：只读 `orders` / `order_items` / `products`，只写 `refund_apply` 与本服务自己的 `chat_message` / `chat_summary`。没有跨服务 HTTP 调用，避免引入注册中心与分布式事务。
