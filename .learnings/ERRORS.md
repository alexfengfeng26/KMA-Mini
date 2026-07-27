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
