package com.yuezhijian.server.notification;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationNumberGenerator {
    public String next() {
        return "NTF" + UUID.randomUUID().toString().replace("-", "");
    }
}
