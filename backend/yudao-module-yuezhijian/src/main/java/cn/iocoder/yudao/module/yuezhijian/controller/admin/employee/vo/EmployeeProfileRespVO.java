package cn.iocoder.yudao.module.yuezhijian.controller.admin.employee.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class EmployeeProfileRespVO {

    private Long id;
    private Long userId;
    private String nickname;
    private Set<Long> postIds;
    private String employeeNo;
    private Long primaryStoreDeptId;
    private String primaryStoreName;
    private LocalDate hireDate;
    private LocalDate leaveDate;
    private Boolean canService;
    private Boolean canSell;
    private String employmentStatus;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
