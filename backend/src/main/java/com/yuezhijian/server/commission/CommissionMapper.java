package com.yuezhijian.server.commission;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommissionMapper {
    String PLAN_SELECT = """
            SELECT p.id, p.plan_code AS code, p.plan_name AS name, p.scene,
                   p.calculation_mode AS calculationMode, p.rate, p.fixed_amount AS fixedAmount,
                   p.store_id AS storeId, s.store_name AS storeName,
                   p.position_id AS positionId, pos.position_name AS positionName,
                   p.effective_from AS effectiveFrom, p.effective_to AS effectiveTo,
                   p.status, p.rule_version AS ruleVersion,
                   sys.fn_varbintohexstr(p.row_version) AS version
            FROM dbo.comm_plan p
            LEFT JOIN dbo.org_store s ON s.id = p.store_id
            LEFT JOIN dbo.org_position pos ON pos.id = p.position_id
            """;

    String LEDGER_SELECT = """
            SELECT l.id, l.ledger_no AS ledgerNo, l.employee_id AS employeeId, e.employee_name AS employeeName,
                   l.store_id AS storeId, s.store_name AS storeName, l.commission_type AS commissionType,
                   l.source_type AS sourceType, l.source_id AS sourceId, l.source_no AS sourceNo,
                   l.source_line_id AS sourceLineId, l.source_line_name AS sourceLineName,
                   l.base_amount AS baseAmount, l.rate, l.commission_amount AS commissionAmount,
                   l.calculation_status AS calculationStatus, l.plan_id AS planId,
                   l.plan_name AS planName, l.plan_rule_version AS planRuleVersion,
                   l.formula_snapshot AS formulaSnapshot, l.occurred_at AS occurredAt,
                   l.correlation_id AS correlationId, l.reversed_ledger_id AS reversedLedgerId
            FROM dbo.comm_ledger l
            JOIN dbo.org_employee e ON e.id = l.employee_id
            JOIN dbo.org_store s ON s.id = l.store_id
            """;

    @Select("""
            <script>
            <bind name="pattern" value="keyword == null ? null : '%' + keyword + '%'" />
            """ + PLAN_SELECT + """
            WHERE (#{keyword} IS NULL OR p.plan_code LIKE #{pattern} OR p.plan_name LIKE #{pattern})
              AND (#{status} IS NULL OR p.status = #{status})
            ORDER BY p.id DESC
            </script>
            """)
    List<CommissionPlan> findPlans(@Param("keyword") String keyword, @Param("status") String status);

    @Select(PLAN_SELECT + " WHERE p.id = #{id}")
    CommissionPlan findPlan(long id);

    @Select("SELECT COUNT(1) FROM dbo.comm_plan WHERE plan_code = #{code}")
    int countPlanCode(String code);

    @Insert("""
            INSERT INTO dbo.comm_plan (
                plan_code, plan_name, scene, calculation_mode, rate, fixed_amount, store_id, position_id,
                effective_from, effective_to, status, rule_version, created_by, updated_by
            ) VALUES (
                #{plan.code}, #{plan.name}, #{plan.scene}, #{plan.calculationMode}, #{plan.rate},
                #{plan.fixedAmount}, #{plan.storeId}, #{plan.positionId}, #{plan.effectiveFrom},
                #{plan.effectiveTo}, #{plan.status}, 1, #{operatorId}, #{operatorId}
            )
            """)
    int insertPlan(@Param("plan") CommissionPlan plan, @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.comm_plan SET plan_name = #{plan.name}, scene = #{plan.scene},
                calculation_mode = #{plan.calculationMode}, rate = #{plan.rate}, fixed_amount = #{plan.fixedAmount},
                store_id = #{plan.storeId}, position_id = #{plan.positionId},
                effective_from = #{plan.effectiveFrom}, effective_to = #{plan.effectiveTo}, status = #{plan.status},
                rule_version = rule_version + 1, updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{plan.id} AND row_version = CONVERT(binary(8), #{plan.version}, 1)
            """)
    int updatePlan(@Param("plan") CommissionPlan plan, @Param("operatorId") long operatorId);

    @Insert("""
            INSERT INTO dbo.comm_plan_revision (
                plan_id, rule_version, plan_name, scene, calculation_mode, rate, fixed_amount,
                store_id, position_id, effective_from, effective_to, status, recorded_by
            )
            SELECT id, rule_version, plan_name, scene, calculation_mode, rate, fixed_amount,
                   store_id, position_id, effective_from, effective_to, status, #{operatorId}
            FROM dbo.comm_plan WHERE id = #{planId}
            """)
    int insertPlanRevision(@Param("planId") long planId, @Param("operatorId") long operatorId);

    @Select(PLAN_SELECT + """
            WHERE p.scene = #{scene} AND p.status = 'ACTIVE'
              AND (p.store_id IS NULL OR p.store_id = #{storeId})
              AND (p.position_id IS NULL OR p.position_id = #{positionId})
              AND p.effective_from <= #{businessDate}
              AND (p.effective_to IS NULL OR p.effective_to >= #{businessDate})
            ORDER BY CASE WHEN p.position_id IS NULL THEN 0 ELSE 2 END
                   + CASE WHEN p.store_id IS NULL THEN 0 ELSE 1 END DESC,
                   p.rule_version DESC, p.id DESC
            OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
            """)
    CommissionPlan findApplicablePlan(
            @Param("scene") String scene,
            @Param("storeId") long storeId,
            @Param("positionId") Long positionId,
            @Param("businessDate") LocalDate businessDate);

    @Select("""
            <script>
            """ + LEDGER_SELECT + """
            WHERE (#{query.employeeId} IS NULL OR l.employee_id = #{query.employeeId})
              AND (#{query.storeId} IS NULL OR l.store_id = #{query.storeId})
              AND (#{query.startDate} IS NULL OR l.occurred_at &gt;= #{query.startDate})
              AND (#{query.endDate} IS NULL OR l.occurred_at &lt; DATEADD(day, 1, #{query.endDate}))
              <if test="query.direction != null and query.direction == 'POSITIVE'">AND l.commission_amount &gt;= 0</if>
              <if test="query.direction != null and query.direction == 'NEGATIVE'">AND l.commission_amount &lt; 0</if>
              AND (#{query.calculationStatus} IS NULL OR l.calculation_status = #{query.calculationStatus})
            ORDER BY l.occurred_at DESC, l.id DESC
            </script>
            """)
    List<CommissionLedgerItem> findLedgers(@Param("query") CommissionLedgerQuery query);

    @Select(LEDGER_SELECT + " WHERE l.correlation_id = #{correlationId}")
    CommissionLedgerItem findLedgerByCorrelation(String correlationId);

    @Insert("""
            INSERT INTO dbo.comm_ledger (
                ledger_no, employee_id, store_id, commission_type, source_type, source_id, source_no,
                source_line_id, source_line_name, base_amount, rate, commission_amount, calculation_status,
                plan_id, plan_name, plan_rule_version, formula_snapshot, occurred_at, correlation_id,
                reversed_ledger_id, created_by
            ) VALUES (
                #{ledgerNo}, #{employeeId}, #{storeId}, #{commissionType}, #{sourceType}, #{sourceId}, #{sourceNo},
                #{sourceLineId}, #{sourceLineName}, #{baseAmount}, #{rate}, #{commissionAmount},
                #{calculationStatus}, #{planId}, #{planName}, #{planRuleVersion}, #{formulaSnapshot},
                #{occurredAt}, #{correlationId}, #{reversedLedgerId}, #{createdBy}
            )
            """)
    int insertLedger(CommissionLedgerDraft draft);

    @Select(LEDGER_SELECT + """
            WHERE l.source_type = 'BILL' AND l.source_id = #{billId}
              AND l.reversed_ledger_id IS NULL AND l.commission_amount >= 0
            ORDER BY l.id
            """)
    List<CommissionLedgerItem> findOriginalBillLedgers(long billId);

    @Select(LEDGER_SELECT + """
            WHERE l.commission_type = 'CARD_SALE' AND l.source_line_id = #{memberCardId}
              AND l.reversed_ledger_id IS NULL AND l.commission_amount >= 0
            ORDER BY l.id
            """)
    List<CommissionLedgerItem> findOriginalCardSaleLedgers(long memberCardId);
}
