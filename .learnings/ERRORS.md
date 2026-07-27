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
