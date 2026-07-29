package com.yuezhijian.server.audit;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.masterdata.MasterDataService;
import com.yuezhijian.server.product.ProductService;
import java.util.List;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operation-history")
public class OperationHistoryController {
    private final AuditService auditService;
    private final ProductService productService;
    private final MasterDataService masterDataService;

    public OperationHistoryController(
            AuditService auditService,
            ProductService productService,
            MasterDataService masterDataService) {
        this.auditService = auditService;
        this.productService = productService;
        this.masterDataService = masterDataService;
    }

    @GetMapping("/{objectType}/{objectId}")
    @PreAuthorize("(#objectType.equalsIgnoreCase('PRODUCT') and hasAuthority('catalog:product:view')) or "
            + "(#objectType.equalsIgnoreCase('SERVICE') and hasAuthority('catalog:service:view'))")
    public ApiResponse<List<OperationHistoryItem>> history(
            @PathVariable String objectType, @PathVariable long objectId) {
        String type = objectType.toUpperCase(Locale.ROOT);
        List<Long> stores = switch (type) {
            case "PRODUCT" -> productService.product(objectId).stores().stream()
                    .map(store -> store.storeId()).toList();
            case "SERVICE" -> masterDataService.service(objectId).stores().stream()
                    .map(store -> store.storeId()).toList();
            default -> throw new IllegalArgumentException("不支持的操作历史对象类型");
        };
        return ApiResponse.ok(auditService.history(type, objectId, stores));
    }
}
