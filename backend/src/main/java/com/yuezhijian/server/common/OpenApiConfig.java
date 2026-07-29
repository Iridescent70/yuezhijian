package com.yuezhijian.server.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI yuezhijianOpenApi() {
        return new OpenAPI().info(new Info()
                .title("悦指间管理系统 API")
                .version("v1")
                .description("接口编号和完整设计见 plan/API接口.md"));
    }
}
