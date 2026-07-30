package cn.iocoder.yudao.module.yuezhijian.controller.admin.store;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.store.vo.StoreProfileRespVO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.store.vo.StoreProfileSaveReqVO;
import cn.iocoder.yudao.module.yuezhijian.service.store.StoreProfileService;
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

@Tag(name = "管理后台 - 悦指间门店档案")
@RestController
@RequestMapping("/yuezhijian/store-profile")
@Validated
public class StoreProfileController {

    @Resource
    private StoreProfileService storeProfileService;

    @PutMapping("/save")
    @Operation(summary = "新增或更新门店业务档案")
    @PreAuthorize("@ss.hasPermission('yuezhijian:store:update')")
    public CommonResult<StoreProfileRespVO> save(@Valid @RequestBody StoreProfileSaveReqVO reqVO) {
        return success(storeProfileService.save(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "按部门获得门店业务档案")
    @Parameter(name = "deptId", description = "芋道部门编号", required = true)
    @PreAuthorize("@ss.hasPermission('yuezhijian:store:query')")
    public CommonResult<StoreProfileRespVO> get(@RequestParam("deptId") Long deptId) {
        return success(storeProfileService.getByDeptId(deptId));
    }

    @GetMapping("/list")
    @Operation(summary = "获得当前数据范围内的门店档案")
    @PreAuthorize("@ss.hasPermission('yuezhijian:store:query')")
    public CommonResult<List<StoreProfileRespVO>> getList() {
        return success(storeProfileService.getList());
    }

}
