package com.yuezhijian.server.member;

import com.yuezhijian.server.common.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/member-tags")
public class MemberTagController {
    private final MemberService memberService;

    public MemberTagController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('member:tag:view')")
    public ApiResponse<List<MemberTagOption>> options() {
        return ApiResponse.ok(memberService.tagOptions());
    }
}
