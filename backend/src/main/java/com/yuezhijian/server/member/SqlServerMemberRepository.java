package com.yuezhijian.server.member;

import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.SensitiveDataCodec;
import com.yuezhijian.server.common.DuplicateResourceException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlserver")
public class SqlServerMemberRepository implements MemberRepository {
    private final MemberMapper mapper;
    private final SensitiveDataCodec codec;

    public SqlServerMemberRepository(MemberMapper mapper, SensitiveDataCodec codec) {
        this.mapper = mapper;
        this.codec = codec;
    }

    @Override
    public PageResult<MemberSummary> search(MemberQuery query) {
        MemberQuery protectedQuery = withMobileHash(query);
        List<MemberSummary> items = mapper.findPage(protectedQuery, protectedQuery.offset(), protectedQuery.size())
                .stream().map(this::toSummary).toList();
        return new PageResult<>(items, query.page(), query.size(), mapper.count(protectedQuery));
    }

    @Override
    public Optional<MemberDetail> findById(long id) {
        MemberRow row = mapper.findById(id);
        return row == null ? Optional.empty() : Optional.of(toDetail(row, mapper.findTags(id)));
    }

    @Override
    public boolean existsByMobile(String normalizedMobile) {
        return mapper.countByMobileHash(codec.searchableHash(normalizedMobile)) > 0;
    }

    @Override
    public boolean existsByMobileExcluding(String normalizedMobile, long memberId) {
        return mapper.countByMobileHashExcluding(codec.searchableHash(normalizedMobile), memberId) > 0;
    }

    @Override
    @Transactional
    public CreatedMember create(CreateMemberCommand command) {
        Long levelId = mapper.findDefaultLevelId();
        String last4 = command.mobile().substring(command.mobile().length() - 4);
        long memberId = mapper.insertMember(new NewMemberRow(
                command.memberNo(), command.fullName(), command.nickname(), command.gender(), command.birthday(),
                codec.encrypt(command.mobile()), codec.searchableHash(command.mobile()), last4,
                command.email(), command.sourceType(), command.joinStoreId(), command.ownerStoreId(),
                command.advisorEmployeeId(), levelId, command.createdBy()));
        mapper.insertMembershipCard(
                memberId, command.membershipCardNo(), command.joinStoreId(), command.createdBy());
        mapper.insertBalanceAccount(memberId);
        mapper.insertPointAccount(memberId);
        mapper.insertNewMemberTag(memberId, command.createdBy());
        return new CreatedMember(memberId, command.memberNo(), command.membershipCardNo());
    }

    @Override
    @Transactional
    public MemberDetail update(MemberUpdateCommand command) {
        String ciphertext = command.mobile() == null ? null : codec.encrypt(command.mobile());
        String mobileHash = command.mobile() == null ? null : codec.searchableHash(command.mobile());
        String last4 = command.mobile() == null ? null : command.mobile().substring(command.mobile().length() - 4);
        if (mapper.updateMember(command, ciphertext, mobileHash, last4, decodeVersion(command.version())) != 1) {
            throw stale();
        }
        return findById(command.id()).orElseThrow();
    }

    @Override
    @Transactional
    public MemberDetail changeStatus(MemberStatusCommand command) {
        if (mapper.changeStatus(command, decodeVersion(command.version())) != 1) throw stale();
        mapper.insertStatusLog(command);
        return findById(command.id()).orElseThrow();
    }

    @Override
    public List<MemberTagOption> tagOptions() {
        return mapper.findTagOptions();
    }

    @Override
    @Transactional
    public MemberDetail updateTags(MemberTagUpdateCommand command) {
        if (mapper.touchMember(command.memberId(), decodeVersion(command.version()), command.operatorId()) != 1) {
            throw stale();
        }
        if (!command.removeIds().isEmpty()) {
            mapper.removeTags(command.memberId(), command.removeIds(), command.operatorId());
        }
        if (!command.addIds().isEmpty()) {
            mapper.addTags(command.memberId(), command.addIds(), command.operatorId());
        }
        return findById(command.memberId()).orElseThrow();
    }

    @Override
    public boolean applyOwnership(long memberId, long oldStoreId, long newStoreId, long operatorId) {
        return mapper.applyOwnership(memberId, oldStoreId, newStoreId, operatorId) == 1;
    }

    private MemberQuery withMobileHash(MemberQuery query) {
        String hash = query.keyword() != null && query.keyword().matches("1[3-9]\\d{9}")
                ? codec.searchableHash(query.keyword())
                : null;
        return new MemberQuery(query.keyword(), query.storeId(), query.status(), query.page(), query.size(), hash);
    }

    private MemberSummary toSummary(MemberRow row) {
        return new MemberSummary(
                row.id(), row.memberNo(), row.fullName(), maskMobile(row.mobileLast4()), row.gender(),
                row.levelName(), row.ownerStoreId(), row.ownerStoreName(), row.availableBalance(),
                row.availablePoints(), row.cardCount(), row.status(), row.lastVisitAt());
    }

    private MemberDetail toDetail(MemberRow row, List<MemberTag> tags) {
        return new MemberDetail(
                row.id(), row.memberNo(), row.membershipCardNo(), row.fullName(), row.nickname(),
                maskMobile(row.mobileLast4()), row.gender(), row.birthday(), row.email(), row.sourceType(),
                row.joinStoreId(), row.joinStoreName(), row.ownerStoreId(), row.ownerStoreName(),
                row.advisorEmployeeId(), row.levelName(), row.specialFlag(), row.status(),
                row.frozenAt(), row.freezeReason(), row.lastVisitAt(),
                row.createdAt(), new MemberAssets(row.availableBalance(), row.frozenBalance(),
                        row.totalRecharged(), row.availablePoints(), row.lifetimePoints(), row.cardCount()),
                tags, Base64.getEncoder().encodeToString(row.rowVersion()));
    }

    private String maskMobile(String last4) {
        return "*******" + last4;
    }

    private byte[] decodeVersion(String version) {
        try {
            return Base64.getDecoder().decode(version);
        } catch (IllegalArgumentException exception) {
            throw stale();
        }
    }

    private DuplicateResourceException stale() {
        return new DuplicateResourceException("会员档案已被他人修改，请刷新后重试");
    }
}
