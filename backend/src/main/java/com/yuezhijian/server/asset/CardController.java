package com.yuezhijian.server.asset;

import com.yuezhijian.server.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CardController {
    private final CardService service;

    public CardController(CardService service) {
        this.service = service;
    }

    @GetMapping("/card-types")
    @PreAuthorize("hasAuthority('catalog:card:view')")
    public ApiResponse<List<CardTypeDetail>> cardTypes(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.cardTypes(storeId, keyword, status));
    }

    @GetMapping("/card-types/{id}")
    @PreAuthorize("hasAuthority('catalog:card:view')")
    public ApiResponse<CardTypeDetail> cardType(@PathVariable long id) {
        return ApiResponse.ok(service.cardType(id));
    }

    @PostMapping("/card-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:card:manage')")
    public ApiResponse<CardTypeDetail> createCardType(
            @Valid @RequestBody CreateCardTypeRequest request, Principal principal) {
        return ApiResponse.ok(service.createCardType(request, principal.getName()));
    }

    @GetMapping("/members/{memberId}/cards")
    @PreAuthorize("hasAuthority('member:card:view')")
    public ApiResponse<List<MemberCardSummary>> memberCards(
            @PathVariable long memberId, @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.memberCards(memberId, status));
    }

    @GetMapping("/member-cards/{id}")
    @PreAuthorize("hasAuthority('member:card:view')")
    public ApiResponse<MemberCardDetail> memberCard(@PathVariable long id) {
        return ApiResponse.ok(service.memberCard(id));
    }

    @PostMapping("/members/{memberId}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('member:card:manage')")
    public ApiResponse<CardSaleResult> purchase(
            @PathVariable long memberId,
            @Valid @RequestBody PurchaseMemberCardRequest request,
            Principal principal) {
        return ApiResponse.ok(service.purchase(memberId, request, principal.getName()));
    }
}
