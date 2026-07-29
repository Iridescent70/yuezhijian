package com.yuezhijian.server.banner;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateBannerRequest(
        @NotBlank String positionCode,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String linkType,
        @Size(max = 500) String linkValue,
        @Min(0) @Max(9999) int sortNo,
        LocalDateTime validFrom,
        LocalDateTime validTo) {
}
