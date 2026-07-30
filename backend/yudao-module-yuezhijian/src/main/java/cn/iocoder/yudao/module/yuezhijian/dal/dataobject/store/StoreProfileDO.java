package cn.iocoder.yudao.module.yuezhijian.dal.dataobject.store;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("yzj_store_profile")
@KeySequence("yzj_store_profile_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreProfileDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long deptId;
    private String storeCode;
    private String storeLevel;
    private String province;
    private String city;
    private String district;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String businessHoursJson;
    private Integer version;

}
