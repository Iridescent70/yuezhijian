package cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MemberProfileRespVO {

    private Long id;
    private Long memberUserId;
    private String memberNo;
    private String fullName;
    private String maskedMobile;
    private String membershipCardNo;
    private Long joinStoreDeptId;
    private String joinStoreName;
    private Long ownerStoreDeptId;
    private String ownerStoreName;
    private Long advisorUserId;
    private String advisorName;
    private String sourceType;
    private Boolean special;
    private String lifecycleStatus;
    private LocalDateTime frozenAt;
    private String freezeReason;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
