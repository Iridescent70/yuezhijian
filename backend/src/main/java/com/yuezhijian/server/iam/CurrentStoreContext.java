package com.yuezhijian.server.iam;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentStoreContext {
    static final String SESSION_ATTRIBUTE = CurrentStoreContext.class.getName() + ".storeId";

    private final AccessCatalogService accessCatalog;

    public CurrentStoreContext(AccessCatalogService accessCatalog) {
        this.accessCatalog = accessCatalog;
    }

    public List<StoreSummary> availableStores(Authentication authentication) {
        List<StoreSummary> activeStores = accessCatalog.stores();
        if (hasHeadquartersScope(authentication)) return activeStores;

        Long primaryStoreId = accessCatalog.userIdentity(authentication.getName()).currentStoreId();
        return activeStores.stream()
                .filter(store -> Objects.equals(store.id(), primaryStoreId))
                .toList();
    }

    public StoreSummary currentStore(Authentication authentication, HttpSession session) {
        List<StoreSummary> stores = availableStores(authentication);
        if (stores.isEmpty()) throw new IllegalStateException("当前账号没有可用门店");

        Object selectedValue = session.getAttribute(SESSION_ATTRIBUTE);
        StoreSummary selected = selectedValue instanceof Long selectedId
                ? find(stores, selectedId)
                : null;
        if (selected == null) {
            Long primaryStoreId = accessCatalog.userIdentity(authentication.getName()).currentStoreId();
            selected = find(stores, primaryStoreId);
        }
        if (selected == null) selected = stores.getFirst();
        session.setAttribute(SESSION_ATTRIBUTE, selected.id());
        return selected;
    }

    public StoreSummary switchTo(Authentication authentication, HttpSession session, long storeId) {
        StoreSummary store = find(availableStores(authentication), storeId);
        if (store == null) throw new IllegalArgumentException("无权切换到该门店或门店已停用");
        session.setAttribute(SESSION_ATTRIBUTE, store.id());
        return store;
    }

    private static StoreSummary find(List<StoreSummary> stores, Long storeId) {
        if (storeId == null) return null;
        return stores.stream().filter(store -> Objects.equals(store.id(), storeId)).findFirst().orElse(null);
    }

    private static boolean hasHeadquartersScope(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + AccessCatalogService.ROLE_ADMIN).equals(authority.getAuthority()));
    }
}
