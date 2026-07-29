package com.yuezhijian.server;

import com.yuezhijian.server.common.DataProtectionProperties;
import com.yuezhijian.server.file.FileStorageProperties;
import com.yuezhijian.server.iam.BootstrapProperties;
import com.yuezhijian.server.job.AsyncJobProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        BootstrapProperties.class,
        DataProtectionProperties.class,
        FileStorageProperties.class,
        AsyncJobProperties.class
})
public class YuezhijianApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuezhijianApplication.class, args);
    }
}
