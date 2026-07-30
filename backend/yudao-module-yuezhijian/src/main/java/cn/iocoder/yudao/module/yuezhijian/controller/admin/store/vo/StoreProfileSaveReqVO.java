package cn.iocoder.yudao.module.yuezhijian.controller.admin.store.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 悦指间门店档案保存 Request VO")
@Data
public class StoreProfileSaveReqVO {

    @Schema(description = "芋道部门编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "门店部门不能为空")
    private Long deptId;

    @Schema(description = "门店编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "SH001")
    @NotBlank(message = "门店编码不能为空")
    @Size(max = 32, message = "门店编码长度不能超过32个字符")
    private String storeCode;

    @Size(max = 32, message = "门店等级长度不能超过32个字符")
    private String storeLevel;

    @Size(max = 32)
    private String province;
    @Size(max = 32)
    private String city;
    @Size(max = 32)
    private String district;
    @Size(max = 255)
    private String address;

    @DecimalMin(value = "-180", message = "经度不能小于-180")
    @DecimalMax(value = "180", message = "经度不能大于180")
    private BigDecimal longitude;

    @DecimalMin(value = "-90", message = "纬度不能小于-90")
    @DecimalMax(value = "90", message = "纬度不能大于90")
    private BigDecimal latitude;

    @Size(max = 2000, message = "营业时间配置过长")
    private String businessHoursJson;

    @Schema(description = "乐观锁版本；首次创建可不传")
    private Integer version;

}
