package cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 悦指间会员建档 Request VO")
@Data
public class MemberProfileCreateReqVO {

    @NotBlank(message = "会员姓名不能为空")
    @Size(max = 30, message = "会员姓名长度不能超过30个字符")
    private String fullName;

    @Size(max = 30, message = "会员昵称长度不能超过30个字符")
    private String nickname;

    @NotBlank(message = "手机号不能为空")
    @Mobile
    private String mobile;

    private Integer sex;
    private LocalDate birthday;

    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过50个字符")
    private String email;

    @NotNull(message = "入会门店不能为空")
    private Long joinStoreDeptId;

    @Schema(description = "归属门店；不传时默认等于入会门店")
    private Long ownerStoreDeptId;

    private Long advisorUserId;

    @Pattern(regexp = "MANUAL|IMPORT|ONLINE|REFERRAL", message = "会员来源不正确")
    private String sourceType;

    @Size(max = 64, message = "会员卡号长度不能超过64个字符")
    private String membershipCardNo;

}
