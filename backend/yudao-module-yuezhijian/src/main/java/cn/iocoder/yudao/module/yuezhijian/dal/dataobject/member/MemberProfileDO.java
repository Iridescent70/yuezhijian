package cn.iocoder.yudao.module.yuezhijian.dal.dataobject.member;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("yzj_member_profile")
@KeySequence("yzj_member_profile_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberProfileDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long memberUserId;
    private String memberNo;
    private String fullName;
    private String mobileHash;
    private String mobileLast4;
    private Long joinStoreDeptId;
    private Long ownerStoreDeptId;
    private Long advisorUserId;
    private String sourceType;
    private Boolean special;
    private String lifecycleStatus;
    private LocalDateTime frozenAt;
    private String freezeReason;
    @Version
    private Integer version;

}
