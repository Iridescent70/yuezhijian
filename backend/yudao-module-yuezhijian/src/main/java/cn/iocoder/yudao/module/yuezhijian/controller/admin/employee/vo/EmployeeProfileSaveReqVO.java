package cn.iocoder.yudao.module.yuezhijian.controller.admin.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 悦指间员工档案保存 Request VO")
@Data
public class EmployeeProfileSaveReqVO {

    @NotNull(message = "系统用户不能为空")
    private Long userId;

    @NotBlank(message = "员工编号不能为空")
    @Size(max = 32, message = "员工编号长度不能超过32个字符")
    private String employeeNo;

    @NotNull(message = "主门店不能为空")
    private Long primaryStoreDeptId;

    private LocalDate hireDate;
    private LocalDate leaveDate;

    @NotNull(message = "是否可服务不能为空")
    private Boolean canService;

    @NotNull(message = "是否可销售不能为空")
    private Boolean canSell;

    @NotBlank(message = "在职状态不能为空")
    @Pattern(regexp = "ACTIVE|LEAVE", message = "在职状态只能是 ACTIVE 或 LEAVE")
    private String employmentStatus;

    @Schema(description = "乐观锁版本；首次创建可不传")
    private Integer version;

}
