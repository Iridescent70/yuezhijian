package com.yuezhijian.server.member;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MemberService {
    private final MemberRepository repository;
    private final BusinessNumberGenerator numberGenerator;
    private final AccessCatalogService accessCatalog;

    public MemberService(
            MemberRepository repository,
            BusinessNumberGenerator numberGenerator,
            AccessCatalogService accessCatalog) {
        this.repository = repository;
        this.numberGenerator = numberGenerator;
        this.accessCatalog = accessCatalog;
    }

    public PageResult<MemberSummary> search(String keyword, Long storeId, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = blankToNull(keyword);
        String normalizedStatus = blankToNull(status);
        if (normalizedStatus != null) {
            normalizedStatus = normalizedStatus.toUpperCase(Locale.ROOT);
            if (!java.util.Set.of("ACTIVE", "FROZEN", "INACTIVE").contains(normalizedStatus)) {
                throw new IllegalArgumentException("会员状态不正确");
            }
        }
        return repository.search(new MemberQuery(
                normalizedKeyword, storeId, normalizedStatus, safePage, safeSize, null));
    }

    public MemberDetail detail(long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("会员不存在"));
    }

    public CreatedMember create(CreateMemberRequest request, String username) {
        String mobile = normalizeMobile(request.mobile());
        if (repository.existsByMobile(mobile)) {
            throw new DuplicateResourceException("该手机号已经存在会员档案");
        }
        validateStore(request.joinStoreId());
        long ownerStoreId = request.ownerStoreId() == null ? request.joinStoreId() : request.ownerStoreId();
        validateStore(ownerStoreId);
        long createdBy = accessCatalog.userIdentity(username).id();
        return repository.create(new CreateMemberCommand(
                numberGenerator.nextMemberNo(),
                blankToNull(request.membershipCardNo()) == null
                        ? numberGenerator.nextMembershipCardNo()
                        : request.membershipCardNo().trim(),
                request.fullName().trim(),
                blankToNull(request.nickname()),
                mobile,
                request.gender() == null ? "UNKNOWN" : request.gender().toUpperCase(Locale.ROOT),
                request.birthday(),
                blankToNull(request.email()),
                request.sourceType() == null ? "MANUAL" : request.sourceType().toUpperCase(Locale.ROOT),
                request.joinStoreId(),
                ownerStoreId,
                request.advisorEmployeeId(),
                createdBy));
    }

    private void validateStore(long storeId) {
        boolean exists = accessCatalog.stores().stream()
                .anyMatch(store -> store.id() == storeId && "ACTIVE".equals(store.status()));
        if (!exists) {
            throw new IllegalArgumentException("所选门店不存在或已停用");
        }
    }

    private String normalizeMobile(String mobile) {
        String normalized = mobile == null ? "" : mobile.replaceAll("[\\s-]", "");
        if (!normalized.matches("1[3-9]\\d{9}")) {
            throw new IllegalArgumentException("请输入正确的11位手机号");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
