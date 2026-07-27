# KMA AI 知识库

KMA（Knowledge Management AI）是从 `opencourse/party-knowledge` 抽离并独立演进的企业级 RAG 知识库。项目不依赖 `opencourse`、MySQL 或 `party-common`，后端使用 JDK 21、Spring Boot 3.5、PostgreSQL/pgvector，前端使用 Vue 3、TypeScript、Vite 和 Element Plus。

当前产品是物理单实例 Mini 版，不包含部署分区目录、行级分区字段或请求分区上下文：

- `/p/{siteKey}/**`：按党建、制度/SOP、产品帮助中心等场景创建的知识门户。
- `/console/**`：内容治理、权限管理、模型、任务和系统运维后台。

旧 `/portal/**` 继续兼容，并跳转到默认站点。`/p/{siteKey}` 的 `siteKey` 仅表示 CMS 多站点，不是部署或数据边界。

后端采用模块化单体架构。同一个 JAR 支持 API 与持久化 Worker 分进程部署，可分别扩容；当前尚未拆成多个 Maven 物理模块。

## 当前状态

Mini 版已去除部署分区管理、用量限制和数据库行级分区，保留用户、角色、组织、知识空间 ACL、CMS 多站点与本地账号认证。

当前验证结果：

- 后端单元与本地 PostgreSQL 集成测试：208 项，0 失败，4 项跳过（需要 Docker 或外部服务）。
- 前端 Vitest：180 项，0 失败。
- 前端格式、Lint、类型、覆盖率、生产构建与 bundle 预算检查通过。

当前是**学习/演示用精简版本，不是生产就绪版本**。生产使用请返回完整 KMA 仓库。

## 核心能力

- 细粒度 RBAC、组织继承与知识空间 ACL。
- 本地账号、令牌旋转、重放检测和权限即时失效。
- 持久化入库任务、租约、`FOR UPDATE SKIP LOCKED`、重试、死信和重启恢复。
- 文档 staging、版本化处理和解析/分块/Embedding 成功后的原子切换。
- PDF、Word、Excel、PPT、HTML、文本与图片入口；扫描件支持 `NEEDS_OCR`。
- 向量与全文真实分数、中文检索词、RRF 混合召回和可选重排。
- 普通/流式问答统一编排、引用复核、无证据拒答和稳定 SSE 事件。
- Embedding/LLM/Reranker Profile、主备链、动态解析和向量版本重建。
- 标准问答集、Recall@K、MRR、答案正确率、引用准确率和拒答率门禁。
- 党建内容草稿、审核、发布、下线、专题、效力状态、收藏和阅读历史。
- 本地/MinIO Storage SPI、对象引用、校验和、对账与孤儿文件清理。
- OpenAPI 类型单源、服务端分页、前端权限菜单和响应式双门户。
- 静态业务模块、路由级按需加载、三套 CMS 门户模板、受控区块编排和全局运行时主题。
- 跨标签页会话退出。
- 多站点门户、三类场景包、多页面区域布局、统一异步区块注册表和独立导航/页脚。
- CMS V2 草稿、乐观锁、审核、原子发布、版本回滚、内容范围编译、品牌资产和访问分析。
- JSON Schema 双端契约、语义 Token、浅色/深色/跟随系统模式及发布期站点作用域 CSS。
- 三套场景视觉包：党建权威文档、制度/SOP 工作台、产品帮助中心；视觉包可改变壳层、导航、排版和组件结构，而非仅换色。
- KMA 安全 Markdown 指令（提示、徽章、步骤、FAQ、下载区）与平台 CI 签名的 iframe 扩展包；站点管理员只能配置获准扩展，不能执行任意 JavaScript。
- CMS V3 响应式布局树、全局页头/页脚与全部系统页面编排、可复用区块、撤销/重做、V2 无损转草稿和站点静态代码沙箱。

## 环境要求

- JDK 21。
- Maven 3.9.6+；仓库已包含 Maven Wrapper。
- Node.js `^20.19.0` 或 `>=22.12.0`，并安装配套 npm。
- PostgreSQL 16+ 和 pgvector；本项目已在 PostgreSQL 18.4 验证。
- Docker 可选，仅用于容器化 PostgreSQL、Testcontainers 或整体部署。
- 本地开发默认使用文件存储；MinIO、OCR 和模型服务按需配置。

## 本机启动

以下示例使用本机 PostgreSQL，不要求 Docker。密码和密钥只写入当前进程环境或 Secret Manager，不要提交到 Git。

### 1. 准备 PostgreSQL

```powershell
createdb -U postgres kma_mini
psql -U postgres -d kma_mini -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

数据库已存在时只需确认 `vector` 扩展可用。应用启动时会自动执行 Flyway V1–V22。

### 2. 启动后端 API

```powershell
cd D:\workspace\claudecode\QuickKB\Kma_mini

$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:KMA_DB_URL = "jdbc:postgresql://localhost:5432/kma_mini"
$env:KMA_DB_USERNAME = "postgres"
$env:KMA_DB_PASSWORD = "<通过安全方式注入>"
$env:KMA_SECURITY_MODE = "local"
$env:KMA_AUTH_JWT_SECRET = "<至少 32 字节随机值>"
$env:KMA_BOOTSTRAP_ADMIN_PASSWORD = "<首次启动临时管理员密码>"

.\mvnw.cmd spring-boot:run
```

首次启动会创建管理员 `admin`，首次登录后必须修改临时密码。README 不提供固定公共密码。

### 3. 启动 Vue 3 前端

另开一个 PowerShell：

```powershell
cd D:\workspace\claudecode\QuickKB\Kma_mini\kma-admin-web
npm ci
npm run dev
```

默认地址：

- 登录页：`http://localhost:27183/login`
- 默认知识门户：`http://localhost:27183/p/default/home`
- 治理后台：`http://localhost:27183/console`
- API：`http://localhost:8090/api/v1`
- Swagger UI：`http://localhost:8090/swagger-ui.html`
- Readiness：`http://localhost:8090/actuator/health/readiness`
- Liveness：`http://localhost:8090/actuator/health/liveness`

### 4. 启动 Worker

API 默认只创建持久化入库任务。生产或分进程开发模式需要另开终端启动无 Web Worker：

```powershell
cd D:\workspace\claudecode\QuickKB\Kma_mini

$env:KMA_DB_URL = "jdbc:postgresql://localhost:5432/kma_mini"
$env:KMA_DB_USERNAME = "postgres"
$env:KMA_DB_PASSWORD = "<通过安全方式注入>"
$env:KMA_AUTH_JWT_SECRET = "<与 API 相同>"
$env:KMA_INGESTION_WORKER_ENABLED = "true"
$env:KMA_FEED_ENABLED = "true"
$env:KMA_GOVERNANCE_ENABLED = "true"

java -jar target\kma-mini-server-0.1.0-mini.jar --spring.main.web-application-type=none
```

使用 JAR 启动前先执行：

```powershell
.\mvnw.cmd clean package
```

本地演示也可以让 API 合并执行 Worker，但不建议用于生产：

```powershell
$env:KMA_INGESTION_WORKER_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

## 模型与外部依赖

默认模型配置：

```text
Embedding: local-bge-m3 / http://localhost:9997/v1 / bge-m3
LLM:       ollama / http://localhost:11434/v1 / qwen2.5
门户设计: DeepSeek / https://api.deepseek.com / deepseek-v4-flash
Reranker:  可选；未配置时使用中文词项覆盖率降级重排
```

模型不可用时应用仍可启动，依赖状态显示 `DEGRADED`；问答、Embedding 或重排能力会按 Profile 和 fallback 链返回明确错误或降级结果。模型密钥只能通过环境变量或 Secret Provider 注入，数据库和前端只保存密钥别名。

门户设计中心的 AI 设计独立使用 DeepSeek V4 Flash，不会切换知识问答模型。启动后端前设置
`KMA_DEEPSEEK_API_KEY` 即可启用；未配置时设计器会明确显示不可用。该密钥不会写入数据库、
仓库、日志或前端响应。AI 结果只生成候选草稿，必须在设计器中预览并确认应用，之后仍需手动
保存、审核和发布。

关键环境变量参见 [.env.example](.env.example)。

## 身份与权限

mini 版只支持 `KMA_SECURITY_MODE=local`，使用 Argon2id 密码、短期 Access Token 和旋转 Refresh Token。

授权规则：

1. RBAC 决定用户能执行什么业务操作。
2. 组织参与成员归属和空间 ACL 继承。
3. 空间 ACL 决定用户能访问哪些知识空间。
4. 空间类操作必须同时满足 RBAC 与 ACL。
5. 数据库是物理单实例模型，不包含部署分区目录、行级分区字段或用量限制表；CMS 的 `/p/{siteKey}` 仅表示多站点门户。

## 演示数据

演示脚本只允许写入独立的 `kma_ui_test` 数据库，不会连接或清理正式业务库：

```powershell
$env:PGPASSWORD = "<本机 PostgreSQL 密码>"
.\scripts\seed-demo-data.ps1
```

脚本覆盖用户、角色、组织、模型 Profile、数据集、空间与 ACL、党建内容、专题、门户配置、收藏、阅读历史、任务、审计和 RAG 评测数据。演示账号密码由测试环境管理员创建或重置，仓库不保存固定密码。

本机 `kma_mini` 开发库可执行幂等的默认演示数据种子：

为默认知识空间补充“三会一课”AI 问答演示知识，可执行：

```powershell
$env:PGPASSWORD = "<本机 PostgreSQL 密码>"
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" `
  -h localhost -U postgres -d kma_mini -v ON_ERROR_STOP=1 `
  -f .\scripts\sql\seed-default-three-meetings.sql
```

脚本可重复执行，写入 5 篇已发布演示资料和 15 个可全文检索的 Chunk，并验证核心关键词召回。所有资料均明确标记为演示内容。

## API 调用示例

先登录获取 Access Token：

```powershell
$loginBody = @{
  username = "admin"
  password = "<当前管理员密码>"
} | ConvertTo-Json

$login = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8090/api/v1/auth/login" `
  -ContentType "application/json" `
  -Body $loginBody

$headers = @{
  Authorization = "Bearer $($login.data.accessToken)"
  "Idempotency-Key" = "demo:document:1"
}
```

文本入库：

```powershell
$body = @{
  spaceCode = "default"
  title = "KMA 示例"
  sourceTag = "demo"
  sourceVersion = 1
  content = "KMA 是一个独立的 AI 知识库服务。"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8090/api/v1/documents/text" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $body
```

非流式问答：

```powershell
$qaBody = @{
  spaceCode = "default"
  query = "KMA 是什么？"
  topK = 5
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8090/api/v1/qa" `
  -Headers @{ Authorization = $headers.Authorization } `
  -ContentType "application/json" `
  -Body $qaBody
```

SSE 端点为 `POST /api/v1/qa/stream`，事件类型固定为 `citations`、`message`、`heartbeat`、`done`、`error`。

## 测试与质量门禁

### 后端快速测试

```powershell
.\mvnw.cmd test
```

### 前端完整质量门禁

```powershell
cd kma-admin-web
npm ci
npm run quality
npm run test:e2e
npm run api:generate
```

### JDK 21 + 本机 PostgreSQL 终态门禁

测试数据库必须以 `_test` 结尾：

```powershell
cd D:\workspace\claudecode\QuickKB\Kma_mini

$env:KMA_IT_DB_URL = "jdbc:postgresql://localhost:5432/kma_test"
$env:KMA_IT_DB_USERNAME = "postgres"
$env:KMA_IT_DB_PASSWORD = "<通过安全方式注入>"

.\mvnw.cmd "-Plocal-pg-it,p0-quality-gate,quality-gate" verify
```

mini 版不再包含 `scripts/verify.ps1` 与完整生产门禁脚本。

真实 MinIO/OCR 测试默认跳过，只有显式提供隔离环境时才运行。Docker 不可用不会阻止本机 PostgreSQL 门禁。

## 生产门禁

只有 `scripts/release/release-gate.ps1` 最终输出 `PASSED`，项目才能标记为生产就绪：

```powershell
# 只生成当前阻断报告
.\scripts\release\release-gate.ps1 -ReportOnly

# 生产候选环境执行完整外部依赖、负载和稳定性门禁
.\scripts\release\release-gate.ps1 -RunExternalTests -RunLoadTests -RunStability
```

截至 2026-07-24，最近一次本机预检仍为 `BLOCKED`。已完成数据库备份/迁移/恢复、真实 DeepSeek 非生产评测、1 万 Chunk/20 并发基线和 1 分钟稳定性探针，但这些结果不能替代：

- 正式权威语料和完整 Embedding/Reranker 链路评测。
- 真实 MinIO、OCR 和 Embedding/Reranker 链路。
- 500 万 Chunk、100 并发性能测试。
- 24 小时稳定性与 PostgreSQL、模型、对象存储故障恢复演练。



## 文档索引

mini 版为精简仓库，原 `docs/` 目录已移除。运行时关键环境变量参见 [.env.example](.env.example)。
