package com.yuezhijian.server.benefit;

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

class BenefitMapperSqlTest {
    @Test
    void voucherSearchCanParseAllFilters() throws Exception {
        Method method = BenefitMapper.class.getMethod("findVoucherCodes", Long.class, String.class, String.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("memberId", 1001L);
        parameters.put("status", "BOUND");
        parameters.put("keyword", "VC");

        BoundSql sql = source.getBoundSql(parameters);

        assertThat(sql.getSql()).contains("code.member_id = ?", "code.status = ?", "code.code LIKE");
        assertThat(sql.getParameterMappings()).hasSize(4);
    }

    @Test
    void redeemAndReturnUseStateAndVersionGuards() throws Exception {
        Method redeem = BenefitMapper.class.getMethod("redeemVoucher", VoucherSettlementConsumption.class);
        Method returned = BenefitMapper.class.getMethod("returnVoucher", VoucherRefundCommand.class);
        String redeemSql = String.join(" ", redeem.getAnnotation(Update.class).value());
        String returnSql = String.join(" ", returned.getAnnotation(Update.class).value());

        assertThat(redeemSql).contains(
                "status = 'REDEEMED'", "status = 'BOUND'", "row_version = CONVERT(varbinary(8)",
                "valid_until >= sysdatetime()");
        assertThat(returnSql).contains(
                "status = 'BOUND'", "status = 'REDEEMED'", "redeemed_bill_id = #{command.billId}");
    }
}
