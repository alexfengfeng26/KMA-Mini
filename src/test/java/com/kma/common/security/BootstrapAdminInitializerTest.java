package com.kma.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    @SuppressWarnings("unchecked")
    void bootstrapAdminIsAssignedToRootOrganization() {
        KmaSecurityProperties properties = new KmaSecurityProperties();
        properties.getBootstrap().setUsername("admin");
        properties.getBootstrap().setPassword("initial-password-123");
        when(passwordEncoder.encode("initial-password-123")).thenReturn("argon-hash");
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(11L, 3L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("admin"))).thenReturn(7L);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        new BootstrapAdminInitializer(jdbc, transactionTemplate, passwordEncoder, properties)
            .run(mock(ApplicationArguments.class));

        verify(jdbc).update(anyString(), eq(7L), eq(11L), eq(7L));
    }
}
