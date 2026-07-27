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
