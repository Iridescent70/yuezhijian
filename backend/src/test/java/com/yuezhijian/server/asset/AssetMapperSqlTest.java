package com.yuezhijian.server.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class AssetMapperSqlTest {
    @Test
    void ledgerQueriesAreBoundedAndOrdered() throws Exception {
        Method balance = AssetMapper.class.getMethod("findBalanceLedgers", long.class, int.class);
        Method points = AssetMapper.class.getMethod("findPointLedgers", long.class, int.class);
        String balanceSql = String.join(" ", balance.getAnnotation(Select.class).value());
        String pointSql = String.join(" ", points.getAnnotation(Select.class).value());

        assertThat(balanceSql).contains("TOP (#{limit})", "account.member_id = #{memberId}", "occurred_at DESC");
        assertThat(pointSql).contains("TOP (#{limit})", "account.member_id = #{memberId}", "occurred_at DESC");
    }

    @Test
    void settlementDeductionsUseVersionAndBalanceGuards() throws Exception {
        Method balance = AssetMapper.class.getMethod(
                "consumeBalance", long.class, java.math.BigDecimal.class,
                java.time.LocalDateTime.class, byte[].class);
        Method points = AssetMapper.class.getMethod(
                "consumePoints", long.class, int.class, java.time.LocalDateTime.class, byte[].class);

        String balanceSql = String.join(" ", balance.getAnnotation(Update.class).value());
        String pointSql = String.join(" ", points.getAnnotation(Update.class).value());

        assertThat(balanceSql).contains("row_version = #{rowVersion}", "available_balance >= #{amount}");
        assertThat(pointSql).contains("row_version = #{rowVersion}", "available_points >= #{points}");
    }

    @Test
    void reversalCreditsUseVersionAndLinkOriginalLedger() throws Exception {
        Method balance = AssetMapper.class.getMethod(
                "refundBalance", long.class, java.math.BigDecimal.class,
                java.time.LocalDateTime.class, byte[].class);
        Method pointLedger = AssetMapper.class.getMethod(
                "insertPointRefundLedger", String.class, long.class, int.class, int.class, int.class,
                java.time.LocalDateTime.class, PointRefundCommand.class);

        String balanceSql = String.join(" ", balance.getAnnotation(Update.class).value());
        String ledgerSql = String.join(" ", pointLedger.getAnnotation(org.apache.ibatis.annotations.Insert.class).value());

        assertThat(balanceSql).contains("available_balance = available_balance + #{amount}", "row_version = #{rowVersion}");
        assertThat(ledgerSql).contains("'REVERSAL'", "#{command.originalLedgerId}", "#{command.usageId}");
    }
}
