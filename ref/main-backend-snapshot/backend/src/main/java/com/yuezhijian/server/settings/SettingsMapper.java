package com.yuezhijian.server.settings;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SettingsMapper {
    String PARAMETER_SELECT = """
            SELECT id, param_group AS paramGroup, param_key AS paramKey,
                   value_ciphertext AS value, value_type AS valueType, description, status,
                   updated_at AS updatedAt, sys.fn_varbintohexstr(row_version) AS version
            FROM dbo.sys_parameter
            WHERE is_secret = 0
            """;

    String RULE_SELECT = """
            SELECT id, rule_name AS ruleName, keyword_pattern AS keywordPattern, score,
                   component_mapping_json AS componentMappingJson, priority, status,
                   updated_at AS updatedAt, sys.fn_varbintohexstr(row_version) AS version
            FROM dbo.vis_satisfaction_rule
            """;

    @Select("""
            <script>
            """ + PARAMETER_SELECT + """
              <if test="group != null">AND param_group = #{group}</if>
            ORDER BY param_group, param_key
            </script>
            """)
    List<SystemParameterItem> findParameters(@Param("group") String group);

    @Select(PARAMETER_SELECT + " AND param_group = #{group} AND param_key = #{key}")
    SystemParameterItem findParameter(@Param("group") String group, @Param("key") String key);

    @Select(PARAMETER_SELECT + " AND id = #{id}")
    SystemParameterItem findParameterById(long id);

    @Update("""
            UPDATE dbo.sys_parameter
            SET value_ciphertext = #{value}, status = #{status},
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND is_secret = 0
              AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int updateParameter(
            @Param("id") long id,
            @Param("value") String value,
            @Param("status") String status,
            @Param("version") String version,
            @Param("operatorId") long operatorId);

    @Select("""
            <script>
            """ + RULE_SELECT + """
            <if test="status != null">WHERE status = #{status}</if>
            ORDER BY priority, id
            </script>
            """)
    List<SatisfactionRuleRow> findSatisfactionRules(@Param("status") String status);

    @Select(RULE_SELECT + " WHERE id = #{id}")
    SatisfactionRuleRow findSatisfactionRule(long id);

    @Select(RULE_SELECT + " WHERE rule_name = #{name}")
    SatisfactionRuleRow findSatisfactionRuleByName(String name);

    @Select("""
            SELECT COUNT(1) FROM dbo.vis_satisfaction_rule
            WHERE rule_name = #{name} AND (#{excludeId} IS NULL OR id != #{excludeId})
            """)
    int countSatisfactionRuleName(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Insert("""
            INSERT INTO dbo.vis_satisfaction_rule (
                rule_name, keyword_pattern, score, component_mapping_json,
                priority, status, created_by, updated_by
            ) VALUES (
                #{ruleName}, #{keywordPattern}, #{score}, #{componentMappingJson},
                #{priority}, #{status}, #{operatorId}, #{operatorId}
            )
            """)
    int insertSatisfactionRule(SatisfactionRuleDraft draft);

    @Update("""
            UPDATE dbo.vis_satisfaction_rule
            SET rule_name = #{ruleName}, keyword_pattern = #{keywordPattern}, score = #{score},
                component_mapping_json = #{componentMappingJson}, priority = #{priority}, status = #{status},
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int updateSatisfactionRule(SatisfactionRuleUpdate update);
}
