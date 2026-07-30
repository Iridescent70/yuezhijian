package cn.iocoder.yudao.module.yuezhijian.controller.admin.employee;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.employee.vo.EmployeeProfileRespVO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.employee.vo.EmployeeProfileSaveReqVO;
import cn.iocoder.yudao.module.yuezhijian.service.employee.EmployeeProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 悦指间员工档案")
@RestController
@RequestMapping("/yuezhijian/employee-profile")
@Validated
public class EmployeeProfileController {

    @Resource
    private EmployeeProfileService employeeProfileService;

    @PutMapping("/save")
    @Operation(summary = "新增或更新员工业务档案")
    @PreAuthorize("@ss.hasPermission('yuezhijian:employee:update')")
    public CommonResult<EmployeeProfileRespVO> save(@Valid @RequestBody EmployeeProfileSaveReqVO reqVO) {
        return success(employeeProfileService.save(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "按系统用户获得员工业务档案")
    @Parameter(name = "userId", description = "芋道系统用户编号", required = true)
    @PreAuthorize("@ss.hasPermission('yuezhijian:employee:query')")
    public CommonResult<EmployeeProfileRespVO> get(@RequestParam("userId") Long userId) {
        return success(employeeProfileService.getByUserId(userId));
    }

    @GetMapping("/list")
    @Operation(summary = "获得当前数据范围内的员工档案")
    @PreAuthorize("@ss.hasPermission('yuezhijian:employee:query')")
    public CommonResult<List<EmployeeProfileRespVO>> getList() {
        return success(employeeProfileService.getList());
    }

}
