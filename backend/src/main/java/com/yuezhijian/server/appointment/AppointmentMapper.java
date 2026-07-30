package com.yuezhijian.server.appointment;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppointmentMapper {
    String SUMMARY_SELECT = """
            SELECT a.id, a.appointment_no AS appointmentNo, a.member_id AS memberId,
                   COALESCE(member.full_name, a.guest_name) AS customerName,
                   CASE WHEN COALESCE(member.mobile_last4, a.mobile_last4) IS NULL THEN NULL
                        ELSE CONCAT('*******', RTRIM(COALESCE(member.mobile_last4, a.mobile_last4))) END AS maskedMobile,
                   a.store_id AS storeId, store.store_name AS storeName, a.source_type AS sourceType,
                   a.appointment_type AS appointmentType, a.start_at AS startAt, a.end_at AS endAt,
                   a.person_count AS personCount, employee_slot.employee_id AS employeeId,
                   employee_slot.employee_name AS employeeName, a.workstation_id AS workstationId,
                   workstation.name AS workstationName, service_names.names AS serviceNames,
                   a.note, a.status, CONVERT(varchar(18), CAST(a.row_version AS varbinary(8)), 1) AS version
            FROM dbo.apt_appointment a
            LEFT JOIN dbo.mem_member member ON member.id = a.member_id
            JOIN dbo.org_store store ON store.id = a.store_id
            LEFT JOIN dbo.org_workstation workstation ON workstation.id = a.workstation_id
            OUTER APPLY (
                SELECT TOP 1 relation.employee_id, employee.name AS employee_name
                FROM dbo.apt_appointment_employee relation
                JOIN dbo.org_employee employee ON employee.id = relation.employee_id
                WHERE relation.appointment_id = a.id
                ORDER BY CASE WHEN relation.role_type = 'PRIMARY' THEN 0 ELSE 1 END, relation.id
            ) employee_slot
            OUTER APPLY (
                SELECT STRING_AGG(CONVERT(nvarchar(max), relation.service_name_snapshot), N'、')
                       WITHIN GROUP (ORDER BY relation.sort_no, relation.id) AS names
                FROM dbo.apt_appointment_service relation
                WHERE relation.appointment_id = a.id
            ) service_names
            """;

    @Select("""
            <script>
            """ + SUMMARY_SELECT + """
            WHERE a.store_id = #{query.storeId}
              AND a.start_at &gt;= #{from}
              AND a.start_at &lt; #{until}
            <if test="query.status != null">
              AND a.status = #{query.status}
            </if>
            ORDER BY a.start_at, a.id
            </script>
            """)
    List<AppointmentSummary> search(
            @Param("query") AppointmentQuery query,
            @Param("from") LocalDateTime from,
            @Param("until") LocalDateTime until);

    @Select(SUMMARY_SELECT + " WHERE a.id = #{id}")
    AppointmentSummary findSummaryById(long id);

    @Select(SUMMARY_SELECT + " WHERE a.idempotency_key = #{idempotencyKey}")
    AppointmentSummary findSummaryByIdempotencyKey(String idempotencyKey);

    @Select("""
            SELECT service_id AS serviceId, service_name_snapshot AS serviceName,
                   duration_minutes AS durationMinutes, price_snapshot AS price, sort_no AS sortNo
            FROM dbo.apt_appointment_service
            WHERE appointment_id = #{appointmentId}
            ORDER BY sort_no, id
            """)
    List<AppointmentServiceLine> findServices(long appointmentId);

    @Select("""
            SELECT id, from_status AS fromStatus, to_status AS toStatus,
                   reason_code AS reasonCode, note, occurred_at AS occurredAt, operator_id AS operatorId
            FROM dbo.apt_status_history
            WHERE appointment_id = #{appointmentId}
            ORDER BY occurred_at, id
            """)
    List<AppointmentHistoryItem> findHistory(long appointmentId);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM dbo.apt_appointment a WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.apt_appointment_employee employee_slot ON employee_slot.appointment_id = a.id
            WHERE a.store_id = #{storeId}
              AND a.status IN ('PENDING_CONFIRM', 'CONFIRMED', 'ARRIVED', 'SERVING')
              AND a.start_at &lt; #{endAt} AND a.end_at &gt; #{startAt}
              AND (employee_slot.employee_id = #{employeeId}
                   <if test="workstationId != null">
                     OR a.workstation_id = #{workstationId}
                   </if>)
            <if test="excludeAppointmentId != null">
              AND a.id != #{excludeAppointmentId}
            </if>
            </script>
            """)
    int countConflicts(
            @Param("storeId") long storeId,
            @Param("employeeId") long employeeId,
            @Param("workstationId") Long workstationId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("excludeAppointmentId") Long excludeAppointmentId);

    @Select(value = """
            INSERT INTO dbo.apt_appointment (
                appointment_no, member_id, guest_name, mobile_ciphertext, mobile_hash, mobile_last4,
                store_id, source_type, appointment_type, start_at, end_at, person_count,
                workstation_id, note, idempotency_key, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{appointmentNo}, #{memberId}, #{guestName}, #{mobileCiphertext}, #{mobileHash}, #{mobileLast4},
                #{storeId}, #{sourceType}, #{appointmentType}, #{startAt}, #{endAt}, #{personCount},
                #{workstationId}, #{note}, #{idempotencyKey}, #{operatorId}, #{operatorId}
            )
            """, affectData = true)
    long insertAppointment(ProtectedAppointmentRow appointment);

    @Insert("""
            INSERT INTO dbo.apt_appointment_service (
                appointment_id, service_id, service_name_snapshot, duration_minutes, price_snapshot, sort_no
            ) VALUES (
                #{appointmentId}, #{line.serviceId}, #{line.serviceName},
                #{line.durationMinutes}, #{line.price}, #{line.sortNo}
            )
            """)
    void insertService(@Param("appointmentId") long appointmentId, @Param("line") AppointmentServiceLine line);

    @Insert("""
            INSERT INTO dbo.apt_appointment_employee (
                appointment_id, employee_id, role_type, start_at, end_at, is_designated
            ) VALUES (#{appointmentId}, #{employeeId}, 'PRIMARY', #{startAt}, #{endAt}, #{designated})
            """)
    void insertEmployee(
            @Param("appointmentId") long appointmentId,
            @Param("employeeId") long employeeId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("designated") boolean designated);

    @Update("""
            UPDATE dbo.apt_appointment
            SET start_at = #{startAt}, end_at = #{endAt}, person_count = #{personCount},
                workstation_id = #{workstationId}, note = #{note},
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
              AND status IN ('PENDING_CONFIRM', 'CONFIRMED')
            """)
    int updateAppointment(AppointmentUpdate update);

    @Delete("DELETE FROM dbo.apt_appointment_service WHERE appointment_id = #{appointmentId}")
    void deleteServices(long appointmentId);

    @Delete("DELETE FROM dbo.apt_appointment_employee WHERE appointment_id = #{appointmentId}")
    void deleteEmployees(long appointmentId);

    @Update("""
            UPDATE dbo.apt_appointment
            SET status = #{toStatus},
                person_count = COALESCE(#{personCount}, person_count),
                arrived_at = CASE WHEN #{toStatus} = 'ARRIVED' THEN COALESCE(arrived_at, sysdatetime()) ELSE arrived_at END,
                started_at = CASE WHEN #{toStatus} = 'SERVING' THEN COALESCE(started_at, sysdatetime()) ELSE started_at END,
                completed_at = CASE WHEN #{toStatus} = 'COMPLETED' THEN COALESCE(completed_at, sysdatetime()) ELSE completed_at END,
                cancelled_at = CASE WHEN #{toStatus} IN ('CANCELLED', 'NO_SHOW') THEN COALESCE(cancelled_at, sysdatetime()) ELSE cancelled_at END,
                cancel_reason_id = CASE WHEN #{toStatus} IN ('CANCELLED', 'NO_SHOW') THEN
                    (SELECT id FROM dbo.sys_cancel_reason
                     WHERE business_type = 'APPOINTMENT' AND reason_code = #{reasonCode} AND status = 'ACTIVE')
                    ELSE cancel_reason_id END,
                cancel_note = CASE WHEN #{toStatus} IN ('CANCELLED', 'NO_SHOW') THEN #{note} ELSE cancel_note END,
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND status = #{fromStatus}
              AND row_version = CONVERT(binary(8), #{version}, 1)
              AND (#{toStatus} NOT IN ('CANCELLED', 'NO_SHOW') OR EXISTS (
                    SELECT 1 FROM dbo.sys_cancel_reason
                    WHERE business_type = 'APPOINTMENT' AND reason_code = #{reasonCode} AND status = 'ACTIVE'
              ))
            """)
    int transition(AppointmentStatusChange change);

    @Insert("""
            INSERT INTO dbo.apt_status_history (
                appointment_id, from_status, to_status, reason_code, note, operator_id
            ) VALUES (#{appointmentId}, #{fromStatus}, #{toStatus}, #{reasonCode}, #{note}, #{operatorId})
            """)
    void insertHistory(
            @Param("appointmentId") long appointmentId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("reasonCode") String reasonCode,
            @Param("note") String note,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.apt_appointment
            SET bill_id = #{billId}, updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{appointmentId} AND (bill_id IS NULL OR bill_id = #{billId})
            """)
    int linkBill(
            @Param("appointmentId") long appointmentId,
            @Param("billId") long billId,
            @Param("operatorId") long operatorId);
}
