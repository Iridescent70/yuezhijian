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
        StoreSummary currentStore = accessCatalogService.stores().getFirst();
        return new CurrentUser(
                1L,
                authentication.getName(),
                "本地管理员",
                currentStore.id(),
                currentStore.name(),
                roles,
                permissions,
                accessCatalogService.stores(),
                accessCatalogService.menusForPermissions(permissions));
    }
}
