package com.yuezhijian.server.iam;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final AccessCatalogService accessCatalogService;
    private final CurrentStoreContext currentStoreContext;

    public CurrentUserService(
            AccessCatalogService accessCatalogService,
            CurrentStoreContext currentStoreContext) {
        this.accessCatalogService = accessCatalogService;
        this.currentStoreContext = currentStoreContext;
    }

    public CurrentUser from(Authentication authentication, HttpSession session) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        List<String> roles = authorities.stream().filter(value -> value.startsWith("ROLE_"))
                .map(value -> value.substring(5)).toList();
        List<String> permissions = authorities.stream().filter(value -> !value.startsWith("ROLE_")).toList();
        UserIdentity identity = accessCatalogService.userIdentity(authentication.getName());
        List<StoreSummary> stores = currentStoreContext.availableStores(authentication);
        StoreSummary currentStore = currentStoreContext.currentStore(authentication, session);
        return new CurrentUser(
                identity.id(),
                identity.username(),
                identity.fullName(),
                currentStore.id(),
                currentStore.name(),
                roles,
                permissions,
                stores,
                accessCatalogService.menusForPermissions(permissions));
    }

    public CurrentUser switchStore(Authentication authentication, HttpSession session, long storeId) {
        currentStoreContext.switchTo(authentication, session, storeId);
        return from(authentication, session);
    }
}
