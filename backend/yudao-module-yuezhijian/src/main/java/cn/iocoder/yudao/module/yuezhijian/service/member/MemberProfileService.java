package cn.iocoder.yudao.module.yuezhijian.service.member;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo.MemberProfileCreateReqVO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo.MemberProfilePageReqVO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo.MemberProfileRespVO;

public interface MemberProfileService {

    MemberProfileRespVO create(MemberProfileCreateReqVO reqVO);

    MemberProfileRespVO get(Long id);

    PageResult<MemberProfileRespVO> getPage(MemberProfilePageReqVO reqVO);

}
