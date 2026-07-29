package com.yuezhijian.server.iam;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final AccessCatalogService accessCatalogService;

    public CurrentUserService(AccessCatalogService accessCatalogService) {
        this.accessCatalogService = accessCatalogService;
    }

    public CurrentUser from(Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        List<String> roles = authorities.stream().filter(value -> value.startsWith("ROLE_"))
                .map(value -> value.substring(5)).toList();
        List<String> permissions = authorities.stream().filter(value -> !value.startsWith("ROLE_")).toList();
        UserIdentity identity = accessCatalogService.userIdentity(authentication.getName());
        List<StoreSummary> stores = accessCatalogService.stores();
        StoreSummary currentStore = stores.stream()
                .filter(store -> java.util.Objects.equals(store.id(), identity.currentStoreId()))
                .findFirst()
                .orElseGet(stores::getFirst);
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
}
