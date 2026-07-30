package cn.iocoder.yudao.module.yuezhijian.dal.mysql.member;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.member.MembershipCardDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MembershipCardMapper extends BaseMapperX<MembershipCardDO> {

    default MembershipCardDO selectActiveByMemberProfileId(Long memberProfileId) {
        return selectOne(MembershipCardDO::getMemberProfileId, memberProfileId,
                MembershipCardDO::getStatus, "ACTIVE");
    }

    default MembershipCardDO selectByCardNo(String cardNo) {
        return selectOne(MembershipCardDO::getCardNo, cardNo);
    }

}
