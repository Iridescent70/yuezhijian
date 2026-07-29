package com.yuezhijian.server;

import com.yuezhijian.server.common.DataProtectionProperties;
import com.yuezhijian.server.iam.BootstrapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({BootstrapProperties.class, DataProtectionProperties.class})
public class YuezhijianApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuezhijianApplication.class, args);
    }
}
