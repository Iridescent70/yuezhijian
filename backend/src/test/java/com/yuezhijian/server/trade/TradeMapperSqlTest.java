package com.yuezhijian.server.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class TradeMapperSqlTest {
    @Test
    void billSearchQueryCanBeParsedWithAllFilters() throws Exception {
        BillQuery query = new BillQuery(
                2L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7), "SETTLED", "B2026");
        Method method = TradeMapper.class.getMethod(
                "search", BillQuery.class, LocalDateTime.class, LocalDateTime.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        parameters.put("from", query.startDate().atStartOfDay());
        parameters.put("until", query.endDate().plusDays(1).atStartOfDay());

        BoundSql sql = source.getBoundSql(parameters);

        assertThat(sql.getSql()).contains("bill.status = ?");
        assertThat(sql.getSql()).contains("bill.bill_no LIKE CONCAT('%', ?, '%')");
        assertThat(sql.getParameterMappings()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void settlementSqlPersistsAssetTotalsAndIdempotencyKey() throws Exception {
        Method insertQuote = TradeMapper.class.getMethod("insertQuote", SettlementQuoteDraft.class);
        Method settleBill = TradeMapper.class.getMethod(
                "settleBill", long.class, String.class, java.math.BigDecimal.class,
                String.class, long.class);

        String quoteSql = String.join(" ", insertQuote.getAnnotation(Select.class).value());
        String settleSql = String.join(" ", settleBill.getAnnotation(Update.class).value());

        assertThat(quoteSql).contains("asset_amount", "external_payment_amount", "#{assetAmount}");
        assertThat(settleSql).contains("settlement_idempotency_key = #{idempotencyKey}");
    }

    @Test
    void billMaintenanceSqlUsesSoftDeleteAndVersionedDiscountTotals() throws Exception {
        Method findLines = TradeMapper.class.getMethod("findLines", long.class);
        Method removeLine = TradeMapper.class.getMethod("removeLine", long.class, long.class, long.class);
        Method updateTotals = TradeMapper.class.getMethod(
                "updateDiscountTotals", BillDiscountDraft.class, java.math.BigDecimal.class);

        String findSql = String.join(" ", findLines.getAnnotation(Select.class).value());
        String removeSql = String.join(" ", removeLine.getAnnotation(Update.class).value());
        String totalsSql = String.join(" ", updateTotals.getAnnotation(Update.class).value());

        assertThat(findSql).contains("line.line_status = 'ACTIVE'");
        assertThat(removeSql).contains("line_status = 'REMOVED'", "removed_by = #{operatorId}");
        assertThat(totalsSql).contains(
                "discount_amount = #{draft.discountAmount}",
                "row_version = CONVERT(binary(8), #{draft.version}, 1)");
    }

    @Test
    void billVoidRequiresAnActiveConfiguredReason() throws Exception {
        Method method = TradeMapper.class.getMethod(
                "voidBill", long.class, String.class, String.class, long.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertThat(sql).contains(
                "business_type = 'BILL'",
                "reason.reason_code = #{reasonCode}",
                "reason.status = 'ACTIVE'");
    }
}
