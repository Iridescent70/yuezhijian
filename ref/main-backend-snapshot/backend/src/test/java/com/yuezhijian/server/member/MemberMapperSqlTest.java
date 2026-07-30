package com.yuezhijian.server.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

class MemberMapperSqlTest {
    @Test
    void nullableBirthdayIsBoundAsSqlDate() throws Exception {
        Method insertMethod = MemberMapper.class.getMethod("insertMember", NewMemberRow.class);
        String insertSql = String.join(" ", insertMethod.getAnnotation(Select.class).value());
        Configuration configuration = new Configuration();
        SqlSource insertSource = new XMLLanguageDriver().createSqlSource(
                configuration, "<script>" + insertSql + "</script>", NewMemberRow.class);

        BoundSql boundSql = insertSource.getBoundSql(new NewMemberRow(
                "M001", "会员", null, "UNKNOWN", null, "ciphertext", "hash", "0000",
                null, "MANUAL", 1L, 1L, null, 1L, 1L));

        assertThat(boundSql.getParameterMappings())
                .filteredOn(mapping -> mapping.getProperty().equals("birthday"))
                .singleElement()
                .extracting(mapping -> mapping.getJdbcType())
                .isEqualTo(JdbcType.DATE);
    }

    @Test
    void dynamicMemberSearchSqlCanBeParsedWithRecordParameters() throws Exception {
        Method method = MemberMapper.class.getMethod(
                "findPage", MemberQuery.class, int.class, int.class);
        Select select = method.getAnnotation(Select.class);
        String script = String.join(" ", select.value());
        Configuration configuration = new Configuration();
        SqlSource sqlSource = new XMLLanguageDriver().createSqlSource(configuration, script, Map.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", new MemberQuery("13800001001", 2L, "ACTIVE", 1, 20, "hash"));
        parameters.put("offset", 0);
        parameters.put("size", 20);

        BoundSql boundSql = sqlSource.getBoundSql(parameters);

        assertThat(boundSql.getSql()).contains("m.mobile_hash = ?");
        assertThat(boundSql.getSql()).contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        assertThat(boundSql.getParameterMappings()).isNotEmpty();
    }

    @Test
    void profileAndTagUpdatesUseVersionAndParseDynamicLists() throws Exception {
        Method updateMethod = MemberMapper.class.getMethod(
                "updateMember", MemberUpdateCommand.class, String.class, String.class, String.class, byte[].class);
        SqlSource updateSource = new XMLLanguageDriver().createSqlSource(
                new Configuration(), String.join(" ", updateMethod.getAnnotation(Update.class).value()), Map.class);
        Map<String, Object> updateParameters = new HashMap<>();
        updateParameters.put("command", new MemberUpdateCommand(
                1L, "会员", null, "13800000000", "FEMALE", null, null, null, false, "AQ==", 1L));
        updateParameters.put("mobileCiphertext", "ciphertext");
        updateParameters.put("mobileHash", "hash");
        updateParameters.put("mobileLast4", "0000");
        updateParameters.put("rowVersion", new byte[] {1});
        BoundSql updateSql = updateSource.getBoundSql(updateParameters);
        assertThat(updateSql.getSql()).contains("mobile_ciphertext = ?", "row_version = ?");

        Method duplicateMethod = MemberMapper.class.getMethod(
                "countByMobileHashExcluding", String.class, long.class);
        assertThat(String.join(" ", duplicateMethod.getAnnotation(Select.class).value()))
                .contains("id <> #{memberId}");

        Method removeMethod = MemberMapper.class.getMethod(
                "removeTags", long.class, java.util.List.class, long.class);
        SqlSource removeSource = new XMLLanguageDriver().createSqlSource(
                new Configuration(), String.join(" ", removeMethod.getAnnotation(Update.class).value()), Map.class);
        BoundSql removeSql = removeSource.getBoundSql(Map.of(
                "memberId", 1L, "tagIds", java.util.List.of(1L, 2L), "operatorId", 1L));
        assertThat(removeSql.getSql()).contains("tag_id IN", "removed_at IS NULL");
        assertThat(removeSql.getParameterMappings()).hasSize(4);

        Method addMethod = MemberMapper.class.getMethod(
                "addTags", long.class, java.util.List.class, long.class);
        SqlSource addSource = new XMLLanguageDriver().createSqlSource(
                new Configuration(), String.join(" ", addMethod.getAnnotation(Insert.class).value()), Map.class);
        BoundSql addSql = addSource.getBoundSql(Map.of(
                "memberId", 1L, "tagIds", java.util.List.of(2L, 3L), "operatorId", 1L));
        assertThat(addSql.getSql()).contains("INSERT INTO dbo.mem_member_tag", "tag.status = 'ACTIVE'", "NOT EXISTS");
    }

    @Test
    void advisorAssignmentUsesStoreVersionAndAppendOnlyHistory() throws Exception {
        Method assignMethod = MemberMapper.class.getMethod(
                "assignAdvisor", MemberAdvisorCommand.class, byte[].class);
        String assignSql = String.join(" ", assignMethod.getAnnotation(Update.class).value());
        assertThat(assignSql).contains(
                "advisor_employee_id = #{command.newAdvisorEmployeeId}",
                "owner_store_id = #{command.ownerStoreId}",
                "row_version = #{rowVersion}");

        Method historyMethod = MemberMapper.class.getMethod("insertAdvisorLog", MemberAdvisorCommand.class);
        String historySql = String.join(" ", historyMethod.getAnnotation(Insert.class).value());
        assertThat(historySql).contains(
                "INSERT INTO dbo.mem_member_advisor_log", "old_advisor_employee_id",
                "new_advisor_employee_id", "#{changeSource}");
    }
}
