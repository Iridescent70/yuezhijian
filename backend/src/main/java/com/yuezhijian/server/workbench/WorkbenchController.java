package com.yuezhijian.server.workbench;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.appointment.AppointmentQuery;
import com.yuezhijian.server.appointment.AppointmentRepository;
import com.yuezhijian.server.appointment.AppointmentSummary;
import com.yuezhijian.server.iam.CurrentStoreContext;
import com.yuezhijian.server.trade.BillQuery;
import com.yuezhijian.server.trade.BillSummary;
import com.yuezhijian.server.trade.TradeRepository;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workbench")
public class WorkbenchController {
    private final AppointmentRepository appointments;
    private final CurrentStoreContext currentStoreContext;
    private final TradeRepository trade;

    public WorkbenchController(
            AppointmentRepository appointments, CurrentStoreContext currentStoreContext, TradeRepository trade) {
        this.appointments = appointments;
        this.currentStoreContext = currentStoreContext;
        this.trade = trade;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('workbench:view')")
    public ApiResponse<WorkbenchOverview> overview(Authentication authentication, HttpSession session) {
        LocalDate businessDate = LocalDate.now();
        long storeId = currentStoreContext.currentStore(authentication, session).id();
        List<AppointmentSummary> today = appointments.search(
                new AppointmentQuery(storeId, businessDate, businessDate, null));
        int customerTraffic = today.stream()
                .filter(item -> List.of("ARRIVED", "SERVING", "COMPLETED").contains(item.status()))
                .mapToInt(AppointmentSummary::personCount).sum();
        int pending = (int) today.stream().filter(item -> "PENDING_CONFIRM".equals(item.status())).count();
        BigDecimal revenue = trade.search(new BillQuery(storeId, businessDate, businessDate, "SETTLED", null))
                .stream().map(BillSummary::receivedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return ApiResponse.ok(new WorkbenchOverview(
                businessDate,
                today.size(),
                customerTraffic,
                revenue,
                pending,
                List.of(
                        new Shortcut("member-new", "新建会员", "/app/members/new", "member:member:create"),
                        new Shortcut("appointment-new", "新建预约", "/app/appointments/new", "appointment:appointment:create"),
                        new Shortcut("bill-new", "新建账单", "/app/bills/new", "trade:bill:create"))));
    }

    public record WorkbenchOverview(
            LocalDate businessDate,
            int appointmentCount,
            int customerTraffic,
            BigDecimal revenue,
            int pendingTaskCount,
            List<Shortcut> shortcuts) {
    }

    public record Shortcut(String code, String name, String route, String permission) {
    }
}
