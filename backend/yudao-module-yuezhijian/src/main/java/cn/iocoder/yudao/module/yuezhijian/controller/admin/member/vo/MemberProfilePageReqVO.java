package cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.validation.Mobile;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MemberProfilePageReqVO extends PageParam {

    private String memberNo;
    private String fullName;

    @Mobile
    private String mobile;

    private Long ownerStoreDeptId;

    @Pattern(regexp = "ACTIVE|FROZEN|LOST", message = "会员生命周期状态不正确")
    private String lifecycleStatus;

}
