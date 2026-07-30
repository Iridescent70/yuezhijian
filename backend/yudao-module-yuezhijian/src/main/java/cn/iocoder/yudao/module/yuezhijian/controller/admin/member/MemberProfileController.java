package cn.iocoder.yudao.module.yuezhijian.controller.admin.member;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo.MemberProfileCreateReqVO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo.MemberProfilePageReqVO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo.MemberProfileRespVO;
import cn.iocoder.yudao.module.yuezhijian.service.member.MemberProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 悦指间会员主档")
@RestController
@RequestMapping("/yuezhijian/member")
@Validated
public class MemberProfileController {

    @Resource
    private MemberProfileService memberProfileService;

    @PostMapping("/create")
    @Operation(summary = "创建会员账号、主档和会员卡")
    @PreAuthorize("@ss.hasPermission('yuezhijian:member:create')")
    public CommonResult<MemberProfileRespVO> create(@Valid @RequestBody MemberProfileCreateReqVO reqVO) {
        return success(memberProfileService.create(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得会员聚合主档")
    @Parameter(name = "id", description = "悦指间会员主档编号", required = true)
    @PreAuthorize("@ss.hasPermission('yuezhijian:member:query')")
    public CommonResult<MemberProfileRespVO> get(@RequestParam("id") Long id) {
        return success(memberProfileService.get(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得当前数据范围内的会员主档分页")
    @PreAuthorize("@ss.hasPermission('yuezhijian:member:query')")
    public CommonResult<PageResult<MemberProfileRespVO>> getPage(@Valid MemberProfilePageReqVO reqVO) {
        return success(memberProfileService.getPage(reqVO));
    }

}
