package com.yuezhijian.server.benefit;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.member.MemberDetail;
import com.yuezhijian.server.member.MemberRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BenefitService {
    private static final Set<String> DEFINITION_STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> CODE_STATUSES = Set.of("UNBOUND", "BOUND", "REDEEMED", "EXPIRED", "VOIDED");

    private final BenefitRepository repository;
    private final MemberRepository members;
    private final AccessCatalogService accessCatalog;
    private final BenefitNumberGenerator numbers;

    public BenefitService(
            BenefitRepository repository,
            MemberRepository members,
            AccessCatalogService accessCatalog,
            BenefitNumberGenerator numbers) {
        this.repository = repository;
        this.members = members;
        this.accessCatalog = accessCatalog;
        this.numbers = numbers;
    }

    public List<VoucherDefinition> definitions(String keyword, String status) {
        return repository.definitions(trimToNull(keyword), normalize(status, DEFINITION_STATUSES, "代金券状态无效"));
    }

    public VoucherDefinition definition(long id) {
        return repository.findDefinition(id).orElseThrow(() -> new ResourceNotFoundException("代金券定义不存在"));
    }

    public VoucherDefinition createDefinition(CreateVoucherDefinitionRequest request, String username) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsDefinitionCode(code)) throw new DuplicateResourceException("代金券编码已存在");
        VoucherDefinition draft = normalizedDefinition(
                0, code, request.name(), request.benefitType(), request.faceAmount(), request.discountRate(),
                request.minSpend(), request.validDays(), request.commissionRule(), "ACTIVE", null);
        return repository.createDefinition(draft, currentUserId(username));
    }

    public VoucherDefinition updateDefinition(long id, UpdateVoucherDefinitionRequest request, String username) {
        VoucherDefinition current = definition(id);
        String status = normalize(request.status(), DEFINITION_STATUSES, "代金券状态无效");
        if (!current.version().equals(request.version())) {
            throw new DuplicateResourceException("代金券定义已被他人修改，请刷新后重试");
        }
        VoucherDefinition draft = normalizedDefinition(
                id, current.code(), request.name(), request.benefitType(), request.faceAmount(), request.discountRate(),
                request.minSpend(), request.validDays(), request.commissionRule(), status, request.version());
        return repository.updateDefinition(draft, currentUserId(username));
    }

    public List<VoucherCodeSummary> voucherCodes(Long memberId, String status, String keyword) {
        return repository.voucherCodes(memberId, normalize(status, CODE_STATUSES, "券码状态无效"), trimToNull(keyword));
    }

    public VoucherCodeSummary voucherCode(String code) {
        return repository.findVoucherCode(normalizeCode(code))
                .orElseThrow(() -> new ResourceNotFoundException("券码不存在"));
    }

    @Transactional
    public List<VoucherCodeSummary> issue(IssueVoucherCodesRequest request, String username) {
        String key = request.idempotencyKey().trim();
        List<VoucherCodeSummary> existing = repository.findIssueByKey(key);
        if (!existing.isEmpty()) {
            if (existing.size() != request.count() || existing.getFirst().voucherId() != request.voucherId()
                    || !java.util.Objects.equals(existing.getFirst().memberId(), request.memberId())) {
                throw new IllegalArgumentException("幂等键已用于其他发券任务");
            }
            return existing;
        }
        VoucherDefinition definition = definition(request.voucherId());
        if (!"ACTIVE".equals(definition.status())) throw new IllegalArgumentException("代金券定义已停用");
        MemberDetail member = request.memberId() == null ? null : requireMember(request.memberId());
        LocalDateTime validFrom = LocalDateTime.now();
        List<String> codes = new ArrayList<>();
        for (int index = 0; index < request.count(); index++) codes.add(numbers.voucherCode());
        return repository.issue(new VoucherIssueDraft(
                numbers.issueBatchNo(), definition, request.count(), member == null ? null : member.id(),
                member == null ? null : member.fullName(), validFrom, validFrom.plusDays(definition.validDays()),
                codes, key, currentUserId(username)));
    }

    @Transactional
    public VoucherCodeSummary bind(String code, BindVoucherCodeRequest request, String username) {
        VoucherCodeSummary voucher = voucherCode(code);
        MemberDetail member = requireMember(request.memberId());
        if ("BOUND".equals(voucher.status()) && java.util.Objects.equals(voucher.memberId(), member.id())) return voucher;
        if (!"UNBOUND".equals(voucher.status())) throw new IllegalArgumentException("当前券码不能绑定");
        LocalDateTime now = LocalDateTime.now();
        if (voucher.validFrom().isAfter(now) || voucher.validUntil().isBefore(now)) {
            throw new IllegalArgumentException("券码不在有效期内");
        }
        return repository.bind(new VoucherBindCommand(
                voucher, member.id(), member.fullName(), request.idempotencyKey().trim(), currentUserId(username)));
    }

    private VoucherDefinition normalizedDefinition(
            long id, String code, String name, String typeValue, BigDecimal faceValue, BigDecimal rateValue,
            BigDecimal minSpendValue, int validDays, String commissionRule, String status, String version) {
        String type = typeValue.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("FIXED_AMOUNT", "DISCOUNT").contains(type)) {
            throw new IllegalArgumentException("权益类型只支持金额券或折扣券");
        }
        BigDecimal face = money(faceValue);
        BigDecimal rate = rateValue.setScale(6, RoundingMode.HALF_UP);
        if ("FIXED_AMOUNT".equals(type)) {
            if (face.signum() <= 0) throw new IllegalArgumentException("金额券面额必须大于0");
            rate = BigDecimal.ONE.setScale(6);
        } else {
            if (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalArgumentException("折扣券折扣率必须大于0且小于1");
            }
            face = BigDecimal.ZERO.setScale(4);
        }
        return new VoucherDefinition(
                id, code, name.trim(), type, face, rate, money(minSpendValue), validDays,
                trimToNull(commissionRule), status, version);
    }

    private MemberDetail requireMember(long id) {
        MemberDetail member = members.findById(id).orElseThrow(() -> new ResourceNotFoundException("会员不存在"));
        if (!"ACTIVE".equals(member.status())) throw new IllegalArgumentException("会员当前状态不能绑定代金券");
        return member;
    }

    private String normalizeCode(String code) {
        String value = trimToNull(code);
        if (value == null) throw new IllegalArgumentException("券码不能为空");
        return value.toUpperCase(Locale.ROOT);
    }

    private String normalize(String value, Set<String> allowed, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) return null;
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private long currentUserId(String username) { return accessCatalog.userIdentity(username).id(); }
    private BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
