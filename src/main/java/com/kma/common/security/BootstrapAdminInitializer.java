package com.kma.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;
    private final KmaSecurityProperties properties;

    public BootstrapAdminInitializer(@Qualifier("knowledgeJdbcTemplate") JdbcTemplate jdbc,
                                     @Qualifier("knowledgeTransactionTemplate") TransactionTemplate transactionTemplate,
                                     PasswordEncoder passwordEncoder,
                                     KmaSecurityProperties properties) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.localEnabled()) {
            return;
        }
        KmaSecurityProperties.Bootstrap bootstrap = properties.getBootstrap();
        if (bootstrap.getPassword() == null || bootstrap.getPassword().isBlank()) {
            log.warn("本地账号模式已启用，但未设置 KMA_BOOTSTRAP_ADMIN_PASSWORD；不会创建默认管理员");
            return;
        }
        transactionTemplate.executeWithoutResult(status -> initialize(bootstrap));
    }

    private void initialize(KmaSecurityProperties.Bootstrap bootstrap) {
        Long rootOrgId = jdbc.queryForObject("""
            INSERT INTO kma_org(org_code,name,status,built_in,sort_order)
            VALUES ('root','根组织','active',TRUE,0)
            ON CONFLICT (org_code) DO UPDATE SET
                name=EXCLUDED.name,status='active',built_in=TRUE,update_time=now()
            RETURNING org_id
            """, Long.class);
        jdbc.update("""
            INSERT INTO kma_org_closure(ancestor_id,descendant_id,depth)
            VALUES (?,?,0) ON CONFLICT DO NOTHING
            """, rootOrgId, rootOrgId);
        jdbc.update("""
            INSERT INTO kma_role(role_code, name, built_in)
            VALUES ('kma-admin', 'KMA 管理员', true)
            ON CONFLICT (role_code) DO NOTHING
            """);
        Long roleId = jdbc.queryForObject(
            "SELECT role_id FROM kma_role WHERE role_code = 'kma-admin'", Long.class);

        int inserted = jdbc.update("""
            INSERT INTO kma_user(username, display_name, password_hash, identity_provider,
                                 status, must_change_password)
            VALUES (?, ?, ?, 'local', 'active', true)
            ON CONFLICT (username) DO NOTHING
            """, bootstrap.getUsername(), bootstrap.getDisplayName(),
            passwordEncoder.encode(bootstrap.getPassword()));
        Long userId = jdbc.queryForObject(
            "SELECT user_id FROM kma_user WHERE username = ?",
            Long.class, bootstrap.getUsername());
        jdbc.update("""
            INSERT INTO kma_user_role(user_id, role_id) VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """, userId, roleId);
        jdbc.update("""
            INSERT INTO kma_user_org(user_id,org_id,primary_org)
            SELECT ?,?,TRUE
            WHERE NOT EXISTS (
                SELECT 1 FROM kma_user_org WHERE user_id=? AND primary_org
            )
            ON CONFLICT DO NOTHING
            """, userId, rootOrgId, userId);
        jdbc.update("""
            INSERT INTO kma_role_permission(role_id, permission_code)
            SELECT ?, permission_code FROM kma_permission
            ON CONFLICT DO NOTHING
            """, roleId);
        if (inserted > 0) {
            log.info("已创建 KMA 初始化管理员: username={}，首次登录后必须修改密码",
                bootstrap.getUsername());
        }
    }
}
