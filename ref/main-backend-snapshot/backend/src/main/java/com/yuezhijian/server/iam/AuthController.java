package com.yuezhijian.server.iam;

import com.yuezhijian.server.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CurrentUserService currentUserService;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CurrentUserService currentUserService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ApiResponse.ok(Map.of("headerName", csrfToken.getHeaderName(), "token", csrfToken.getToken()));
    }

    @PostMapping("/login")
    public ApiResponse<CurrentUser> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        loginRequest.username(), loginRequest.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return ApiResponse.ok(currentUserService.from(authentication, request.getSession()));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> me(Authentication authentication, HttpSession session) {
        return ApiResponse.ok(currentUserService.from(authentication, session));
    }

    @PostMapping("/current-store")
    public ApiResponse<CurrentUser> switchStore(
            @Valid @RequestBody SwitchStoreRequest switchStoreRequest,
            Authentication authentication,
            HttpSession session) {
        return ApiResponse.ok(currentUserService.switchStore(
                authentication, session, switchStoreRequest.storeId()));
    }

    @PostMapping("/session/renew")
    public ApiResponse<Map<String, Object>> renew(HttpSession session) {
        session.setMaxInactiveInterval(8 * 60 * 60);
        return ApiResponse.ok(Map.of("expiresInSeconds", session.getMaxInactiveInterval()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ApiResponse.ok(null);
    }

    public record LoginRequest(
            @NotBlank(message = "请输入账号") String username,
            @NotBlank(message = "请输入密码") String password) {
    }

    public record SwitchStoreRequest(
            @NotNull(message = "请选择门店") Long storeId) {
    }
}
