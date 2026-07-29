package com.yuezhijian.server.member;

import com.yuezhijian.server.common.PageResult;
import java.util.Optional;

public interface MemberRepository {
    PageResult<MemberSummary> search(MemberQuery query);

    Optional<MemberDetail> findById(long id);

    boolean existsByMobile(String normalizedMobile);

    CreatedMember create(CreateMemberCommand command);
}
