package cn.iocoder.yudao.module.member.api.user.dto;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 由管理端业务模块创建会员账号的请求。
 */
@Data
public class MemberUserCreateReqDTO {

    @NotBlank
    @Mobile
    private String mobile;

    @NotBlank
    @Size(max = 30)
    private String nickname;

    @Size(max = 30)
    private String name;

    private Integer sex;

    private LocalDateTime birthday;

    @Email
    @Size(max = 50)
    private String email;

    @Size(max = 50)
    private String registerIp;

    private Integer registerTerminal;

}
