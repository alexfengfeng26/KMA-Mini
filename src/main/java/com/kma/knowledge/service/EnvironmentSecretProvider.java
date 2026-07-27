package com.kma.knowledge.service;

import com.kma.common.exception.KmaException;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
@Primary
public class EnvironmentSecretProvider implements SecretProvider {
    private static final Pattern ENV_NAME = Pattern.compile("[A-Z][A-Z0-9_]{1,127}");

    @Override
    public String resolve(String alias) {
        if (!StringUtils.hasText(alias)) {
            return null;
        }
        String envName = alias.startsWith("env:") ? alias.substring(4) : alias;
        if (!ENV_NAME.matcher(envName).matches()) {
            throw new KmaException(400, "密钥别名必须是环境变量名或 env:环境变量名");
        }
        String secret = System.getenv(envName);
        if (!StringUtils.hasText(secret)) {
            throw new KmaException(503, "模型密钥别名未配置: " + alias);
        }
        return secret;
    }
}
