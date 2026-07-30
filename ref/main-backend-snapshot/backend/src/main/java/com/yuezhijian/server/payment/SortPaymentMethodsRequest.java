package com.yuezhijian.server.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SortPaymentMethodsRequest(
        @Positive long storeId,
        @NotEmpty @Size(max = 100) List<@Valid PaymentMethodSortItemRequest> items) {
}
