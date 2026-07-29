package com.yuezhijian.server.workbench;

import com.yuezhijian.server.common.ApiResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workbench")
public class WorkbenchController {

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('workbench:view')")
    public ApiResponse<WorkbenchOverview> overview() {
        return ApiResponse.ok(new WorkbenchOverview(
                LocalDate.now(),
                0,
                0,
                BigDecimal.ZERO,
                0,
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
