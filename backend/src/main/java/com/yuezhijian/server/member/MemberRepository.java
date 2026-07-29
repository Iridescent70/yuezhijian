package com.yuezhijian.server.member;

import com.yuezhijian.server.common.PageResult;
import java.util.List;
import java.util.Optional;

public interface MemberRepository {
    PageResult<MemberSummary> search(MemberQuery query);

    Optional<MemberDetail> findById(long id);

    boolean existsByMobile(String normalizedMobile);

    boolean existsByMobileExcluding(String normalizedMobile, long memberId);

    CreatedMember create(CreateMemberCommand command);

    MemberDetail update(MemberUpdateCommand command);

    MemberDetail changeStatus(MemberStatusCommand command);

    List<MemberTagOption> tagOptions();

    MemberDetail updateTags(MemberTagUpdateCommand command);

    boolean applyOwnership(long memberId, long oldStoreId, long newStoreId, long operatorId);
}
