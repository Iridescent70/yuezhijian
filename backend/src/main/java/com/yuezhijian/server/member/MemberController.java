package com.yuezhijian.server.member;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.common.PageResult;
import jakarta.validation.Valid;
import java.security.Principal;
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
@RequestMapping("/api/v1/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('member:member:view')")
    public ApiResponse<PageResult<MemberSummary>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(memberService.search(keyword, storeId, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('member:member:view')")
    public ApiResponse<MemberDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(memberService.detail(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('member:member:create')")
    public ApiResponse<CreatedMember> create(
            @Valid @RequestBody CreateMemberRequest request,
            Principal principal) {
        return ApiResponse.ok(memberService.create(request, principal.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('member:member:manage')")
    public ApiResponse<MemberDetail> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateMemberRequest request,
            Principal principal) {
        return ApiResponse.ok(memberService.update(id, request, principal.getName()));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('member:member:manage')")
    public ApiResponse<MemberDetail> changeStatus(
            @PathVariable long id,
            @Valid @RequestBody ChangeMemberStatusRequest request,
            Principal principal) {
        return ApiResponse.ok(memberService.changeStatus(id, request, principal.getName()));
    }

    @PutMapping("/{id}/tags")
    @PreAuthorize("hasAuthority('member:tag:manage')")
    public ApiResponse<MemberDetail> updateTags(
            @PathVariable long id,
            @Valid @RequestBody UpdateMemberTagsRequest request,
            Principal principal) {
        return ApiResponse.ok(memberService.updateTags(id, request, principal.getName()));
    }
}
