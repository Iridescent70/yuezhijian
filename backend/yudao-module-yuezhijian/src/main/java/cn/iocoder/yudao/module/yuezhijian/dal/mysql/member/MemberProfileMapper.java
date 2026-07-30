package cn.iocoder.yudao.module.yuezhijian.dal.mysql.member;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.framework.security.MemberMobileProtectionUtils;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo.MemberProfilePageReqVO;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.member.MemberProfileDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberProfileMapper extends BaseMapperX<MemberProfileDO> {

    default MemberProfileDO selectByMemberUserId(Long memberUserId) {
        return selectOne(MemberProfileDO::getMemberUserId, memberUserId);
    }

    default MemberProfileDO selectByMobileHash(String mobileHash) {
        return selectOne(MemberProfileDO::getMobileHash, mobileHash);
    }

    default PageResult<MemberProfileDO> selectPage(MemberProfilePageReqVO reqVO) {
        String mobileHash = StrUtil.isBlank(reqVO.getMobile()) ? null
                : MemberMobileProtectionUtils.searchableHash(reqVO.getMobile());
        return selectPage(reqVO, new LambdaQueryWrapperX<MemberProfileDO>()
                .likeIfPresent(MemberProfileDO::getMemberNo, reqVO.getMemberNo())
                .likeIfPresent(MemberProfileDO::getFullName, reqVO.getFullName())
                .eqIfPresent(MemberProfileDO::getMobileHash, mobileHash)
                .eqIfPresent(MemberProfileDO::getOwnerStoreDeptId, reqVO.getOwnerStoreDeptId())
                .eqIfPresent(MemberProfileDO::getLifecycleStatus, reqVO.getLifecycleStatus())
                .orderByDesc(MemberProfileDO::getId));
    }

}
