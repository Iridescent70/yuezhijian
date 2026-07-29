package com.yuezhijian.server.benefit;

import java.util.List;
import java.util.Optional;

public interface BenefitRepository {
    List<VoucherDefinition> definitions(String keyword, String status);
    Optional<VoucherDefinition> findDefinition(long id);
    boolean existsDefinitionCode(String code);
    VoucherDefinition createDefinition(VoucherDefinition draft, long operatorId);
    VoucherDefinition updateDefinition(VoucherDefinition draft, long operatorId);

    List<VoucherCodeSummary> voucherCodes(Long memberId, String status, String keyword);
    Optional<VoucherCodeSummary> findVoucherCode(String code);
    Optional<VoucherCodeSummary> findVoucherCode(long id);
    List<VoucherCodeSummary> findIssueByKey(String idempotencyKey);
    List<VoucherCodeSummary> issue(VoucherIssueDraft draft);
    VoucherCodeSummary bind(VoucherBindCommand command);
    void consume(VoucherSettlementConsumption command);
    void refund(VoucherRefundCommand command);
}
