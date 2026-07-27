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
