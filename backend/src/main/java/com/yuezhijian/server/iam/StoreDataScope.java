package com.yuezhijian.server.iam;

import jakarta.servlet.http.HttpSession;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class StoreDataScope {
    private final CurrentStoreContext currentStoreContext;

    public StoreDataScope(CurrentStoreContext currentStoreContext) {
        this.currentStoreContext = currentStoreContext;
    }

    public Long constrainNullable(Long requestedStoreId) {
        Authentication authentication = authentication();
        if (requestedStoreId != null) {
            require(authentication, requestedStoreId);
            return requestedStoreId;
        }
        if (currentStoreContext.hasAllStoreAccess(authentication)) return null;
        return currentStoreContext.availableStores(authentication).stream()
                .findFirst()
                .map(StoreSummary::id)
                .orElseThrow(() -> new StoreAccessDeniedException("当前账号没有可用门店"));
    }

    public long resolveRequired(Long requestedStoreId) {
        Authentication authentication = authentication();
        if (requestedStoreId != null) {
            require(authentication, requestedStoreId);
            return requestedStoreId;
        }
        return currentStoreContext.currentStore(authentication, currentSession()).id();
    }

    public void require(long storeId) {
        require(authentication(), storeId);
    }

    public void requireAny(Collection<Long> storeIds) {
        if (storeIds == null || storeIds.stream().noneMatch(this::canAccess)) {
            throw new StoreAccessDeniedException("没有该业务数据的门店权限");
        }
    }

    public boolean canAccess(long storeId) {
        if (!currentStoreContext.isActiveStore(storeId)) return false;
        Authentication authentication = authentication();
        return currentStoreContext.availableStores(authentication).stream()
                .anyMatch(store -> Objects.equals(store.id(), storeId));
    }

    public void requireAllStoreAccess() {
        if (!currentStoreContext.hasAllStoreAccess(authentication())) {
            throw new StoreAccessDeniedException("该操作需要全部门店的数据权限");
        }
    }

    private void require(Authentication authentication, long storeId) {
        if (!currentStoreContext.isActiveStore(storeId)) {
            throw new IllegalArgumentException("所选门店不存在或已停用");
        }
        boolean allowed = currentStoreContext.availableStores(authentication).stream()
                .anyMatch(store -> Objects.equals(store.id(), storeId));
        if (!allowed) throw new StoreAccessDeniedException("没有该门店的数据权限");
    }

    private Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new StoreAccessDeniedException("当前请求缺少有效登录身份");
        }
        return authentication;
    }

    private HttpSession currentSession() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getSession();
        }
        throw new StoreAccessDeniedException("当前请求缺少有效会话");
    }
}
