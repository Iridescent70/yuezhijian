package com.yuezhijian.server.banner;

import com.yuezhijian.server.audit.AuditService;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.file.FileObjectItem;
import com.yuezhijian.server.file.FileObjectService;
import com.yuezhijian.server.file.StoredFileDownload;
import com.yuezhijian.server.iam.AccessCatalogService;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BannerService {
    private static final String IMAGE_PURPOSE = "BANNER_IMAGE";
    private static final Set<String> POSITION_CODES = Set.of("PC_HOME", "HOME_SERVICE_HOME");
    private static final Set<String> LINK_TYPES = Set.of("NONE", "INTERNAL", "EXTERNAL");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private final BannerRepository repository;
    private final FileObjectService files;
    private final AccessCatalogService accessCatalog;
    private final AuditService audit;

    public BannerService(
            BannerRepository repository,
            FileObjectService files,
            AccessCatalogService accessCatalog,
            AuditService audit) {
        this.repository = repository;
        this.files = files;
        this.accessCatalog = accessCatalog;
        this.audit = audit;
    }

    public List<Banner> findAll(String positionCode, String keyword, String status) {
        return repository.findAll(optionalPosition(positionCode), optional(keyword), optionalStatus(status));
    }

    public List<ActiveBanner> active(String positionCode) {
        return repository.findActive(position(positionCode), LocalDateTime.now()).stream()
                .map(banner -> new ActiveBanner(
                        banner.id(), banner.title(), banner.linkType(), banner.linkValue(),
                        banner.sortNo(), banner.version()))
                .toList();
    }

    public Banner detail(long id) {
        return repository.find(id).orElseThrow(() -> new ResourceNotFoundException("首页图片不存在"));
    }

    public Banner activeDetail(long id) {
        Banner banner = detail(id);
        LocalDateTime now = LocalDateTime.now();
        if (!"ACTIVE".equals(banner.status())
                || (banner.validFrom() != null && banner.validFrom().isAfter(now))
                || (banner.validTo() != null && banner.validTo().isBefore(now))) {
            throw new ResourceNotFoundException("首页图片不存在或不在展示期");
        }
        return banner;
    }

    public StoredFileDownload image(long id, boolean activeOnly) {
        Banner banner = activeOnly ? activeDetail(id) : detail(id);
        return files.downloadManagedImage(banner.imageFileId(), IMAGE_PURPOSE);
    }

    @Transactional
    public Banner create(CreateBannerRequest request, MultipartFile upload, String username) {
        long operatorId = accessCatalog.userIdentity(username).id();
        Normalized normalized = normalize(
                request.positionCode(), request.title(), request.linkType(), request.linkValue(),
                request.sortNo(), request.validFrom(), request.validTo(), "ACTIVE");
        FileObjectItem image = files.storeManagedImage(IMAGE_PURPOSE, upload, operatorId);
        try {
            Banner created = repository.create(new NewBanner(
                    normalized.positionCode(), normalized.title(), image.id(), image.originalName(),
                    image.contentType(), normalized.linkType(), normalized.linkValue(), normalized.sortNo(),
                    normalized.validFrom(), normalized.validTo(), operatorId));
            audit.record("SYSTEM", "CREATE", "BANNER", created.id(), null,
                    null, snapshot(created), operatorId);
            return created;
        } catch (RuntimeException exception) {
            discard(image.id(), exception);
            throw exception;
        }
    }

    @Transactional
    public Banner update(long id, UpdateBannerRequest request, String username) {
        Banner before = detail(id);
        long operatorId = accessCatalog.userIdentity(username).id();
        Normalized normalized = normalize(
                request.positionCode(), request.title(), request.linkType(), request.linkValue(),
                request.sortNo(), request.validFrom(), request.validTo(), request.status());
        Banner updated = repository.update(new BannerUpdate(
                id, normalized.positionCode(), normalized.title(), normalized.linkType(), normalized.linkValue(),
                normalized.sortNo(), normalized.validFrom(), normalized.validTo(), normalized.status(),
                request.version(), operatorId));
        audit.record("SYSTEM", "UPDATE", "BANNER", id, null,
                snapshot(before), snapshot(updated), operatorId);
        return updated;
    }

    @Transactional
    public Banner replaceImage(
            long id, String version, MultipartFile upload, String username) {
        Banner before = detail(id);
        long operatorId = accessCatalog.userIdentity(username).id();
        FileObjectItem image = files.storeManagedImage(IMAGE_PURPOSE, upload, operatorId);
        try {
            Banner updated = repository.replaceImage(new BannerImageUpdate(
                    id, image.id(), image.originalName(), image.contentType(), version, operatorId));
            files.retireManagedImage(before.imageFileId(), IMAGE_PURPOSE);
            audit.record("SYSTEM", "REPLACE_IMAGE", "BANNER", id, null,
                    snapshot(before), snapshot(updated), operatorId);
            return updated;
        } catch (RuntimeException exception) {
            discard(image.id(), exception);
            throw exception;
        }
    }

    private void discard(long fileId, RuntimeException original) {
        try {
            files.discardManagedImage(fileId, IMAGE_PURPOSE);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private Map<String, Object> snapshot(Banner banner) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("positionCode", banner.positionCode());
        value.put("title", banner.title());
        value.put("imageName", banner.imageName());
        value.put("linkType", banner.linkType());
        value.put("linkValue", banner.linkValue());
        value.put("sortNo", banner.sortNo());
        value.put("validFrom", banner.validFrom());
        value.put("validTo", banner.validTo());
        value.put("status", banner.status());
        return value;
    }

    private static Normalized normalize(
            String positionCode,
            String title,
            String linkType,
            String linkValue,
            int sortNo,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            String status) {
        if (validFrom != null && validTo != null && !validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException("展示结束时间必须晚于开始时间");
        }
        String normalizedLinkType = enumValue(linkType, LINK_TYPES, "跳转类型无效");
        String normalizedLink = normalizeLink(normalizedLinkType, linkValue);
        return new Normalized(
                position(positionCode), title.trim(), normalizedLinkType, normalizedLink, sortNo,
                validFrom, validTo, enumValue(status, STATUSES, "首页图片状态无效"));
    }

    private static String normalizeLink(String linkType, String value) {
        String normalized = value == null || value.isBlank() ? null : value.trim();
        if ("NONE".equals(linkType)) {
            if (normalized != null) throw new IllegalArgumentException("无跳转时不能填写跳转地址");
            return null;
        }
        if (normalized == null) throw new IllegalArgumentException("请选择跳转地址");
        if ("INTERNAL".equals(linkType)) {
            if (!normalized.startsWith("/") || normalized.startsWith("//")) {
                throw new IllegalArgumentException("站内跳转地址必须以单个/开头");
            }
            return normalized;
        }
        try {
            URI uri = URI.create(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("站外跳转只允许完整HTTPS地址");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("站外跳转只允许完整HTTPS地址");
        }
    }

    private static String position(String value) {
        return enumValue(value, POSITION_CODES, "展示位置无效");
    }

    private static String optionalPosition(String value) {
        return value == null || value.isBlank() ? null : position(value);
    }

    private static String optionalStatus(String value) {
        return value == null || value.isBlank() ? null : enumValue(value, STATUSES, "首页图片状态无效");
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 200) throw new IllegalArgumentException("首页图片查询不能超过200个字符");
        return normalized;
    }

    private static String enumValue(String value, Set<String> supported, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!supported.contains(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private record Normalized(
            String positionCode,
            String title,
            String linkType,
            String linkValue,
            int sortNo,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            String status) {
    }
}
