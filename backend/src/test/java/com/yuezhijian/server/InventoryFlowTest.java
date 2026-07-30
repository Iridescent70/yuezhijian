package com.yuezhijian.server;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.profiles.active=memory",
        "app.bootstrap.username=test-admin",
        "app.bootstrap.password=TestPassword!2026"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InventoryFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void giftTransferAndReversalKeepBalancesAndAppendOnlyLedgersConsistent() throws Exception {
        MockHttpSession session = login();
        mockMvc.perform(get("/api/v1/item-categories?type=GIFT").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("POINT_GIFT"));

        JsonNode createdGift = data(postJson(session, "/api/v1/gifts", """
                {
                  "code":"GFT-FLOW-001","name":"自动化礼品","categoryId":3,"unitId":2,
                  "pointPrice":800,"costPrice":42.0000,"lowStockThreshold":3.0,"description":"测试礼品"
                }
                """, 201));
        long giftId = createdGift.path("id").asLong();
        JsonNode updatedGift = data(putJson(session, "/api/v1/gifts/" + giftId, """
                                {
                                  "name":"自动化礼品（更新）","categoryId":3,"unitId":2,
                                  "pointPrice":900,"costPrice":45,"lowStockThreshold":4,
                                  "description":"更新后","status":"ACTIVE","version":"%s"
                                }
                                """.formatted(createdGift.path("version").asText()), 200));
        org.junit.jupiter.api.Assertions.assertEquals(900, updatedGift.path("pointPrice").asInt());
        JsonNode disabledGift = data(putJson(session, "/api/v1/gifts/" + giftId, """
                {
                  "name":"自动化礼品（更新）","categoryId":3,"unitId":2,
                  "pointPrice":900,"costPrice":45,"lowStockThreshold":4,
                  "description":"停用结存测试","status":"DISABLED","version":"%s"
                }
                """.formatted(updatedGift.path("version").asText()), 200));
        postJson(session, "/api/v1/inventory-counts", """
                {
                  "storeId":1,"name":"停用礼品清账","countDate":"%s","giftIds":[%d],
                  "idempotencyKey":"inventory-disabled-gift-count"
                }
                """.formatted(LocalDate.now(), disabledGift.path("id").asLong()), 201);

        JsonNode transfer = data(postJson(session, "/api/v1/inventory-transfers", """
                {
                  "sourceStoreId":2,"targetStoreId":1,"transferDate":"%s",
                  "remarks":"门店调总部","idempotencyKey":"inventory-flow-transfer-001",
                  "lines":[{"giftId":501,"quantity":5.0,"note":"调拨测试"}]
                }
                """.formatted(LocalDate.now()), 201));
        long transferId = transfer.path("id").asLong();
        JsonNode confirmed = data(postJson(session, "/api/v1/inventory-transfers/" + transferId + "/confirm", """
                {"version":"%s","reason":"已复核实物"}
                """.formatted(transfer.path("version").asText()), 200));
        mockMvc.perform(get("/api/v1/inventories?storeId=2&keyword=GFT001").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].onHandQuantity").value(15.0));
        mockMvc.perform(get("/api/v1/inventories?storeId=1&keyword=GFT001").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].onHandQuantity").value(5.0));

        postJson(session, "/api/v1/inventory-transfers/" + transferId + "/confirm", """
                {"version":"stale","reason":"重复请求"}
                """, 200);
        JsonNode reversed = data(postJson(session, "/api/v1/inventory-transfers/" + transferId + "/reverse", """
                {"version":"%s","reason":"调拨错误"}
                """.formatted(confirmed.path("version").asText()), 200));
        org.junit.jupiter.api.Assertions.assertEquals("REVERSED", reversed.path("status").asText());
        mockMvc.perform(get("/api/v1/inventories?storeId=2&keyword=GFT001").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].onHandQuantity").value(20.0));
        mockMvc.perform(get("/api/v1/inventories/2/gifts/501/ledgers").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].transactionType").value("TRANSFER_REVERSAL_IN"))
                .andExpect(jsonPath("$.data.items[1].transactionType").value("TRANSFER_OUT"));
    }

    @Test
    void stockCountAdjustsInventoryAndRejectsAStaleBookSnapshot() throws Exception {
        MockHttpSession session = login();
        JsonNode counting = createCount(session, "inventory-count-001", "月末盘点");
        JsonNode ready = saveCount(session, counting, 18);
        JsonNode confirmed = data(postJson(session,
                "/api/v1/inventory-counts/" + counting.path("id").asLong() + "/confirm", """
                        {"version":"%s","reason":"实物复核完成"}
                        """.formatted(ready.path("version").asText()), 200));
        org.junit.jupiter.api.Assertions.assertEquals("CONFIRMED", confirmed.path("status").asText());
        mockMvc.perform(get("/api/v1/inventories?storeId=2&keyword=GFT001").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].onHandQuantity").value(18.0));

        JsonNode staleCount = createCount(session, "inventory-count-002", "并发盘点");
        JsonNode transfer = data(postJson(session, "/api/v1/inventory-transfers", """
                {
                  "sourceStoreId":2,"targetStoreId":1,"transferDate":"%s",
                  "idempotencyKey":"inventory-count-transfer-002",
                  "lines":[{"giftId":501,"quantity":1}]
                }
                """.formatted(LocalDate.now()), 201));
        postJson(session, "/api/v1/inventory-transfers/" + transfer.path("id").asLong() + "/confirm", """
                {"version":"%s"}
                """.formatted(transfer.path("version").asText()), 200);
        JsonNode staleReady = saveCount(session, staleCount, 17);
        postJson(session, "/api/v1/inventory-counts/" + staleCount.path("id").asLong() + "/confirm", """
                {"version":"%s","reason":"尝试覆盖并发库存"}
                """.formatted(staleReady.path("version").asText()), 409);
    }

    @Test
    void inventoryRejectsDuplicateLinesInsufficientStockAndFutureBusinessDates() throws Exception {
        MockHttpSession session = login();
        postJson(session, "/api/v1/inventory-transfers", """
                {
                  "sourceStoreId":2,"targetStoreId":1,"transferDate":"%s",
                  "idempotencyKey":"inventory-invalid-duplicate",
                  "lines":[{"giftId":501,"quantity":2},{"giftId":501,"quantity":3}]
                }
                """.formatted(LocalDate.now()), 400);
        JsonNode transfer = data(postJson(session, "/api/v1/inventory-transfers", """
                {
                  "sourceStoreId":2,"targetStoreId":1,"transferDate":"%s",
                  "idempotencyKey":"inventory-invalid-insufficient",
                  "lines":[{"giftId":501,"quantity":2000}]
                }
                """.formatted(LocalDate.now()), 201));
        postJson(session, "/api/v1/inventory-transfers/" + transfer.path("id").asLong() + "/confirm", """
                {"version":"%s"}
                """.formatted(transfer.path("version").asText()), 400);
        postJson(session, "/api/v1/inventory-counts", """
                {
                  "storeId":2,"name":"未来盘点","countDate":"%s","giftIds":[501],
                  "idempotencyKey":"inventory-invalid-future"
                }
                """.formatted(LocalDate.now().plusDays(1)), 400);
    }

    private JsonNode createCount(MockHttpSession session, String key, String name) throws Exception {
        return data(postJson(session, "/api/v1/inventory-counts", """
                {
                  "storeId":2,"name":"%s","countDate":"%s","giftIds":[501],
                  "remarks":"全量礼品盘点","idempotencyKey":"%s"
                }
                """.formatted(name, LocalDate.now(), key), 201));
    }

    private JsonNode saveCount(MockHttpSession session, JsonNode counting, int actualQuantity) throws Exception {
        long id = counting.path("id").asLong();
        long lineId = counting.path("lines").get(0).path("id").asLong();
        return data(putJson(session, "/api/v1/inventory-counts/" + id + "/lines", """
                {"version":"%s","lines":[{"lineId":%d,"actualQuantity":%d.0}]}
                """.formatted(counting.path("version").asText(), lineId, actualQuantity), 200));
    }

    private String postJson(MockHttpSession session, String path, String body, int expectedStatus) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private String putJson(MockHttpSession session, String path, String body, int expectedStatus) throws Exception {
        return mockMvc.perform(put(path).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private JsonNode data(String response) throws Exception {
        return objectMapper.readTree(response).path("data");
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"TestPassword!2026\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
