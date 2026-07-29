package com.yuezhijian.server.member;

import com.yuezhijian.server.common.PageResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryMemberRepository implements MemberRepository {
    private final AtomicLong ids = new AtomicLong(1003);
    private final List<MemoryMember> members = new ArrayList<>(List.of(
            new MemoryMember(
                    1001L, "M202607290001", "C202607290001", "林晓悦", "悦悦", "13800001001", "FEMALE",
                    LocalDate.of(1992, 5, 18), null, "STORE", 2L, 2L, null, "普通会员", false, "ACTIVE",
                    LocalDateTime.now().minusDays(3), LocalDateTime.now().minusMonths(8),
                    new BigDecimal("1280.00"), BigDecimal.ZERO, new BigDecimal("3000.00"), 860, 1260, 2,
                    List.of(new MemberTag(1L, "HIGH_VALUE", "高价值会员", "#c17b32", false))),
            new MemoryMember(
                    1002L, "M202607290002", "C202607290002", "周雨桐", null, "13900001002", "FEMALE",
                    LocalDate.of(1997, 11, 2), null, "IMPORT", 2L, 2L, null, "普通会员", false, "ACTIVE",
                    LocalDateTime.now().minusDays(18), LocalDateTime.now().minusMonths(4),
                    new BigDecimal("320.00"), BigDecimal.ZERO, new BigDecimal("800.00"), 120, 320, 1,
                    List.of(new MemberTag(3L, "FOLLOW_UP", "需要跟进", "#d14b4b", true))),
            new MemoryMember(
                    1003L, "M202607290003", "C202607290003", "陈安然", null, "13700001003", "UNKNOWN",
                    null, null, "MANUAL", 1L, 1L, null, "普通会员", false, "FROZEN",
                    LocalDateTime.now().minusDays(60), LocalDateTime.now().minusMonths(2),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0,
                    List.of())));

    @Override
    public synchronized PageResult<MemberSummary> search(MemberQuery query) {
        String keyword = query.keyword() == null ? null : query.keyword().toLowerCase(Locale.ROOT);
        List<MemoryMember> matched = members.stream()
                .filter(member -> keyword == null || member.matches(keyword))
                .filter(member -> query.storeId() == null || member.ownerStoreId() == query.storeId())
                .filter(member -> query.status() == null || member.status().equals(query.status()))
                .sorted(Comparator.comparingLong(MemoryMember::id).reversed())
                .toList();
        int from = Math.min(query.offset(), matched.size());
        int to = Math.min(from + query.size(), matched.size());
        List<MemberSummary> page = matched.subList(from, to).stream().map(this::toSummary).toList();
        return new PageResult<>(page, query.page(), query.size(), matched.size());
    }

    @Override
    public synchronized Optional<MemberDetail> findById(long id) {
        return members.stream().filter(member -> member.id() == id).findFirst().map(this::toDetail);
    }

    @Override
    public synchronized boolean existsByMobile(String normalizedMobile) {
        return members.stream().anyMatch(member -> member.mobile().equals(normalizedMobile));
    }

    @Override
    public synchronized CreatedMember create(CreateMemberCommand command) {
        long id = ids.incrementAndGet();
        MemoryMember member = new MemoryMember(
                id,
                command.memberNo(),
                command.membershipCardNo(),
                command.fullName(),
                command.nickname(),
                command.mobile(),
                command.gender(),
                command.birthday(),
                command.email(),
                command.sourceType(),
                command.joinStoreId(),
                command.ownerStoreId(),
                command.advisorEmployeeId(),
                "普通会员",
                false,
                "ACTIVE",
                null,
                LocalDateTime.now(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
                0,
                List.of(new MemberTag(1L, "NEW_MEMBER", "新会员", "#8f5267", false)));
        members.add(member);
        return new CreatedMember(id, command.memberNo(), command.membershipCardNo());
    }

    private MemberSummary toSummary(MemoryMember member) {
        return new MemberSummary(
                member.id(), member.memberNo(), member.fullName(), maskMobile(member.mobile()), member.gender(),
                member.levelName(), member.ownerStoreId(), storeName(member.ownerStoreId()), member.availableBalance(),
                member.availablePoints(), member.cardCount(), member.status(), member.lastVisitAt());
    }

    private MemberDetail toDetail(MemoryMember member) {
        return new MemberDetail(
                member.id(), member.memberNo(), member.membershipCardNo(), member.fullName(), member.nickname(),
                maskMobile(member.mobile()), member.gender(), member.birthday(), member.email(), member.sourceType(),
                member.joinStoreId(), storeName(member.joinStoreId()), member.ownerStoreId(),
                storeName(member.ownerStoreId()), member.advisorEmployeeId(), member.levelName(), member.special(),
                member.status(), member.lastVisitAt(), member.createdAt(),
                new MemberAssets(member.availableBalance(), member.frozenBalance(), member.totalRecharged(),
                        member.availablePoints(), member.lifetimePoints(), member.cardCount()),
                member.tags(), "memory-" + member.id());
    }

    private String maskMobile(String mobile) {
        return "*******" + mobile.substring(mobile.length() - 4);
    }

    private String storeName(long storeId) {
        return storeId == 1L ? "悦指间总部" : "悦指间示范店";
    }

    private record MemoryMember(
            long id,
            String memberNo,
            String membershipCardNo,
            String fullName,
            String nickname,
            String mobile,
            String gender,
            LocalDate birthday,
            String email,
            String sourceType,
            long joinStoreId,
            long ownerStoreId,
            Long advisorEmployeeId,
            String levelName,
            boolean special,
            String status,
            LocalDateTime lastVisitAt,
            LocalDateTime createdAt,
            BigDecimal availableBalance,
            BigDecimal frozenBalance,
            BigDecimal totalRecharged,
            int availablePoints,
            int lifetimePoints,
            int cardCount,
            List<MemberTag> tags) {
        boolean matches(String keyword) {
            return memberNo.toLowerCase(Locale.ROOT).contains(keyword)
                    || membershipCardNo.toLowerCase(Locale.ROOT).contains(keyword)
                    || fullName.toLowerCase(Locale.ROOT).contains(keyword)
                    || mobile.contains(keyword);
        }
    }
}
