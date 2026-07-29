package com.yuezhijian.server.appointment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository {
    List<AppointmentSummary> search(AppointmentQuery query);

    Optional<AppointmentDetail> findById(long id);

    Optional<CreatedAppointment> findByIdempotencyKey(String idempotencyKey);

    boolean hasConflict(
            long storeId, long employeeId, Long workstationId,
            LocalDateTime startAt, LocalDateTime endAt, Long excludeAppointmentId);

    CreatedAppointment create(AppointmentDraft draft);

    AppointmentDetail update(AppointmentUpdate update);

    AppointmentDetail transition(AppointmentStatusChange change);

    void linkBill(long appointmentId, long billId, long operatorId);
}
