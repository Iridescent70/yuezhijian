package com.yuezhijian.server.iam;

import com.yuezhijian.server.common.ApiResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {
    private final CurrentUserService currentUserService;

    public MenuController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/tree")
    public ApiResponse<List<MenuItem>> tree(Authentication authentication, HttpSession session) {
        return ApiResponse.ok(currentUserService.from(authentication, session).menus());
    }
}
