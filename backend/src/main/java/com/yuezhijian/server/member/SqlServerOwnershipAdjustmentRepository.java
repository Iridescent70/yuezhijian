package com.yuezhijian.server.member;

import com.yuezhijian.server.common.DuplicateResourceException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("sqlserver")
public class SqlServerOwnershipAdjustmentRepository implements OwnershipAdjustmentRepository {
    private final OwnershipAdjustmentMapper mapper;

    public SqlServerOwnershipAdjustmentRepository(OwnershipAdjustmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<OwnershipAdjustmentRow> search(OwnershipAdjustmentQuery query) {
        return mapper.search(query);
    }

    @Override
    public Optional<OwnershipAdjustmentRow> findById(long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public boolean hasActiveAdjustment(long memberId) {
        return mapper.countActive(memberId) > 0;
    }

    @Override
    public OwnershipAdjustmentRow create(OwnershipAdjustmentDraft draft) {
        Long id;
        try {
            id = mapper.insert(draft, Base64.getDecoder().decode(draft.memberVersion()));
        } catch (IllegalArgumentException exception) {
            throw staleMember();
        }
        if (id == null) throw staleMember();
        return findById(id).orElseThrow();
    }

    @Override
    public OwnershipAdjustmentRow review(
            long id, boolean approved, String comment, String version, long operatorId) {
        if (mapper.review(id, approved, comment, version, operatorId) != 1) throw staleAdjustment();
        return findById(id).orElseThrow();
    }

    @Override
    public List<OwnershipAdjustmentRow> due(LocalDate businessDate) {
        return mapper.findDue(businessDate);
    }

    @Override
    public Optional<OwnershipAdjustmentRow> claim(long id, String version, LocalDate businessDate) {
        if (mapper.claim(id, version, businessDate) != 1) return Optional.empty();
        return findById(id);
    }

    @Override
    public OwnershipAdjustmentRow finish(long id, boolean applied, String message, String version) {
        if (mapper.finish(id, applied, message, version) != 1) throw staleAdjustment();
        return findById(id).orElseThrow();
    }

    private DuplicateResourceException staleMember() {
        return new DuplicateResourceException("会员归属已变化或已有待处理申请，请刷新后重试");
    }

    private DuplicateResourceException staleAdjustment() {
        return new DuplicateResourceException("归属调整已被他人处理，请刷新后重试");
    }
}
