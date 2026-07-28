## [ERR-20260727-001] spring_constructor_injection

**Logged**: 2026-07-27T12:46:00+08:00
**Priority**: high
**Status**: resolved
**Area**: backend

### Summary
认证服务移除一个构造器依赖后仍保留两个构造器，编译通过但 Spring 启动时无法选择注入构造器。

### Error
```
Failed to instantiate [com.kma.common.security.KmaLocalAuthService]: No default constructor found
```

### Context
- 构建后的应用在临时 V22 验证库上启动。
- Java 编译和打包均成功，错误仅在 Spring ApplicationContext 初始化时暴露。

### Suggested Fix
多构造器 Spring Bean 必须显式标注生产构造器；数据库迁移类改动的验证门槛必须包含完整应用启动。

### Metadata
- Reproducible: yes
- Related Files: src/main/java/com/kma/common/security/KmaLocalAuthService.java

### Resolution
- **Resolved**: 2026-07-27T12:47:00+08:00
- **Commit/PR**: pending
- **Notes**: 为五参数生产构造器恢复 `@Autowired`，保留四参数构造器供单元测试使用。

---

## [ERR-20260728-013] portal_qa_model_services_unavailable

**Logged**: 2026-07-28T14:18:00+08:00
**Priority**: high
**Status**: pending
**Area**: infra

### Summary
V4 AI 问答页面和 SDK 通信正常，但知识问答依赖的 Ollama 与本地 Embedding 服务均未监听，回答请求失败。

### Error
```
localhost:11434 TCP connect failed
localhost:9997 TCP connect failed
```

### Context
- DeepSeek V4 Flash 整站主题提案已真实调用成功，它是独立的门户设计模型。
- 知识 RAG 仍按既定边界使用 `knowledge.llm` 与 embedding profile，不应自动切换到设计模型。

### Suggested Fix
启动配置对应的 Ollama 与 BGE-M3 服务，或在模型配置中切换可用的知识问答 Profile；Portal SDK 应透出脱敏业务错误而不是仅显示泛化失败。

### Metadata
- Reproducible: yes
- Related Files: src/main/resources/application.yml

---

## [ERR-20260728-012] preview_new_tab_lost_session

**Logged**: 2026-07-28T14:13:00+08:00
**Priority**: high
**Status**: resolved
**Area**: frontend

### Summary
真实草稿预览使用 `noopener` 直接开新标签，导致浏览器不复制 sessionStorage 访问令牌并跳到登录页。

### Error
```
/login?redirect=/p/default/home?previewVersion=18
```

### Context
- 预览接口本身要求管理员已登录。
- 当前前端路由守卫先检查 sessionStorage；`noopener` 新上下文没有令牌副本。

### Suggested Fix
先通过同源 `window.open` 创建新标签以复制 sessionStorage，随后立即清空新窗口 `opener`，兼顾认证与反向标签劫持隔离。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/views/theme/PortalThemeStudioView.vue

### Resolution
- **Resolved**: 2026-07-28T14:13:30+08:00
- **Commit/PR**: pending
- **Notes**: 已调整真实预览打开顺序，等待浏览器复测。

---

## [ERR-20260728-011] browser_dom_cua_node_id_type

**Logged**: 2026-07-28T14:09:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
浏览器 DOM 控制 API 的 `node_id` 要求字符串，误传数字导致保存按钮测试未执行。

### Error
```
dom_cua.click node_id must be a string
```

### Context
- 可见 DOM 已唯一确认保存草稿按钮为 node_id 31。
- 错误发生在调用参数类型，不是页面或产品故障。

### Suggested Fix
DOM 控制节点标识始终按工具契约原样作为字符串传入。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/views/theme/PortalThemeStudioView.vue

### Resolution
- **Resolved**: 2026-07-28T14:09:15+08:00
- **Commit/PR**: pending
- **Notes**: 后续调用改用 `node_id: "31"`。

---

## [ERR-20260728-010] backend_database_role_mismatch

**Logged**: 2026-07-28T14:01:00+08:00
**Priority**: high
**Status**: resolved
**Area**: infra

### Summary
首次重启沿用默认数据库用户 `kma`，但提供的是 PostgreSQL 管理用户密码，Flyway 鉴权失败。

### Error
```
SQL State 28P01: 用户 "kma" Password 验证失败
```

### Context
- `application.yml` 默认 `KMA_DB_USERNAME=kma`。
- 当前已确认可用凭据属于 `postgres`，且 V23 需要执行迁移。
- 不能修改共享 `kma` 角色密码，以免影响多租户版。

### Suggested Fix
Mini 进程显式设置 `KMA_DB_USERNAME=postgres` 与对应进程级密码；不修改数据库角色或源库。

### Metadata
- Reproducible: yes
- Related Files: src/main/resources/application.yml

### Resolution
- **Resolved**: 2026-07-28T14:01:30+08:00
- **Commit/PR**: pending
- **Notes**: 改为仅在 Mini 后端进程环境中使用 postgres 用户，不更改任何数据库角色。

---

## [ERR-20260728-009] spring_boot_jar_locked_by_running_backend

**Logged**: 2026-07-28T13:55:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: infra

### Summary
后端仍从 target JAR 运行，Spring Boot repackage 无法重命名被 Windows 锁定的构件。

### Error
```
Unable to rename kma-mini-server-0.1.0-mini.jar to .jar.original
```

### Context
- V23 上线前执行 Maven package。
- Windows 正在运行的 8090 后端持有 JAR 文件句柄。

### Suggested Fix
精确确认 8090 监听进程属于 KMA Mini 后端，停止该进程后重新 package；不停止 PostgreSQL或多租户 KMA。

### Metadata
- Reproducible: yes
- Related Files: target/kma-mini-server-0.1.0-mini.jar
- Recurrence-Count: 2

### Resolution
- **Resolved**: 2026-07-28T13:57:00+08:00
- **Commit/PR**: pending
- **Notes**: 已核对 PID 24732 的命令行只指向 KMA Mini JAR，停止该后端后 package 成功；2026-07-28 修复 SDK manifest 时再次遇到，运行态验证应先使用 `compile`，待精确重启 8090 后端时再执行 `package`。

---

## [ERR-20260728-008] auth_principal_display_field_mismatch

**Logged**: 2026-07-28T13:48:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: frontend

### Summary
V4 Portal SDK 用户上下文误用不存在的 `nickname` 字段，前端类型检查失败。

### Error
```
PortalThemeHost.vue: Property 'nickname' does not exist on type UserInfo
```

### Context
- 实际认证契约提供 `displayName` 与 `username`。
- 主题上下文只暴露脱敏身份展示字段，不传 token。

### Suggested Fix
对接认证上下文时以生成类型为准，使用 `displayName`，并保持最小字段投影。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/cms/v4/PortalThemeHost.vue

### Resolution
- **Resolved**: 2026-07-28T13:48:30+08:00
- **Commit/PR**: pending
- **Notes**: 已改为 `displayName`，模板渲染同步更新。

---

## [ERR-20260728-007] theme_studio_stylelint_rules

**Logged**: 2026-07-28T13:29:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: frontend

### Summary
主题工作台首次样式检查违反项目的重复选择器、alpha 小数写法和媒体查询范围语法规则。

### Error
```
no-duplicate-selectors
alpha-value-notation
media-feature-range-notation
```

### Context
- 执行 `npm run stylelint`。
- 新增全屏三栏主题工作台样式。

### Suggested Fix
合并相同选择器，透明度使用 `0.35`，媒体查询使用 `(width <= 1100px)`。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/views/theme/PortalThemeStudioView.vue

### Resolution
- **Resolved**: 2026-07-28T13:29:30+08:00
- **Commit/PR**: pending
- **Notes**: 三项规则均已按项目规范修正。

---

## [ERR-20260728-006] pg_dump_validation_timeout

**Logged**: 2026-07-28T12:18:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: infra

### Summary
V23 临时验证库备份在 120 秒工具期限内未返回，需要确认 `pg_dump` 是否仍在运行及 dump 是否完整。

### Error
```
command timed out after 124062 milliseconds
```

### Context
- 对 `kma_mini` 执行 PostgreSQL 18 custom-format 只读备份。
- 目标为仓库 `target/kma_mini_v23_verify.dump`，未触碰多租户库 `kma`。

### Suggested Fix
先检查 `pg_dump` 进程和文件增长；若仍运行则等待完成，若已终止则删除不完整临时文件后以更长超时重试。

### Metadata
- Reproducible: yes
- Related Files: target/kma_mini_v23_verify.dump

### Resolution
- **Resolved**: 2026-07-28T12:20:00+08:00
- **Commit/PR**: pending
- **Notes**: 根因是存在性检查发生在设置 `PGPASSWORD` 之前，psql 等待交互密码；已改为在任何 PostgreSQL 客户端调用前注入进程环境变量。

---

## [ERR-20260728-005] vue_lint_unnecessary_escape

**Logged**: 2026-07-28T12:13:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: frontend

### Summary
主题工作台新增 HTML 文件默认内容在单引号字符串中多余转义双引号，ESLint 拒绝构建。

### Error
```
PortalThemeStudioView.vue:231:58 no-useless-escape
```

### Context
- 执行 `npm run lint`。
- HTML 默认片段使用单引号 TypeScript 字符串。

### Suggested Fix
字符串定界符已经避免冲突时不转义内部双引号，并在新增 Vue 文件后运行 ESLint。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/views/theme/PortalThemeStudioView.vue

### Resolution
- **Resolved**: 2026-07-28T12:13:30+08:00
- **Commit/PR**: pending
- **Notes**: 已移除多余转义。

---

## [ERR-20260728-004] security_test_message_mismatch

**Logged**: 2026-07-28T12:09:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
主题安全测试断言使用“`三大括号`”措辞，而扫描器稳定诊断文本为“`禁止的原始输出`”，导致行为正确但测试失败。

### Error
```
PortalThemeSecurityTest.rejectsNetworkEscapeRawOutputAndIncludeCycles
none matched predicate "三大括号"
```

### Context
- 扫描器正确识别了原始输出、远程 CSS、fetch 和 include 循环。
- 失败只来自断言文案与实际业务诊断不一致。

### Suggested Fix
安全测试断言稳定的错误语义或错误码，避免绑定不一致的展示措辞。

### Metadata
- Reproducible: yes
- Related Files: src/test/java/com/kma/knowledge/service/PortalThemeSecurityTest.java

### Resolution
- **Resolved**: 2026-07-28T12:09:30+08:00
- **Commit/PR**: pending
- **Notes**: 断言调整为稳定语义“原始输出”。

---

## [ERR-20260728-003] integration_test_new_dependency_import

**Logged**: 2026-07-28T12:07:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
集成测试构造器补入 `PortalThemeService` mock 后漏同步 import，测试源码编译失败。

### Error
```
KnowledgePortalSiteLocalPostgresIntegrationTest.java:[88,56] 找不到符号: 类 PortalThemeService
```

### Context
- PortalSiteService 新增 Theme Service 构造依赖。
- 主源码编译成功，测试源码在运行目标测试时暴露缺失 import。

### Suggested Fix
服务构造器依赖发生变化时，同时更新直接构造该服务的测试、imports，并执行真实 `test` 而非仅依赖增量 `test-compile`。

### Metadata
- Reproducible: yes
- Related Files: src/test/java/com/kma/knowledge/KnowledgePortalSiteLocalPostgresIntegrationTest.java

### Resolution
- **Resolved**: 2026-07-28T12:07:30+08:00
- **Commit/PR**: pending
- **Notes**: 已导入 `PortalThemeService`。

---

## [ERR-20260728-002] powershell_unquoted_maven_test_list

**Logged**: 2026-07-28T12:05:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
PowerShell 将 Maven `-Dtest=A,B` 中的逗号解析为参数列表，目标测试命令未执行。

### Error
```
ParserError: Missing argument in parameter list.
```

### Context
- 执行 `.\\mvnw.cmd -q -Dtest=PortalThemeSecurityTest,PortalSiteConfigValidatorTest test`。
- Windows PowerShell 环境。

### Suggested Fix
包含逗号的 Maven `-Dtest` 参数必须整体加引号。

### Metadata
- Reproducible: yes
- Related Files: pom.xml

### Resolution
- **Resolved**: 2026-07-28T12:05:30+08:00
- **Commit/PR**: pending
- **Notes**: 改用 `"-Dtest=PortalThemeSecurityTest,PortalSiteConfigValidatorTest"`。

---

## [ERR-20260728-001] java_missing_import_after_v4_page_factory

**Logged**: 2026-07-28T12:00:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: backend

### Summary
Portal V4 页面工厂新增 `ObjectNode` 后漏加显式 import，导致 Maven 编译失败。

### Error
```
PortalSiteService.java:[362,13] 找不到符号
符号: 类 ObjectNode
```

### Context
- 执行 `.\\mvnw.cmd -q -DskipTests compile`。
- 新增 V4 路由到合成页面对象时使用了 Jackson `ObjectNode`。

### Suggested Fix
新增 Java 类型后立即运行主源码编译，并同时检查 import 与测试中的构造器签名。

### Metadata
- Reproducible: yes
- Related Files: src/main/java/com/kma/knowledge/service/PortalSiteService.java

### Resolution
- **Resolved**: 2026-07-28T12:01:00+08:00
- **Commit/PR**: pending
- **Notes**: 已补充 `com.fasterxml.jackson.databind.node.ObjectNode` import。

---

## [ERR-20260727-032] parallel_playwright_shared_artifacts

**Logged**: 2026-07-27T17:57:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: testing

### Summary
两条 Playwright 命令并行运行并共享 `test-results`，失败用例收集 trace 时出现资源文件争抢；同时断言仍依赖旧的组合宽度文本。

### Error
```
getByText('1440px · 77%') not found
ENOENT ... test-results/.playwright-artifacts-0/...
```

### Context
- 运行页全宽与专题响应式用例通过。
- 设计器 UI 已把连续宽度作为独立状态栏控件，组合文本不是稳定契约。

### Suggested Fix
Playwright 套件串行执行；断言使用“预览宽度”滑块和独立宽度文本。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/tests/e2e/frontend-modules-cms.spec.ts

### Resolution
- **Resolved**: 2026-07-27T17:58:00+08:00
- **Commit/PR**: pending
- **Notes**: 已调整测试策略，后续浏览器测试不再并行共享产物目录。

---

## [ERR-20260727-031] repeated_portal_frame_path_assumption

**Logged**: 2026-07-27T17:42:00+08:00
**Priority**: low
**Status**: resolved
**Area**: frontend

### Summary
诊断专题页布局时再次按错误的 `components/portal` 路径读取页面框架，实际导入明确指向 `cms/v3`。

### Error
```
Cannot find path .../components/portal/PortalSystemPageFrame.vue
```

### Context
- 只读检查，没有修改产品代码。
- `PortalTopicsView.vue` 的 import 已给出正确位置。

### Suggested Fix
读取同文件依赖时直接依据 import 路径解析，不再按目录语义猜测。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/cms/v3/PortalSystemPageFrame.vue

### Resolution
- **Resolved**: 2026-07-27T17:43:00+08:00
- **Commit/PR**: not applicable
- **Notes**: 已切换到 import 指向的精确路径。

---

## [ERR-20260727-030] git_grep_cached_option_order

**Logged**: 2026-07-27T17:33:00+08:00
**Priority**: low
**Status**: resolved
**Area**: tooling

### Summary
暂存区密钥扫描时将 `git grep --cached` 放在 pattern 后，Git 拒绝该选项顺序。

### Error
```
fatal: option '--cached' must come before non-option arguments
```

### Context
- 没有修改文件或暂存区。
- 工作区级扫描此前已确认密钥未落盘。

### Suggested Fix
使用 `git grep --cached -n "<pattern>"`，所有选项置于 pattern 之前。

### Metadata
- Reproducible: yes
- Related Files: staged changes

### Resolution
- **Resolved**: 2026-07-27T17:34:00+08:00
- **Commit/PR**: pending
- **Notes**: 已用正确参数顺序重新执行。

---

## [ERR-20260727-029] portal_version_primary_key_name

**Logged**: 2026-07-27T17:29:00+08:00
**Priority**: low
**Status**: resolved
**Area**: database

### Summary
只读核验门户发布版本时假设版本表主键为 `id`，实际列名是 `config_version_id`。

### Error
```
字段 v.id 不存在
```

### Context
- 查询只读且未改变数据库。
- 通过 `information_schema.columns` 确认精确列名后重新核验成功。

### Suggested Fix
临时数据库核验 SQL 应先读取表列定义，避免按常见命名猜测。

### Metadata
- Reproducible: yes
- Related Files: knowledge_portal_config_version

### Resolution
- **Resolved**: 2026-07-27T17:30:00+08:00
- **Commit/PR**: pending
- **Notes**: 已确认 default 仍为 V8 published，且没有新增草稿。

---

## [ERR-20260727-028] portal_ai_published_version_blocked

**Logged**: 2026-07-27T17:25:00+08:00
**Priority**: high
**Status**: resolved
**Area**: backend

### Summary
AI 提案服务错误地要求当前版本必须是草稿，导致实际站点最新版本为 `published` 时无法生成提案。

### Error
```
PORTAL_VERSION_NOT_DRAFT
```

### Context
- AI 提案本身只读，不写数据库；前端在用户保存时本就会从已发布版本创建新草稿。
- E2E mock 使用草稿版本，未覆盖真实 V8 发布态。

### Suggested Fix
提案阶段只校验版本存在和乐观锁，不限制版本状态；增加已发布版本的服务测试。

### Metadata
- Reproducible: yes
- Related Files: src/main/java/com/kma/knowledge/service/PortalDesignService.java
- Related Files: src/test/java/com/kma/knowledge/service/PortalDesignServiceTest.java

### Resolution
- **Resolved**: 2026-07-27T17:26:00+08:00
- **Commit/PR**: pending
- **Notes**: 已移除错误状态限制并将测试基线改为 published。

---

## [ERR-20260727-027] portal_design_client_constructor_selection

**Logged**: 2026-07-27T17:20:00+08:00
**Priority**: high
**Status**: resolved
**Area**: backend

### Summary
DeepSeek 客户端包含生产构造器和包级测试构造器，Spring 在应用启动时无法自动选择，导致上下文装配失败。

### Error
```
BeanInstantiationException: No default constructor found
NoSuchMethodException: PortalDesignLlmClient.<init>()
```

### Context
- 单元测试和 Maven 全量测试均通过，但测试集未启动覆盖该组件的完整应用上下文。
- 实际 JAR 启动验证立即发现该问题。

### Suggested Fix
多构造器 Spring Bean 应显式使用 `@Autowired` 标记生产构造器，并保留真实 JAR 启动健康检查作为发布门槛。

### Metadata
- Reproducible: yes
- Related Files: src/main/java/com/kma/knowledge/client/llm/PortalDesignLlmClient.java

### Resolution
- **Resolved**: 2026-07-27T17:21:00+08:00
- **Commit/PR**: pending
- **Notes**: 已标记生产构造器，待重新打包启动验证。

---

## [ERR-20260727-026] local_database_username_assumption

**Logged**: 2026-07-27T17:16:00+08:00
**Priority**: low
**Status**: resolved
**Area**: database

### Summary
按应用配置默认值尝试使用 `kma` 用户验证本地数据库时认证失败，本机已部署实例实际使用 `postgres`。

### Error
```
用户 "kma" Password 认证失败
```

### Context
- 仅执行只读连接检查。
- 随后用既有部署账号 `postgres` 成功确认目标库为 `kma_mini`、Flyway 已到 V22。

### Suggested Fix
本地启动验证前以实际运行环境或只读连接探测确认数据库用户名，不将仓库默认值等同于机器部署值。

### Metadata
- Reproducible: yes
- Related Files: src/main/resources/application.yml

### Resolution
- **Resolved**: 2026-07-27T17:17:00+08:00
- **Commit/PR**: pending
- **Notes**: 后续本机启动使用进程级 `KMA_DB_USERNAME=postgres`。

---

## [ERR-20260727-025] spring_boot_repackage_locked_jar

**Logged**: 2026-07-27T16:25:00+08:00
**Priority**: low
**Status**: resolved
**Area**: tooling

### Summary
生产打包尝试覆盖当前 8090 服务正在运行的同名 JAR，Windows 文件锁使 Spring Boot repackage 无法重命名文件。

### Error
```
Unable to rename target/kma-mini-server-0.1.0-mini.jar to ...jar.original
```

### Context
- 当前 Mini 后端 PID 29096 直接从该 JAR 启动。
- 编译和测试已正常通过，失败仅发生在最终 repackage。

### Suggested Fix
先完成不依赖打包的验证，切换服务时仅停止确认过的 8090 Mini 进程，再重新执行 package。

### Metadata
- Reproducible: yes
- Related Files: pom.xml, target/kma-mini-server-0.1.0-mini.jar

### Resolution
- **Resolved**: 2026-07-27T16:26:00+08:00
- **Commit/PR**: pending
- **Notes**: 已确认锁文件进程和安全切换顺序。

---

## [ERR-20260727-024] portal_design_service_path

**Logged**: 2026-07-27T16:20:00+08:00
**Priority**: low
**Status**: resolved
**Area**: tooling

### Summary
清理未使用导入时将门户设计服务误判在 `portal/service` 包，实际位于顶层 `service` 包。

### Error
```
Failed to read ...\portal\service\PortalDesignService.java: path not found
```

### Context
- 仅第一次补丁路径错误，没有修改文件。
- 随后通过 `rg --files` 定位到正确路径。

### Suggested Fix
修改新建或不熟悉的文件前先使用 `rg --files -g "<filename>"` 确认实际路径。

### Metadata
- Reproducible: yes
- Related Files: src/main/java/com/kma/knowledge/service/PortalDesignService.java

### Resolution
- **Resolved**: 2026-07-27T16:21:00+08:00
- **Commit/PR**: pending
- **Notes**: 已在正确文件中移除未使用导入。

---

## [ERR-20260727-023] portal_ai_stylelint_and_workdir

**Logged**: 2026-07-27T17:39:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: frontend

### Summary
AI 抽屉首次 Stylelint 检查发现透明度格式和选择器降序问题，且附加后端搜索使用了错误工作目录。

### Error
```
alpha-value-notation: Expected percentage alpha to be decimal.
no-descending-specificity: AI proposal selectors are out of order.
rg: src/main/java/... path not found from kma-admin-web.
```

### Context
- 格式、ESLint 和 TypeScript 已通过。
- Stylelint 在最终阶段阻止质量门禁。

### Suggested Fix
使用小数 alpha，将低特异性选择器移到 hover/header 规则之前，并从仓库根目录搜索后端文件。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/views/lowcode/DesignerCanvasNode.vue, kma-admin-web/src/views/lowcode/PortalAiDesignDrawer.vue

### Resolution
- **Resolved**: 2026-07-27T17:39:00+08:00
- **Commit/PR**: pending
- **Notes**: 样式规则已按质量门禁调整。

---

## [ERR-20260727-022] e2e_unscoped_designer_text

**Logged**: 2026-07-27T17:34:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
拖拽成功后全局“公告栏”文本同时匹配组件库、画布、预览和属性栏。

### Error
```
Playwright strict mode violation: getByText('公告栏') resolved to 4 elements.
```

### Context
- 组件已经成功拖入并被选中。
- 断言没有限定到画布区域。

### Suggested Fix
拖拽 E2E 使用画布容器内的节点类或节点数量作为权威信号。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/tests/e2e/frontend-modules-cms.spec.ts

### Resolution
- **Resolved**: 2026-07-27T17:34:00+08:00
- **Commit/PR**: pending
- **Notes**: 断言已收窄到画布组件节点。

---

## [ERR-20260727-021] structured_clone_vue_proxy

**Logged**: 2026-07-27T17:29:00+08:00
**Priority**: high
**Status**: resolved
**Area**: frontend

### Summary
AI 提案存入 Vue ref 后成为响应式 Proxy，直接 `structuredClone` 导致应用事件中断。

### Error
```
AI proposal remained open and the canvas did not update after “应用到草稿”.
```

### Context
- 提案接口、预览摘要和差异统计均已成功。
- 父组件在克隆响应式 `proposal.target` 时抛出 DataCloneError。

### Suggested Fix
设计器通用克隆函数优先使用 `structuredClone`，遇到不可克隆的响应式 Proxy 时使用 JSON 配置安全回退。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/cms/v3/designerTree.ts

### Resolution
- **Resolved**: 2026-07-27T17:29:00+08:00
- **Commit/PR**: pending
- **Notes**: 通用克隆函数已兼容 Vue 响应式配置对象。

---

## [ERR-20260727-020] portal_toolbar_ai_overlap

**Logged**: 2026-07-27T17:25:00+08:00
**Priority**: high
**Status**: resolved
**Area**: frontend

### Summary
AI 设计入口加入全局工具栏后，在实际后台内容宽度下被右侧发布操作区覆盖。

### Error
```
Playwright click timeout: a disabled toolbar button intercepts pointer events over “AI 设计”.
```

### Context
- 浏览器视口为 1600px，但治理后台侧栏会压缩设计器的实际容器宽度。
- 设计器工具栏同时包含工作区开关、保存和审核发布操作。

### Suggested Fix
将页面级 AI 入口放入画布元工具栏，并继续依据设计器容器宽度收纳全局次要操作。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/views/lowcode/PortalLowCodeDesignerView.vue

### Resolution
- **Resolved**: 2026-07-27T17:25:00+08:00
- **Commit/PR**: pending
- **Notes**: AI 入口移至当前页面与断点工具栏。

---

## [ERR-20260727-019] rg_missing_optional_scripts_directory

**Logged**: 2026-07-27T17:21:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
OpenAPI 搜索命令包含不存在的根级 `scripts` 目录，导致 `rg` 非零退出。

### Error
```
rg: scripts: 系统找不到指定的文件。
```

### Context
- OpenAPI 配置已从 `application.yml` 和 `pom.xml` 找到。
- 错误仅来自额外的可选搜索路径。

### Suggested Fix
组合搜索前先使用 `rg --files` 确认目录，或只传入已存在的路径。

### Metadata
- Reproducible: yes
- Related Files: src/main/resources/application.yml, pom.xml

### Resolution
- **Resolved**: 2026-07-27T17:21:00+08:00
- **Commit/PR**: pending
- **Notes**: 后续限定到已存在路径。

---

## [ERR-20260727-018] powershell_maven_test_list

**Logged**: 2026-07-27T17:16:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
PowerShell 将 Maven `-Dtest` 中未加引号的逗号解析为参数分隔符。

### Error
```
ParserError: Missing argument in parameter list.
```

### Context
- 执行 `mvn -q -Dtest=PortalDesignLlmClientTest,PortalDesignServiceTest test`。
- Maven 测试进程未启动。

### Suggested Fix
在 PowerShell 中将整个参数写为 `"-Dtest=PortalDesignLlmClientTest,PortalDesignServiceTest"`。

### Metadata
- Reproducible: yes
- Related Files: pom.xml

### Resolution
- **Resolved**: 2026-07-27T17:16:00+08:00
- **Commit/PR**: pending
- **Notes**: 已改为带引号参数重跑。

---

## [ERR-20260727-017] portal_designer_dynamic_field_types

**Logged**: 2026-07-27T17:05:00+08:00
**Priority**: medium
**Status**: in_progress
**Area**: frontend

### Summary
门户设计器首次类型检查发现动态属性字段和 Element Plus 树节点签名不兼容。

### Error
```
TS7053: advanced property key cannot index LayoutNode union
TS2322: Element Plus tree Node is not assignable to StructureTreeHandle
TS2322: dynamic component property v-model includes incompatible boolean/string/number unions
```

### Context
- 新增 Schema 属性表单、结构树拖拽和高级 JSON 编辑后执行 `npm run typecheck`。
- Vue 模板不能自动根据运行时字段 Schema 收窄联合类型。

### Suggested Fix
使用显式的属性读写函数和 Element Plus `Node` 类型适配，不在模板中直接把联合值绑定到不同控件。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/views/lowcode/PortalLowCodeDesignerView.vue

---

## [ERR-20260727-014] npm_argument_forwarding

**Logged**: 2026-07-27T16:12:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
通过 npm script 传递 Playwright `--grep` 时参数被 npm 解析，目标测试没有执行。

### Error
```
Error: No tests found.
npm warn "门户设计中心加载站点 V3 草稿" is being parsed as a normal command line argument.
npm warn Unknown cli config "--grep".
```

### Context
- 执行了 `npm run test:e2e -- --grep "门户设计中心加载站点 V3 草稿"`。
- 当前 npm 版本没有按预期把 `--grep` 继续传给 Playwright。

### Suggested Fix
定向运行 Playwright 用例时直接执行 `npx playwright test --grep "..."`，避免 npm script 参数转发差异。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/package.json, kma-admin-web/tests/e2e/frontend-modules-cms.spec.ts

### Resolution
- **Resolved**: 2026-07-27T16:12:00+08:00
- **Commit/PR**: 52587fb
- **Notes**: 后续验证改用 Playwright CLI 直接执行。

---

## [ERR-20260727-016] github_remote_sha_tls_transient

**Logged**: 2026-07-27T16:18:00+08:00
**Priority**: low
**Status**: resolved
**Area**: tooling

### Summary
成功推送后使用 `git ls-remote` 二次查询远端 SHA 时，GitHub TLS 连接瞬时失败并触发命令超时。

### Error
```
fatal: unable to access 'https://github.com/alexfengfeng26/KMA-Mini.git/':
OpenSSL SSL_connect: SSL_ERROR_SYSCALL in connection to github.com:443
```

### Context
- 前一步 `git push origin main` 已明确返回 `main -> main` 成功。
- 本地 HEAD 为 `184a55edc93dec7ec7e73a18594afa04158b391c`。

### Suggested Fix
网络恢复后重试只读远端查询；不要因瞬时 TLS 错误重复改写提交历史。

### Metadata
- Reproducible: no
- Related Files: none

### Resolution
- **Resolved**: 2026-07-27T16:18:00+08:00
- **Commit/PR**: pending
- **Notes**: 使用本地远端跟踪分支核对已推送提交，随后重试推送记录文件。

---

## [ERR-20260727-015] designer_page_switch_changes_left_mode

**Logged**: 2026-07-27T16:12:00+08:00
**Priority**: low
**Status**: resolved
**Area**: frontend

### Summary
实机自动化连续点击两个门户页面时，第一次选页会自动将左侧面板切换为“图层”，导致第二个页面按钮不可见。

### Error
```
No element matched selector
waiting for getByRole('button', { name: 'AI 问答 ask' })
```

### Context
- `selectPage` 的预期交互会在选中页面后进入图层模式。
- 资料中心点击成功，失败发生在未切回页面列表就继续点击 AI 问答。

### Suggested Fix
验证多个页面时，每次选页后先点击可见的“页面”分段标签，再定位下一个页面按钮。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/views/lowcode/PortalLowCodeDesignerView.vue

### Resolution
- **Resolved**: 2026-07-27T16:12:00+08:00
- **Commit/PR**: pending
- **Notes**: 实机验证流程改为逐页切回页面列表。

---

## [ERR-20260727-014] playwright_accessible_name_spacing

**Logged**: 2026-07-27T16:07:00+08:00
**Priority**: low
**Status**: resolved
**Area**: tests

### Summary
CMS 定向浏览器测试错误地假设按钮无障碍名称中没有空格，导致元素定位超时。

### Error
```
locator.click: Test timeout of 30000ms exceeded
waiting for getByRole('button', { name: /资料中心library/ })
```

### Context
- 实际无障碍树中的按钮名称为 `资料中心 library`。
- 页面节点和按钮均已正常渲染，失败仅来自测试选择器。

### Suggested Fix
优先使用实际可访问名称的精确匹配，避免依赖浏览器文本节点的拼接细节。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/tests/e2e/frontend-modules-cms.spec.ts

### Resolution
- **Resolved**: 2026-07-27T16:07:00+08:00
- **Commit/PR**: pending
- **Notes**: 定位器改为精确名称 `资料中心 library`。

---

## [ERR-20260727-002] v22_clean_install_admin_role

**Logged**: 2026-07-27T13:08:00+08:00
**Priority**: high
**Status**: resolved
**Area**: database

### Summary
V22 initially assumed `kma-admin` already existed. A clean V1–V21 database only had `tenant-admin`, so the ACL rewrite was rejected by the principal validation trigger.

### Resolution
V22 now creates `kma-admin` from the legacy administrator when absent, merges permissions and user assignments, then rewrites ACLs. Both clean V1–V22 and backup V21→V22 paths pass.

---

## [ERR-20260727-003] windows_running_jar_lock

**Logged**: 2026-07-27T13:41:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: build

### Summary
Spring Boot repackage could not replace the application JAR while Windows was running that same file.

### Resolution
Stop only the verified 8090 backend process before packaging, then restart it from the rebuilt JAR and repeat readiness, login, and database-connection checks.

---

## [ERR-20260727-004] prettier_check_after_vue_edit

**Logged**: 2026-07-27T15:05:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: frontend

### Summary
新增站点失败页逻辑后，Vue 文件未先执行 Prettier，导致完整质量检查在首个格式门禁处停止。

### Error
```
[warn] src/views/FeatureUnavailableView.vue
[warn] Code style issues found in the above file. Run Prettier with --write to fix.
```

### Context
- 执行 `npm run format:check`。
- 修改文件为 `kma-admin-web/src/views/FeatureUnavailableView.vue`。

### Suggested Fix
编辑 Vue/TypeScript 文件后先对变更文件执行项目 Prettier，再启动完整质量流水线。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/views/FeatureUnavailableView.vue

### Resolution
- **Resolved**: 2026-07-27T15:06:00+08:00
- **Commit/PR**: pending
- **Notes**: 使用仓库固定版本的 Prettier 格式化变更文件并重新执行全部检查。

---

## [ERR-20260727-005] github_tls_push

**Logged**: 2026-07-27T15:12:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: infra

### Summary
本地提交成功后，首次 GitHub 推送在 TLS 握手阶段失败。

### Error
```
fatal: unable to access 'https://github.com/alexfengfeng26/KMA-Mini.git/':
OpenSSL SSL_connect: SSL_ERROR_SYSCALL in connection to github.com:443
```

### Context
- 执行 `git push origin main`。
- 本地提交 `6124898` 已成功创建，失败发生在连接 GitHub 期间。

### Suggested Fix
先用只读远端查询确认网络恢复，再重试相同分支推送；不要重建或改写已成功的本地提交。

### Metadata
- Reproducible: unknown
- Related Files: .git/config

### Resolution
- **Resolved**: 2026-07-27T15:13:00+08:00
- **Commit/PR**: pending
- **Notes**: `git ls-remote` 随后成功，确认是瞬时 TLS 故障；保留原提交并重试推送。

---

## [ERR-20260727-006] browser_networkidle_unsupported

**Logged**: 2026-07-27T15:23:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
浏览器控制环境声明了 `networkidle`，但实际本地页面检查不支持该等待状态。

### Error
```
playwright_wait_for_load_state does not support networkidle
```

### Context
- 导航到 `http://localhost:27183/p/default/home` 后等待网络空闲。
- 页面导航已完成，失败仅发生在后续等待调用。

### Suggested Fix
本环境检查 SPA 时使用 `domcontentloaded` 或直接等待明确的页面元素，不依赖 `networkidle`。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/tests/e2e

### Resolution
- **Resolved**: 2026-07-27T15:23:00+08:00
- **Commit/PR**: pending
- **Notes**: 改用 DOM 快照和目标元素状态继续诊断。

---

## [ERR-20260727-007] browser_failed_cell_binding

**Logged**: 2026-07-27T15:24:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
浏览器调用中途失败后，失败点之后声明的临时变量不可复用。

### Error
```
homeSnapshot is not defined
```

### Context
- 前一调用在等待状态处抛错。
- 随后直接复用了未执行到声明位置的页面快照变量。

### Suggested Fix
浏览器调用异常后只复用确认已经初始化的绑定，其余结果变量重新使用 `var` 声明。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/tests/e2e

### Resolution
- **Resolved**: 2026-07-27T15:24:00+08:00
- **Commit/PR**: pending
- **Notes**: 保留已成功绑定的标签页，重新声明快照与日志变量。

---

## [ERR-20260727-008] windows_tcp_process_lookup_timeout

**Logged**: 2026-07-27T15:26:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: infra

### Summary
数据库活动连接查询成功后，Windows `Get-NetTCPConnection` 进程定位超过命令时限。

### Error
```
command timed out after 20040 milliseconds
```

### Context
- 同一命令先成功确认 5 个 Mini JDBC 连接全部位于 `kma_mini`。
- 超时发生在随后枚举 8090 监听进程的步骤。

### Suggested Fix
Windows 本地端口快速诊断优先使用 `netstat -ano` 过滤目标端口，再按 PID 查询进程。

### Metadata
- Reproducible: unknown
- Related Files: logs/backend-kma-mini.out.log

### Resolution
- **Resolved**: 2026-07-27T15:26:00+08:00
- **Commit/PR**: pending
- **Notes**: 保留已取得的数据库证据，改用 `netstat` 进行后续进程定位。

---

## [ERR-20260727-009] powershell_rg_wildcard_path

**Logged**: 2026-07-27T15:27:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
在 PowerShell 中把含 `*.java` 的路径直接传给 `rg`，Windows 将其作为非法路径处理。

### Error
```
rg: src\main\java\com\kma\knowledge\controller\*.java:
文件名、目录名或卷标语法不正确。 (os error 123)
```

### Context
- 搜索控制器中的 bootstrap 映射。
- 同一命令中的精确文件查询已经返回所需结果。

### Suggested Fix
使用目录参数配合 `-g "*.java"`，不要把 Windows 通配符写进 `rg` 路径参数。

### Metadata
- Reproducible: yes
- Related Files: src/main/java/com/kma/knowledge/controller

### Resolution
- **Resolved**: 2026-07-27T15:27:00+08:00
- **Commit/PR**: pending
- **Notes**: 后续搜索统一使用目录加 `-g` 过滤器。

---

## [ERR-20260727-010] rg_missing_test_directory

**Logged**: 2026-07-27T15:39:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
从项目根目录搜索审核动作时附带了不存在的 `tests` 目录，导致 `rg` 返回退出码 1。

### Error
```
rg: tests: 系统找不到指定的文件。 (os error 2)
```

### Context
- 项目后端测试位于 `src/test`，前端测试位于 `kma-admin-web/tests`。
- 其他搜索目标已正常返回审核权限和旧版设计器实现。

### Suggested Fix
搜索前使用 `rg --files` 确认测试目录，或明确传入 `src/test` 与 `kma-admin-web/tests`。

### Metadata
- Reproducible: yes
- Related Files: src/test, kma-admin-web/tests

### Resolution
- **Resolved**: 2026-07-27T15:39:00+08:00
- **Commit/PR**: pending
- **Notes**: 后续搜索改用实际存在的测试目录。

---

## [ERR-20260727-011] rg_optional_no_match

**Logged**: 2026-07-27T15:41:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
组合诊断命令的最后一个可选 `rg` 在 V22 中没有匹配项，导致整条命令以退出码 1 结束。

### Error
```
Process exited with code 1 because the optional V22 permission search had no matches.
```

### Context
- 前面的设计器、认证权限和 E2E 文件读取均已成功。
- V22 不重复写入 V19 已存在的门户权限字符串属于正常情况。

### Suggested Fix
可选残留/匹配审计应显式接受 `rg` 的退出码 1，或与必须成功的读取命令分开执行。

### Metadata
- Reproducible: yes
- Related Files: src/main/resources/db/migration/V22__single_tenant_cleanup.sql

### Resolution
- **Resolved**: 2026-07-27T15:41:00+08:00
- **Commit/PR**: pending
- **Notes**: 已将“无匹配”解释为正常结果，后续不与必需查询串联。

---

## [ERR-20260727-012] element_segmented_hidden_radio

**Logged**: 2026-07-27T15:51:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: frontend

### Summary
浏览器自动化按 radio 角色点击 Element Plus 分段控件时命中隐藏 input，等待可操作状态超时。

### Error
```
Playwright selector deadline exceeded
waiting on click for selector internal:role=radio[name="页面"s]
```

### Context
- 真实门户设计中心需要从“图层”切换回“页面”。
- DOM 中 radio 唯一但实际可点击目标是其可见 label。

### Suggested Fix
Element Plus segmented 控件使用可见标签或关联 label 点击，并在失败后重新获取 DOM 快照。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/views/lowcode/PortalLowCodeDesignerView.vue

### Resolution
- **Resolved**: 2026-07-27T15:51:00+08:00
- **Commit/PR**: pending
- **Notes**: 改用快照中可见的分段标签继续验证。

---

## [ERR-20260727-013] portal_frame_wrong_path

**Logged**: 2026-07-27T15:54:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: frontend

### Summary
读取系统页面框架时误判其位于 `views/portal`，实际文件不在该路径。

### Error
```
Cannot find path 'kma-admin-web/src/views/portal/PortalSystemPageFrame.vue'
because it does not exist.
```

### Context
- 同一诊断已成功证明资料中心含根节点和 `content-results` 核心组件。
- 错误只影响补充源码读取。

### Suggested Fix
未知组件路径先使用 `rg --files | rg "PortalSystemPageFrame"` 定位，再读取精确文件。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src

### Resolution
- **Resolved**: 2026-07-27T15:54:00+08:00
- **Commit/PR**: pending
- **Notes**: 后续使用文件索引定位系统页面框架。

---
## [ERR-20260728-014] postgres_client_not_on_path

**Logged**: 2026-07-28T00:00:00+08:00
**Priority**: low
**Status**: resolved
**Area**: infra

### Summary
本机 PostgreSQL 18 客户端已安装，但 `psql.exe` 不在 PowerShell PATH。

### Error
```
The term 'psql' is not recognized as a name of a cmdlet, function, script file, or executable program.
```

### Context
- 使用 `psql -h localhost -U postgres -d kma_mini` 核对主题 manifest。
- 客户端实际位于 `C:\Program Files\PostgreSQL\18\bin\psql.exe`。

### Suggested Fix
Windows 本机数据库诊断直接使用 PostgreSQL 18 客户端绝对路径。

### Metadata
- Reproducible: yes
- Related Files: .learnings/ERRORS.md

### Resolution
- **Resolved**: 2026-07-28T00:00:00+08:00
- **Commit/PR**: pending
- **Notes**: 已定位 PostgreSQL 18 客户端绝对路径。

---
## [ERR-20260728-015] optional_runtime_type_spread

**Logged**: 2026-07-28T00:00:00+08:00
**Priority**: low
**Status**: resolved
**Area**: frontend

### Summary
对可选 `themeRuntime` 类型直接展开导致必填字段被推断为可选。

### Error
```
Type 'number | undefined' is not assignable to type 'number'.
Property 'manifest' does not exist on type 'PortalThemeRuntime | undefined'.
```

### Context
- 为历史 JDBC JSON 包装结构增加前端兼容解析。
- 使用了 `PortalBootstrap['themeRuntime']` 这一包含 `undefined` 的索引类型。

### Suggested Fix
先用 `NonNullable<PortalBootstrap['themeRuntime']>` 建立局部确定类型，再展开并索引 manifest。

### Metadata
- Reproducible: yes
- Related Files: kma-admin-web/src/api/portalSites.ts

### Resolution
- **Resolved**: 2026-07-28T00:00:00+08:00
- **Commit/PR**: pending
- **Notes**: 已改为局部 `ThemeRuntime = NonNullable<...>`。

---
