package com.yuezhijian.server;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.profiles.active=memory",
        "app.bootstrap.username=test-admin",
        "app.bootstrap.password=TestPassword!2026"
})
@AutoConfigureMockMvc
class CardFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void cardTypeCanBeCreatedWithStoreAndServiceRules() throws Exception {
        MockHttpSession session = login();
        mockMvc.perform(post("/api/v1/card-types").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "code":"STYLE_NAIL_12","name":"款式美甲12次卡",
                                  "salePrice":2680,"listPrice":3576,"totalTimes":12,"validDays":365,
                                  "purchaseThreshold":0,"autoRemindDays":30,"storeIds":[2],
                                  "serviceRules":[
                                    {"serviceId":302,"includedTimes":12,"deductTimes":1,"priority":10}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("STYLE_NAIL_12"))
                .andExpect(jsonPath("$.data.storeIds[0]").value(2))
                .andExpect(jsonPath("$.data.serviceRules[0].serviceName").value("精致款式美甲"));

        mockMvc.perform(get("/api/v1/card-types").param("storeId", "2")
                        .param("keyword", "STYLE_NAIL_12").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void cardTypeRejectsMismatchedTotalAndDuplicateService() throws Exception {
        MockHttpSession session = login();
        mockMvc.perform(post("/api/v1/card-types").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "code":"INVALID_CARD","name":"错误次卡","salePrice":100,"listPrice":100,
                                  "totalTimes":10,"validDays":30,"autoRemindDays":5,"storeIds":[2],
                                  "serviceRules":[{"serviceId":301,"includedTimes":9,"deductTimes":1,"priority":1}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("各项目包含次数合计必须等于次卡总次数"));
    }

    @Test
    void purchaseCreatesCardsBalancesAndImmutablePurchaseLedgersIdempotently() throws Exception {
        MockHttpSession session = login();
        long memberId = createMember(session);
        String request = """
                {"cardTypeId":501,"quantity":2,"storeId":2,"paymentMethodId":1,
                 "salesEmployeeId":101,"idempotencyKey":"card-sale-test-1"}
                """;
        JsonNode first = json(postJson(session, "/api/v1/members/" + memberId + "/cards", request, 201)).path("data");
        JsonNode repeated = json(postJson(session, "/api/v1/members/" + memberId + "/cards", request, 201)).path("data");
        org.assertj.core.api.Assertions.assertThat(repeated.path("orderId").asLong())
                .isEqualTo(first.path("orderId").asLong());

        mockMvc.perform(get("/api/v1/members/{id}/cards", memberId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].remainingTimes").value(10));
        long cardId = first.path("cards").get(0).path("id").asLong();
        mockMvc.perform(get("/api/v1/member-cards/{id}", cardId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balances", hasSize(1)))
                .andExpect(jsonPath("$.data.balances[0].serviceName").value("基础单色美甲"))
                .andExpect(jsonPath("$.data.ledgers", hasSize(1)))
                .andExpect(jsonPath("$.data.ledgers[0].transactionType").value("PURCHASE"));
    }

    private long createMember(MockHttpSession session) throws Exception {
        String response = postJson(session, "/api/v1/members", """
                {"fullName":"次卡测试会员","mobile":"13600003001","joinStoreId":2,"ownerStoreId":2}
                """, 201);
        return json(response).path("data").path("memberId").asLong();
    }

    private String postJson(MockHttpSession session, String url, String content, int code) throws Exception {
        return mockMvc.perform(post(url).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().is(code)).andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String value) throws Exception { return objectMapper.readTree(value); }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"TestPassword!2026\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
