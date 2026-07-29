package com.yuezhijian.server.appointment;

import com.yuezhijian.server.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AppointmentController {
    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @GetMapping({"/appointments", "/appointments/calendar"})
    @PreAuthorize("hasAuthority('appointment:appointment:view')")
    public ApiResponse<List<AppointmentSummary>> search(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.search(storeId, startDate, endDate, status));
    }

    @GetMapping("/appointments/availability")
    @PreAuthorize("hasAuthority('appointment:appointment:view')")
    public ApiResponse<List<AvailabilitySlot>> availability(
            @RequestParam long storeId,
            @RequestParam long serviceId,
            @RequestParam long employeeId,
            @RequestParam LocalDate date) {
        return ApiResponse.ok(service.availability(storeId, serviceId, employeeId, date));
    }

    @GetMapping("/appointments/{id}")
    @PreAuthorize("hasAuthority('appointment:appointment:view')")
    public ApiResponse<AppointmentDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('appointment:appointment:create')")
    public ApiResponse<CreatedAppointment> create(
            @Valid @RequestBody CreateAppointmentRequest request, Principal principal) {
        return ApiResponse.ok(service.create(request, principal.getName()));
    }

    @PutMapping("/appointments/{id}")
    @PreAuthorize("hasAuthority('appointment:appointment:manage')")
    public ApiResponse<AppointmentDetail> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateAppointmentRequest request,
            Principal principal) {
        return ApiResponse.ok(service.update(id, request, principal.getName()));
    }

    @PostMapping("/appointments/{id}/{action:confirm|arrive|start|complete|cancel|no-show}")
    @PreAuthorize("hasAuthority('appointment:appointment:manage')")
    public ApiResponse<AppointmentDetail> transition(
            @PathVariable long id,
            @PathVariable String action,
            @Valid @RequestBody AppointmentTransitionRequest request,
            Principal principal) {
        AppointmentStatus target = switch (action) {
            case "confirm" -> AppointmentStatus.CONFIRMED;
            case "arrive" -> AppointmentStatus.ARRIVED;
            case "start" -> AppointmentStatus.SERVING;
            case "complete" -> AppointmentStatus.COMPLETED;
            case "cancel" -> AppointmentStatus.CANCELLED;
            case "no-show" -> AppointmentStatus.NO_SHOW;
            default -> throw new IllegalArgumentException("不支持的预约操作");
        };
        return ApiResponse.ok(service.transition(id, target, request, principal.getName()));
    }

    @GetMapping("/appointment-cancel-reasons")
    @PreAuthorize("hasAuthority('appointment:appointment:view')")
    public ApiResponse<List<CancelReasonOption>> cancelReasons() {
        return ApiResponse.ok(service.cancelReasons());
    }
}
