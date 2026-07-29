package com.yuezhijian.server.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CurrentStoreContextTest {
    private final CurrentStoreContext context = new CurrentStoreContext(new MemoryAccessCatalogService());

    @Test
    void storeRoleCannotSelectAnotherStore() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "store-manager", "ignored", List.of(new SimpleGrantedAuthority("ROLE_STORE_MANAGER")));
        var session = new MockHttpSession();

        assertThat(context.availableStores(authentication)).extracting(StoreSummary::id).containsExactly(1L);
        assertThat(context.currentStore(authentication, session).id()).isEqualTo(1L);
        assertThatThrownBy(() -> context.switchTo(authentication, session, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权切换");
    }
}
