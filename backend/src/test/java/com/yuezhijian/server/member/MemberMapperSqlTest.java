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
import org.junit.jupiter.api.Test;

class MemberMapperSqlTest {
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
}
