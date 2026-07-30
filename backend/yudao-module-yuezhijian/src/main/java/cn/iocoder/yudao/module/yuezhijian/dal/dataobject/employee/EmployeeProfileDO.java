package cn.iocoder.yudao.module.yuezhijian.dal.dataobject.employee;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@TableName("yzj_employee_profile")
@KeySequence("yzj_employee_profile_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EmployeeProfileDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long userId;
    private String employeeNo;
    private Long primaryStoreDeptId;
    private LocalDate hireDate;
    private LocalDate leaveDate;
    private Boolean canService;
    private Boolean canSell;
    private String employmentStatus;
    private Integer version;

}
