package com.yuezhijian.server.benefit;

import com.yuezhijian.server.common.DuplicateResourceException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryBenefitRepository implements BenefitRepository {
    private final Map<Long, VoucherDefinition> definitions = new LinkedHashMap<>();
    private final Map<Long, VoucherCodeSummary> codes = new LinkedHashMap<>();
    private final Map<String, List<Long>> issues = new LinkedHashMap<>();
    private final Map<String, Long> bindKeys = new LinkedHashMap<>();
    private final AtomicLong definitionIds = new AtomicLong(100);
    private final AtomicLong codeIds = new AtomicLong(1000);

    @Override
    public synchronized List<VoucherDefinition> definitions(String keyword, String status) {
        String value = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return definitions.values().stream()
                .filter(item -> value == null || item.code().toLowerCase(Locale.ROOT).contains(value)
                        || item.name().toLowerCase(Locale.ROOT).contains(value))
                .filter(item -> status == null || status.equals(item.status()))
                .sorted(Comparator.comparingLong(VoucherDefinition::id).reversed()).toList();
    }

    @Override public synchronized Optional<VoucherDefinition> findDefinition(long id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override public synchronized boolean existsDefinitionCode(String code) {
        return definitions.values().stream().anyMatch(item -> item.code().equalsIgnoreCase(code));
    }

    @Override public synchronized VoucherDefinition createDefinition(VoucherDefinition draft, long operatorId) {
        long id = definitionIds.incrementAndGet();
        VoucherDefinition saved = copyDefinition(draft, id, "1");
        definitions.put(id, saved);
        return saved;
    }

    @Override public synchronized VoucherDefinition updateDefinition(VoucherDefinition draft, long operatorId) {
        VoucherDefinition current = definitions.get(draft.id());
        if (current == null || !current.version().equals(draft.version())) {
            throw new DuplicateResourceException("代金券定义已被他人修改，请刷新后重试");
        }
        VoucherDefinition saved = copyDefinition(draft, draft.id(), nextVersion(draft.version()));
        definitions.put(saved.id(), saved);
        return saved;
    }

    @Override
    public synchronized List<VoucherCodeSummary> voucherCodes(Long memberId, String status, String keyword) {
        String value = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return codes.values().stream()
                .filter(item -> memberId == null || java.util.Objects.equals(memberId, item.memberId()))
                .filter(item -> status == null || status.equals(item.status()))
                .filter(item -> value == null || item.code().toLowerCase(Locale.ROOT).contains(value)
                        || item.voucherName().toLowerCase(Locale.ROOT).contains(value))
                .sorted(Comparator.comparingLong(VoucherCodeSummary::id).reversed()).toList();
    }

    @Override public synchronized Optional<VoucherCodeSummary> findVoucherCode(String code) {
        return codes.values().stream().filter(item -> item.code().equalsIgnoreCase(code)).findFirst();
    }

    @Override public synchronized Optional<VoucherCodeSummary> findVoucherCode(long id) {
        return Optional.ofNullable(codes.get(id));
    }

    @Override public synchronized List<VoucherCodeSummary> findIssueByKey(String key) {
        List<Long> ids = issues.get(key);
        return ids == null ? List.of() : ids.stream().map(codes::get).toList();
    }

    @Override public synchronized List<VoucherCodeSummary> issue(VoucherIssueDraft draft) {
        List<VoucherCodeSummary> existing = findIssueByKey(draft.idempotencyKey());
        if (!existing.isEmpty()) return existing;
        LocalDateTime boundAt = draft.memberId() == null ? null : LocalDateTime.now();
        List<VoucherCodeSummary> result = draft.codes().stream().map(code -> {
            long id = codeIds.incrementAndGet();
            VoucherDefinition definition = draft.definition();
            VoucherCodeSummary saved = new VoucherCodeSummary(
                    id, code, definition.id(), definition.code(), definition.name(), definition.benefitType(),
                    definition.faceAmount(), definition.discountRate(), definition.minSpend(), draft.memberId(),
                    draft.memberName(), draft.validFrom(), draft.validUntil(),
                    draft.memberId() == null ? "UNBOUND" : "BOUND", boundAt, null, null, "1");
            codes.put(id, saved);
            return saved;
        }).toList();
        issues.put(draft.idempotencyKey(), result.stream().map(VoucherCodeSummary::id).toList());
        return result;
    }

    @Override public synchronized VoucherCodeSummary bind(VoucherBindCommand command) {
        Long existingId = bindKeys.get(command.idempotencyKey());
        if (existingId != null) return codes.get(existingId);
        VoucherCodeSummary current = codes.get(command.voucher().id());
        if (current == null || !"UNBOUND".equals(current.status())
                || !current.version().equals(command.voucher().version())) {
            throw new DuplicateResourceException("券码状态已变化，请刷新后重试");
        }
        VoucherCodeSummary saved = copyCode(
                current, command.memberId(), command.memberName(), "BOUND", LocalDateTime.now(), null, null);
        codes.put(saved.id(), saved);
        bindKeys.put(command.idempotencyKey(), saved.id());
        return saved;
    }

    @Override public synchronized void consume(VoucherSettlementConsumption command) {
        VoucherCodeSummary current = codes.get(command.voucherCodeId());
        if (current == null || !"BOUND".equals(current.status()) || current.memberId() != command.memberId()
                || !current.version().equals(command.voucherVersion())) {
            throw new DuplicateResourceException("代金券状态已变化，请重新试算");
        }
        codes.put(current.id(), copyCode(
                current, current.memberId(), current.memberName(), "REDEEMED",
                current.boundAt(), LocalDateTime.now(), command.billId()));
    }

    @Override public synchronized void refund(VoucherRefundCommand command) {
        VoucherCodeSummary current = codes.get(command.voucherCodeId());
        if (current == null || !"REDEEMED".equals(current.status())
                || !java.util.Objects.equals(current.redeemedBillId(), command.billId())) {
            throw new DuplicateResourceException("代金券核销状态已变化，无法返券");
        }
        codes.put(current.id(), copyCode(
                current, current.memberId(), current.memberName(), "BOUND", current.boundAt(), null, null));
    }

    private VoucherDefinition copyDefinition(VoucherDefinition source, long id, String version) {
        return new VoucherDefinition(
                id, source.code(), source.name(), source.benefitType(), source.faceAmount(), source.discountRate(),
                source.minSpend(), source.validDays(), source.commissionRule(), source.status(), version);
    }

    private VoucherCodeSummary copyCode(
            VoucherCodeSummary source, Long memberId, String memberName, String status,
            LocalDateTime boundAt, LocalDateTime redeemedAt, Long billId) {
        return new VoucherCodeSummary(
                source.id(), source.code(), source.voucherId(), source.voucherCode(), source.voucherName(),
                source.benefitType(), source.faceAmount(), source.discountRate(), source.minSpend(), memberId,
                memberName, source.validFrom(), source.validUntil(), status, boundAt, redeemedAt, billId,
                nextVersion(source.version()));
    }

    private String nextVersion(String version) { return Long.toString(Long.parseLong(version) + 1); }
}
