package com.yuezhijian.server.member;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.masterdata.MasterDataService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {
    private final MemberRepository repository;
    private final BusinessNumberGenerator numberGenerator;
    private final AccessCatalogService accessCatalog;
    private final MasterDataService masterData;

    public MemberService(
            MemberRepository repository,
            BusinessNumberGenerator numberGenerator,
            AccessCatalogService accessCatalog,
            MasterDataService masterData) {
        this.repository = repository;
        this.numberGenerator = numberGenerator;
        this.accessCatalog = accessCatalog;
        this.masterData = masterData;
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
        validateAdvisor(request.advisorEmployeeId(), ownerStoreId);
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

    @Transactional
    public MemberDetail update(long id, UpdateMemberRequest request, String username) {
        MemberDetail current = detail(id);
        String mobile = blankToNull(request.mobile());
        if (mobile != null) {
            mobile = normalizeMobile(mobile);
            if (repository.existsByMobileExcluding(mobile, id)) {
                throw new DuplicateResourceException("该手机号已经存在会员档案");
            }
        }
        String gender = request.gender().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("UNKNOWN", "FEMALE", "MALE", "OTHER").contains(gender)) {
            throw new IllegalArgumentException("会员性别不正确");
        }
        if (!java.util.Objects.equals(request.advisorEmployeeId(), current.advisorEmployeeId())) {
            validateAdvisor(request.advisorEmployeeId(), current.ownerStoreId());
        }
        return repository.update(new MemberUpdateCommand(
                id, request.fullName().trim(), blankToNull(request.nickname()), mobile, gender,
                request.birthday(), blankToNull(request.email()), request.advisorEmployeeId(),
                request.special(), request.version(), currentUserId(username)));
    }

    @Transactional
    public MemberDetail changeStatus(long id, ChangeMemberStatusRequest request, String username) {
        MemberDetail current = detail(id);
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "FROZEN", "INACTIVE").contains(status)) {
            throw new IllegalArgumentException("会员状态不正确");
        }
        if (status.equals(current.status())) {
            throw new IllegalArgumentException("会员已经处于该状态");
        }
        return repository.changeStatus(new MemberStatusCommand(
                id, current.status(), status, request.reason().trim(), request.version(), currentUserId(username)));
    }

    public List<MemberTagOption> tagOptions() {
        return repository.tagOptions();
    }

    @Transactional
    public MemberDetail updateTags(long id, UpdateMemberTagsRequest request, String username) {
        detail(id);
        List<Long> addIds = new LinkedHashSet<>(request.addIds()).stream().toList();
        List<Long> removeIds = new LinkedHashSet<>(request.removeIds()).stream().toList();
        if (addIds.stream().anyMatch(removeIds::contains)) {
            throw new IllegalArgumentException("同一标签不能同时添加和移除");
        }
        Set<Long> available = repository.tagOptions().stream()
                .map(MemberTagOption::id).collect(java.util.stream.Collectors.toSet());
        if (!available.containsAll(addIds) || !available.containsAll(removeIds)) {
            throw new IllegalArgumentException("标签不存在或已停用");
        }
        return repository.updateTags(new MemberTagUpdateCommand(
                id, addIds, removeIds, request.version(), currentUserId(username)));
    }

    @Transactional
    public MemberDetail assignAdvisor(long id, long employeeId, String version, String username) {
        MemberDetail current = detail(id);
        if (!current.version().equals(version)) {
            throw new DuplicateResourceException("会员档案已被他人修改，请刷新后重试");
        }
        if (java.util.Objects.equals(current.advisorEmployeeId(), employeeId)) {
            throw new IllegalArgumentException("会员已经由该顾问负责");
        }
        validateAdvisor(employeeId, current.ownerStoreId());
        return repository.assignAdvisor(new MemberAdvisorCommand(
                id, current.ownerStoreId(), current.advisorEmployeeId(), employeeId,
                version, currentUserId(username), "BATCH_ASSIGN"));
    }

    private void validateStore(long storeId) {
        boolean exists = accessCatalog.stores().stream()
                .anyMatch(store -> store.id() == storeId && "ACTIVE".equals(store.status()));
        if (!exists) {
            throw new IllegalArgumentException("所选门店不存在或已停用");
        }
    }

    private void validateAdvisor(Long employeeId, long ownerStoreId) {
        if (employeeId == null) return;
        boolean valid = masterData.employees(ownerStoreId, null).stream()
                .anyMatch(employee -> employee.id() == employeeId && "ACTIVE".equals(employee.status()));
        if (!valid) throw new IllegalArgumentException("所选顾问不存在、已停用或不属于归属门店");
    }

    private long currentUserId(String username) {
        return accessCatalog.userIdentity(username).id();
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
