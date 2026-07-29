package com.yuezhijian.server;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "spring.profiles.active=memory",
        "app.bootstrap.username=test-admin",
        "app.bootstrap.password=TestPassword!2026"
})
@AutoConfigureMockMvc
class StoreDataScopeFlowTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void storeRoleIsRestrictedToItsPrimaryStoreAcrossCoreBusinessApis() throws Exception {
        mockMvc.perform(get("/api/v1/members").with(storeManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].ownerStoreId").value(1))
                .andExpect(jsonPath("$.data.items[*].ownerStoreId", everyItem(is(1))));
        mockMvc.perform(get("/api/v1/members/1003").with(storeManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerStoreId").value(1));

        assertForbidden(get("/api/v1/members").param("storeId", "2"));
        assertForbidden(get("/api/v1/members/1001"));
        assertForbidden(get("/api/v1/appointments").param("storeId", "2"));
        assertForbidden(get("/api/v1/bills").param("storeId", "2"));
        assertForbidden(get("/api/v1/payment-methods").param("storeId", "2"));

        mockMvc.perform(post("/api/v1/members")
                        .with(storeManager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"越权测试会员","mobile":"13588889999","joinStoreId":2}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40301"));

        mockMvc.perform(post("/api/v1/appointments")
                        .with(storeManager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId":1001,"storeId":2,"startAt":"2026-08-20T10:00:00",
                                  "personCount":1,"employeeId":101,"workstationId":201,
                                  "serviceIds":[301],"idempotencyKey":"SCOPE-APT-001"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40301"));

        mockMvc.perform(post("/api/v1/bills")
                        .with(storeManager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":1001,"storeId":2,"personCount":1,"idempotencyKey":"SCOPE-BILL-001"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    @Test
    void storeRoleCannotReadAssetsCardsVouchersOrReversalsFromAnotherStore() throws Exception {
        mockMvc.perform(get("/api/v1/members/1003/balance-account").with(storeManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(1003));

        assertForbidden(get("/api/v1/members/1001/balance-account"));
        assertForbidden(get("/api/v1/members/1001/point-ledgers"));
        assertForbidden(get("/api/v1/members/1001/cards"));
        assertForbidden(get("/api/v1/card-types").param("storeId", "2"));
        assertForbidden(get("/api/v1/card-types/501"));
        assertForbidden(get("/api/v1/voucher-codes").param("memberId", "1001"));

        mockMvc.perform(get("/api/v1/reversals").with(storeManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void storeRoleIsRestrictedAcrossOperationsAndConfiguration() throws Exception {
        assertForbidden(get("/api/v1/employees").param("storeId", "2"));
        assertForbidden(get("/api/v1/workstations").param("storeId", "2"));
        assertForbidden(get("/api/v1/services").param("storeId", "2"));
        assertForbidden(get("/api/v1/services/301"));
        assertForbidden(get("/api/v1/commission-ledgers").param("storeId", "2"));
        assertForbidden(get("/api/v1/visit-tasks").param("storeId", "2"));
        assertForbidden(get("/api/v1/service-feedback").param("storeId", "2"));
        assertForbidden(get("/api/v1/ownership-adjustments").param("memberId", "1001"));

        mockMvc.perform(post("/api/v1/employees")
                        .with(storeManager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeNo":"SCOPE-E-001","name":"越权员工","positionId":1,
                                  "primaryStoreId":2,"canService":true,"canSell":true
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40301"));

        mockMvc.perform(post("/api/v1/commission-plans")
                        .with(storeManager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"SCOPE_GLOBAL","name":"越权全局方案","scene":"SERVICE",
                                  "calculationMode":"RATE","rate":0.1,"effectiveFrom":"2026-07-30"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40301"));

        mockMvc.perform(post("/api/v1/members/1003/ownership-adjustments")
                        .with(storeManager()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newStoreId":2,"effectiveDate":"2026-08-01","shareRule":{},
                                  "reason":"越权归属测试","memberVersion":"1"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    private void assertForbidden(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request.with(storeManager()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40301"));
    }

    private RequestPostProcessor storeManager() {
        return user("store-manager").authorities(
                new SimpleGrantedAuthority("ROLE_STORE_MANAGER"),
                new SimpleGrantedAuthority("member:member:view"),
                new SimpleGrantedAuthority("member:member:create"),
                new SimpleGrantedAuthority("appointment:appointment:view"),
                new SimpleGrantedAuthority("appointment:appointment:create"),
                new SimpleGrantedAuthority("trade:bill:view"),
                new SimpleGrantedAuthority("trade:bill:create"),
                new SimpleGrantedAuthority("member:asset:view"),
                new SimpleGrantedAuthority("member:asset:manage"),
                new SimpleGrantedAuthority("member:card:view"),
                new SimpleGrantedAuthority("member:card:manage"),
                new SimpleGrantedAuthority("catalog:card:view"),
                new SimpleGrantedAuthority("benefit:voucher:view"),
                new SimpleGrantedAuthority("trade:reversal:view"),
                new SimpleGrantedAuthority("trade:reversal:manage"),
                new SimpleGrantedAuthority("org:employee:view"),
                new SimpleGrantedAuthority("org:employee:manage"),
                new SimpleGrantedAuthority("org:workstation:view"),
                new SimpleGrantedAuthority("catalog:service:view"),
                new SimpleGrantedAuthority("commission:plan:view"),
                new SimpleGrantedAuthority("commission:plan:manage"),
                new SimpleGrantedAuthority("commission:ledger:view"),
                new SimpleGrantedAuthority("visit:task:view"),
                new SimpleGrantedAuthority("visit:feedback:view"),
                new SimpleGrantedAuthority("member:ownership:view"),
                new SimpleGrantedAuthority("member:ownership:manage"));
    }
}
