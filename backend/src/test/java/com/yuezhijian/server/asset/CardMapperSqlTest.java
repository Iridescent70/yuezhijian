package com.yuezhijian.server.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class CardMapperSqlTest {
    @Test
    void cardTypeSearchCanParseAllFilters() throws Exception {
        Method method = CardMapper.class.getMethod("searchCardTypes", Long.class, String.class, String.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("storeId", 2L);
        parameters.put("keyword", "NAIL");
        parameters.put("status", "ACTIVE");

        BoundSql sql = source.getBoundSql(parameters);

        assertThat(sql.getSql()).contains("cfg.store_id = ?", "card_type_name LIKE", "type.status = ?");
        assertThat(sql.getParameterMappings()).hasSize(4);
    }

    @Test
    void cardRefundCannotExceedOriginalAvailableTimes() throws Exception {
        Method method = CardMapper.class.getMethod(
                "refundCardBalance", long.class, java.math.BigDecimal.class, byte[].class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertThat(sql).contains(
                "remaining_times = remaining_times + #{times}",
                "remaining_times + #{times} <= total_times - frozen_times",
                "row_version = #{rowVersion}");
    }

    @Test
    void exchangeUsesLockedBalancesAndVersionedOldCardClose() throws Exception {
        Method lock = CardMapper.class.getMethod("lockMemberCardBalances", long.class);
        Method create = CardMapper.class.getMethod(
                "insertExchangeMemberCard", String.class, CardExchangeCommand.class);
        Method close = CardMapper.class.getMethod("markCardExchanged", long.class, String.class, long.class);
        String lockSql = String.join(" ", lock.getAnnotation(Select.class).value());
        String createSql = String.join(" ", create.getAnnotation(Select.class).value());
        String closeSql = String.join(" ", close.getAnnotation(Update.class).value());

        assertThat(lockSql).contains("UPDLOCK, HOLDLOCK", "balance.member_card_id = #{memberCardId}");
        assertThat(createSql).contains(
                "cat_card_type target WITH (UPDLOCK, HOLDLOCK)",
                "target.row_version = CONVERT(binary(8), #{command.quote.targetCardTypeVersion}, 1)");
        assertThat(closeSql).contains(
                "status = 'EXCHANGED'", "status = 'ACTIVE'",
                "row_version = CONVERT(binary(8), #{version}, 1)");
    }

    @Test
    void transferLocksActiveRecipientAndVersionClosesSourceCard() throws Exception {
        Method create = CardMapper.class.getMethod(
                "insertTransferMemberCard", String.class, CardTransferCommand.class);
        Method close = CardMapper.class.getMethod("markCardTransferred", long.class, String.class, long.class);
        String createSql = String.join(" ", create.getAnnotation(Select.class).value());
        String closeSql = String.join(" ", close.getAnnotation(Update.class).value());

        assertThat(createSql).contains(
                "mem_member recipient WITH (UPDLOCK, HOLDLOCK)", "recipient.status = 'ACTIVE'",
                "#{command.remainingValue}", "#{command.sourceCard.id}");
        assertThat(closeSql).contains(
                "status = 'TRANSFERRED'", "status = 'ACTIVE'",
                "row_version = CONVERT(binary(8), #{version}, 1)");
    }

    @Test
    void refundUsesBillOriginalAmountAndVersionedFrozenCard() throws Exception {
        Method reprice = CardMapper.class.getMethod("findConsumptionRepriceItems", long.class);
        Method freeze = CardMapper.class.getMethod("freezeCardForRefund", long.class, String.class, long.class);
        Method close = CardMapper.class.getMethod("markCardRefunded", long.class, String.class, long.class);
        Method saleEmployee = CardMapper.class.getMethod("findCardSaleEmployeeId", long.class);
        Method lineage = CardMapper.class.getMethod("findCardLineage", long.class);
        Method commissionStatus = CardMapper.class.getMethod(
                "updateCardRefundCommissionStatus", long.class, String.class);
        String repriceSql = String.join(" ", reprice.getAnnotation(Select.class).value());
        String freezeSql = String.join(" ", freeze.getAnnotation(Update.class).value());
        String closeSql = String.join(" ", close.getAnnotation(Update.class).value());
        String saleEmployeeSql = String.join(" ", saleEmployee.getAnnotation(Select.class).value());
        String lineageSql = String.join(" ", lineage.getAnnotation(Select.class).value());
        String commissionStatusSql = String.join(" ", commissionStatus.getAnnotation(Update.class).value());

        assertThat(repriceSql).contains(
                "line.original_amount AS originalAmount", "reversed.reversed_ledger_id = ledger.id");
        assertThat(freezeSql).contains("status = 'FROZEN'", "status = 'ACTIVE'", "row_version");
        assertThat(closeSql).contains("status = 'REFUNDED'", "status = 'FROZEN'", "row_version");
        assertThat(saleEmployeeSql).contains(
                "WITH card_lineage", "transfer_from_card_id", "sale_employee_id IS NOT NULL", "ORDER BY depth");
        assertThat(lineageSql).contains(
                "WITH card_lineage", "parent.id = lineage.transfer_from_card_id", "MAXRECURSION 32");
        assertThat(commissionStatusSql).contains(
                "commission_adjustment_status = #{status}",
                "status = 'EXECUTED'",
                "commission_adjustment_status = 'PENDING_MODULE'");
    }
}
