package com.yuezhijian.server.benefit;

import com.yuezhijian.server.common.DuplicateResourceException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlserver")
public class SqlServerBenefitRepository implements BenefitRepository {
    private final BenefitMapper mapper;
    private final BenefitNumberGenerator numbers;

    public SqlServerBenefitRepository(BenefitMapper mapper, BenefitNumberGenerator numbers) {
        this.mapper = mapper;
        this.numbers = numbers;
    }

    @Override public List<VoucherDefinition> definitions(String keyword, String status) {
        return mapper.findDefinitions(keyword, status);
    }
    @Override public Optional<VoucherDefinition> findDefinition(long id) {
        return Optional.ofNullable(mapper.findDefinition(id));
    }
    @Override public boolean existsDefinitionCode(String code) { return mapper.countDefinitionCode(code) > 0; }

    @Override @Transactional
    public VoucherDefinition createDefinition(VoucherDefinition draft, long operatorId) {
        return mapper.findDefinition(mapper.insertDefinition(draft, operatorId));
    }

    @Override @Transactional
    public VoucherDefinition updateDefinition(VoucherDefinition draft, long operatorId) {
        if (mapper.updateDefinition(draft, operatorId) != 1) {
            throw new DuplicateResourceException("代金券定义已被他人修改，请刷新后重试");
        }
        return mapper.findDefinition(draft.id());
    }

    @Override public List<VoucherCodeSummary> voucherCodes(Long memberId, String status, String keyword) {
        return mapper.findVoucherCodes(memberId, status, keyword);
    }
    @Override public Optional<VoucherCodeSummary> findVoucherCode(String code) {
        return Optional.ofNullable(mapper.findVoucherCodeByCode(code));
    }
    @Override public Optional<VoucherCodeSummary> findVoucherCode(long id) {
        return Optional.ofNullable(mapper.findVoucherCodeById(id));
    }
    @Override public List<VoucherCodeSummary> findIssueByKey(String key) { return mapper.findIssueByKey(key); }

    @Override @Transactional
    public List<VoucherCodeSummary> issue(VoucherIssueDraft draft) {
        List<VoucherCodeSummary> existing = findIssueByKey(draft.idempotencyKey());
        if (!existing.isEmpty()) return existing;
        long batchId = mapper.insertIssueBatch(draft);
        for (String code : draft.codes()) mapper.insertVoucherCode(batchId, code, draft);
        return findIssueByKey(draft.idempotencyKey());
    }

    @Override @Transactional
    public VoucherCodeSummary bind(VoucherBindCommand command) {
        Long existingId = mapper.findBoundCodeIdByKey(command.idempotencyKey());
        if (existingId != null) return mapper.findVoucherCodeById(existingId);
        if (mapper.bindVoucher(command) != 1) {
            throw new DuplicateResourceException("券码状态已变化，请刷新后重试");
        }
        mapper.insertBindLedger(numbers.ledgerNo(), command);
        return mapper.findVoucherCodeById(command.voucher().id());
    }

    @Override @Transactional
    public void consume(VoucherSettlementConsumption command) {
        if (mapper.redeemVoucher(command) != 1) {
            throw new DuplicateResourceException("代金券状态已变化，请重新试算");
        }
        long ledgerId = mapper.insertRedeemLedger(numbers.ledgerNo(), command);
        mapper.insertVoucherAssetUsage(command, ledgerId);
    }

    @Override @Transactional
    public void refund(VoucherRefundCommand command) {
        if (mapper.returnVoucher(command) != 1) {
            throw new DuplicateResourceException("代金券核销状态已变化，无法返券");
        }
        if (mapper.insertReturnLedger(numbers.ledgerNo(), command) != 1) {
            throw new DuplicateResourceException("代金券核销流水不匹配，无法返券");
        }
    }
}
