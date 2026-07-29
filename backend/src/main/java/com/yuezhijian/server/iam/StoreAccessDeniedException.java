package com.yuezhijian.server.iam;

import org.springframework.security.access.AccessDeniedException;

public class StoreAccessDeniedException extends AccessDeniedException {
    public StoreAccessDeniedException(String message) {
        super(message);
    }
}
