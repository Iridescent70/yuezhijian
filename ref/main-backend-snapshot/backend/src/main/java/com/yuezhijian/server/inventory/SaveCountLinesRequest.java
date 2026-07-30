package com.yuezhijian.server.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveCountLinesRequest(
        @NotEmpty @Size(max = 500) List<@Valid CountLineInput> lines,
        @NotNull String version) {
}
