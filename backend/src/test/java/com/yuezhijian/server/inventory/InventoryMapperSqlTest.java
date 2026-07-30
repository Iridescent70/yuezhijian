package com.yuezhijian.server.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryMapperSqlTest {
    private Configuration configuration;

    @BeforeEach
    void setUp() {
        configuration = new Configuration();
        configuration.addMapper(InventoryMapper.class);
        new MapperBuilderAssistant(configuration, InventoryMapper.class.getName());
    }

    @Test
    void mapperBoundaryTypesArePublicForJdkProxyAccess() {
        assertThat(InventoryMapper.class.getDeclaredMethods())
                .flatExtracting(method -> {
                    var types = new java.util.ArrayList<Class<?>>();
                    types.add(method.getReturnType());
                    types.addAll(List.of(method.getParameterTypes()));
                    return types;
                })
                .filteredOn(type -> type.getPackageName().equals(InventoryMapper.class.getPackageName()))
                .allMatch(type -> Modifier.isPublic(type.getModifiers()));
    }

    @Test
    void stockAndTransferQueriesKeepStoreScopePaginationAndSafeParameters() {
        BoundSql stock = sql("findStocks", Map.of(
                "storeId", 2L, "keyword", "礼品", "lowStock", true, "offset", 20, "size", 20));
        assertThat(normalize(stock.getSql()))
                .contains("store.id = ?", "gift.gift_code LIKE", "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")
                .doesNotContain("礼品");

        BoundSql transfer = sql("findTransfers", Map.of(
                "storeId", 2L, "keyword", "TRF", "status", "DRAFT", "offset", 0, "size", 20));
        assertThat(normalize(transfer.getSql()))
                .contains("transfer.source_store_id = ? OR transfer.target_store_id = ?")
                .contains("transfer.status = ?", "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
    }

    @Test
    void stockMutationUsesRangeLocksVersionChecksAndLedgerEquationFields() {
        assertThat(normalize(sql("ensureStock", Map.of("storeId", 2L, "giftId", 501L, "operatorId", 1L)).getSql()))
                .contains("WITH (UPDLOCK, HOLDLOCK)", "WHERE store_id = ? AND gift_id = ?");
        assertThat(normalize(sql("lockStock", Map.of("storeId", 2L, "giftId", 501L)).getSql()))
                .contains("WITH (UPDLOCK, HOLDLOCK)");
        assertThat(normalize(sql("updateStock", Map.of(
                "id", 1L, "afterQuantity", 4, "occurredAt", java.time.LocalDateTime.now(),
                "operatorId", 1L, "rowVersion", new byte[8])).getSql()))
                .contains("row_version = ?", "AND ? >= 0");
        assertThat(normalize(sql("insertStockLedger", Map.ofEntries(
                Map.entry("ledgerNo", "INV1"), Map.entry("storeId", 2L), Map.entry("giftId", 501L),
                Map.entry("transactionType", "COUNT_GAIN"), Map.entry("beforeQuantity", 1),
                Map.entry("changeQuantity", 1), Map.entry("afterQuantity", 2),
                Map.entry("sourceType", "COUNT"), Map.entry("sourceId", 1L),
                Map.entry("sourceLineId", 1L), Map.entry("occurredAt", java.time.LocalDateTime.now()),
                Map.entry("reversedLedgerId", 1L), Map.entry("note", "test"), Map.entry("operatorId", 1L)
        )).getSql())).contains("before_quantity", "change_quantity", "after_quantity");
    }

    @Test
    void countStatusSqlExpandsOnlyBoundExpectedStatuses() {
        BoundSql sql = sql("updateCountStatus", Map.of(
                "id", 1L, "expectedStatuses", List.of("DRAFT", "READY_CONFIRM"),
                "targetStatus", "VOIDED", "reason", "test",
                "occurredAt", java.time.LocalDateTime.now(), "operatorId", 1L, "version", "0x01"));
        assertThat(normalize(sql.getSql()))
                .contains("status IN ( ? , ? )", "row_version = CONVERT(binary(8), ?, 1)")
                .doesNotContain("READY_CONFIRM");
    }

    private BoundSql sql(String method, Object parameter) {
        return configuration.getMappedStatement(InventoryMapper.class.getName() + "." + method)
                .getBoundSql(parameter);
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
