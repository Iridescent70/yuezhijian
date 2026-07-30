package cn.iocoder.yudao.module.yuezhijian.controller.admin.store.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 悦指间门店档案 Response VO")
@Data
public class StoreProfileRespVO {

    private Long id;
    private Long deptId;
    private String deptName;
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
