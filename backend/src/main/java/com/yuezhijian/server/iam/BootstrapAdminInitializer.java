package com.yuezhijian.server.iam;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("sqlserver")
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final BootstrapProperties properties;
    private final AccessCatalogMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            BootstrapProperties properties,
            AccessCatalogMapper mapper,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mapper.countUserByUsername(properties.username()) > 0) {
            return;
        }
        if (properties.password() == null
                || properties.password().length() < 12
                || properties.password().startsWith("Replace-With")) {
            throw new IllegalStateException("APP_BOOTSTRAP_PASSWORD 必须设置为至少12位的非占位密码");
        }
        BootstrapUser user = new BootstrapUser(
                properties.username(), passwordEncoder.encode(properties.password()), properties.fullName());
        mapper.insertBootstrapUser(user);
        if (mapper.assignRole(user.getId(), AccessCatalogService.ROLE_ADMIN) != 1) {
            throw new IllegalStateException("未找到总部管理员角色，无法初始化管理员");
        }
    }
}
