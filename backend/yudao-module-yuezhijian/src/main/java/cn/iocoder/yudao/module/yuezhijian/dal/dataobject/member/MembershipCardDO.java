package cn.iocoder.yudao.module.yuezhijian.dal.dataobject.member;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("yzj_membership_card")
@KeySequence("yzj_membership_card_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MembershipCardDO extends BaseDO {

    @TableId
    private Long id;
    private Long memberProfileId;
    private String cardNo;
    private Long registerStoreDeptId;
    private String status;

}
