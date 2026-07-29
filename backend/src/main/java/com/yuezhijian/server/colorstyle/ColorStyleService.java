package com.yuezhijian.server.colorstyle;

import com.yuezhijian.server.audit.AuditService;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.file.FileObjectItem;
import com.yuezhijian.server.file.FileObjectService;
import com.yuezhijian.server.file.StoredFileDownload;
import com.yuezhijian.server.iam.AccessCatalogService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ColorStyleService {
    private static final String CATEGORY_IMAGE_PURPOSE = "COLOR_STYLE_CATEGORY_IMAGE";
    private static final String ASSET_PURPOSE = "COLOR_STYLE_ASSET";
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final int MAX_ASSETS_PER_STYLE = 20;

    private final ColorStyleRepository repository;
    private final FileObjectService files;
    private final AccessCatalogService accessCatalog;
    private final AuditService audit;

    public ColorStyleService(
            ColorStyleRepository repository,
            FileObjectService files,
            AccessCatalogService accessCatalog,
            AuditService audit) {
        this.repository = repository;
        this.files = files;
        this.accessCatalog = accessCatalog;
        this.audit = audit;
    }

    public List<ColorStyleCategory> categories(String keyword, String status) {
        return repository.findCategories(optional(keyword, 100, "分类查询"), optionalStatus(status));
    }

    public ColorStyleCategory category(long id) {
        return repository.findCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("线上试色分类不存在"));
    }

    @Transactional
    public ColorStyleCategory createCategory(CreateColorStyleCategoryRequest request, String username) {
        long operatorId = operatorId(username);
        Long parentId = validParent(null, request.parentId(), true);
        ColorStyleCategory created = repository.createCategory(new NewColorStyleCategory(
                parentId, code(request.code()), text(request.name()), request.sortNo(), operatorId));
        audit.record("SYSTEM", "CREATE", "COLOR_STYLE_CATEGORY", created.id(), null,
                null, categorySnapshot(created), operatorId);
        return created;
    }

    @Transactional
    public ColorStyleCategory updateCategory(
            long id, UpdateColorStyleCategoryRequest request, String username) {
        ColorStyleCategory before = category(id);
        String status = status(request.status());
        Long parentId = validParent(id, request.parentId(), "ACTIVE".equals(status));
        if ("DISABLED".equals(status)) {
            requireNoActiveChildren(id);
            if (repository.hasActiveStyleInCategory(id)) {
                throw new IllegalArgumentException("请先停用或调整该分类下的启用色号");
            }
        }
        long operatorId = operatorId(username);
        ColorStyleCategory updated = repository.updateCategory(new ColorStyleCategoryUpdate(
                id, parentId, text(request.name()), request.sortNo(), status, request.version(), operatorId));
        audit.record("SYSTEM", "UPDATE", "COLOR_STYLE_CATEGORY", id, null,
                categorySnapshot(before), categorySnapshot(updated), operatorId);
        return updated;
    }

    @Transactional
    public ColorStyleCategory replaceCategoryImage(
            long id, String version, MultipartFile upload, String username) {
        ColorStyleCategory before = category(id);
        long operatorId = operatorId(username);
        FileObjectItem image = files.storeManagedImage(CATEGORY_IMAGE_PURPOSE, upload, operatorId);
        try {
            ColorStyleCategory updated = repository.replaceCategoryImage(new ColorStyleCategoryImageUpdate(
                    id, image.id(), image.originalName(), image.contentType(), version, operatorId));
            if (before.imageFileId() != null) {
                files.retireManagedImage(before.imageFileId(), CATEGORY_IMAGE_PURPOSE);
            }
            audit.record("SYSTEM", "REPLACE_IMAGE", "COLOR_STYLE_CATEGORY", id, null,
                    categorySnapshot(before), categorySnapshot(updated), operatorId);
            return updated;
        } catch (RuntimeException exception) {
            discard(image.id(), CATEGORY_IMAGE_PURPOSE, exception);
            throw exception;
        }
    }

    public StoredFileDownload categoryImage(long id) {
        ColorStyleCategory category = category(id);
        if (category.imageFileId() == null) throw new ResourceNotFoundException("线上试色分类图片不存在");
        return files.downloadManagedImage(category.imageFileId(), CATEGORY_IMAGE_PURPOSE);
    }

    public PageResult<ColorStyle> styles(
            Long categoryId, String keyword, String status, int page, int size) {
        if (categoryId != null) category(categoryId);
        return repository.findStyles(
                categoryId, optional(keyword, 100, "色号查询"), optionalStatus(status),
                Math.min(Math.max(page, 1), 1_000_000), Math.min(Math.max(size, 1), 100));
    }

    public ColorStyle style(long id) {
        return repository.findStyle(id)
                .orElseThrow(() -> new ResourceNotFoundException("线上试色色号不存在"));
    }

    @Transactional
    public ColorStyle createStyle(CreateColorStyleRequest request, String username) {
        List<Long> categoryIds = categoryIds(request.categoryIds(), true);
        long operatorId = operatorId(username);
        ColorStyle created = repository.createStyle(new NewColorStyle(
                code(request.code()), text(request.name()), optionalText(request.description()),
                request.sortNo(), categoryIds, operatorId));
        audit.record("SYSTEM", "CREATE", "COLOR_STYLE", created.id(), null,
                null, styleSnapshot(created), operatorId);
        return created;
    }

    @Transactional
    public ColorStyle updateStyle(long id, UpdateColorStyleRequest request, String username) {
        ColorStyle before = style(id);
        String status = status(request.status());
        List<Long> categoryIds = categoryIds(request.categoryIds(), "ACTIVE".equals(status));
        long operatorId = operatorId(username);
        ColorStyle updated = repository.updateStyle(new ColorStyleUpdate(
                id, text(request.name()), optionalText(request.description()), request.sortNo(), status,
                categoryIds, request.version(), operatorId));
        audit.record("SYSTEM", "UPDATE", "COLOR_STYLE", id, null,
                styleSnapshot(before), styleSnapshot(updated), operatorId);
        return updated;
    }

    @Transactional
    public ColorStyleAsset addAsset(
        long styleId, int sortNo, MultipartFile upload, String username) {
        style(styleId);
        if (sortNo < 0 || sortNo > 9999) throw new IllegalArgumentException("素材排序必须在0到9999之间");
        long operatorId = operatorId(username);
        FileObjectItem image = files.storeManagedImage(ASSET_PURPOSE, upload, operatorId);
        try {
            int activeCount = repository.activeAssetCountForUpdate(styleId);
            if (activeCount >= MAX_ASSETS_PER_STYLE) {
                throw new IllegalArgumentException("每个色号最多保留20张启用素材");
            }
            ColorStyleAsset created = repository.createAsset(new NewColorStyleAsset(
                    styleId, image.id(), image.originalName(), image.contentType(), sortNo, operatorId));
            audit.record("SYSTEM", "CREATE", "COLOR_STYLE_ASSET", created.id(), null,
                    null, assetSnapshot(created), operatorId);
            return created;
        } catch (RuntimeException exception) {
            discard(image.id(), ASSET_PURPOSE, exception);
            throw exception;
        }
    }

    @Transactional
    public ColorStyleAsset updateAsset(
            long styleId, long assetId, UpdateColorStyleAssetRequest request, String username) {
        style(styleId);
        ColorStyleAsset before = asset(styleId, assetId);
        String status = status(request.status());
        if ("ACTIVE".equals(status) && "DISABLED".equals(before.status())) {
            int activeCount = repository.activeAssetCountForUpdate(styleId);
            if (activeCount >= MAX_ASSETS_PER_STYLE) {
                throw new IllegalArgumentException("每个色号最多保留20张启用素材");
            }
        }
        long operatorId = operatorId(username);
        ColorStyleAsset updated = repository.updateAsset(new ColorStyleAssetUpdate(
                assetId, styleId, request.sortNo(), status, request.version(), operatorId));
        audit.record("SYSTEM", "UPDATE", "COLOR_STYLE_ASSET", assetId, null,
                assetSnapshot(before), assetSnapshot(updated), operatorId);
        return updated;
    }

    public StoredFileDownload assetContent(long styleId, long assetId) {
        ColorStyleAsset asset = asset(styleId, assetId);
        return files.downloadManagedImage(asset.fileId(), ASSET_PURPOSE);
    }

    private ColorStyleAsset asset(long styleId, long assetId) {
        return repository.findAsset(styleId, assetId)
                .orElseThrow(() -> new ResourceNotFoundException("线上试色素材不存在"));
    }

    private Long validParent(Long categoryId, Long parentId, boolean mustBeActive) {
        if (parentId == null) return null;
        if (categoryId != null && categoryId.equals(parentId)) {
            throw new IllegalArgumentException("分类不能选择自身作为父分类");
        }
        ColorStyleCategory parent = category(parentId);
        if (mustBeActive && !"ACTIVE".equals(parent.status())) {
            throw new IllegalArgumentException("启用分类的父分类必须为启用状态");
        }
        Set<Long> visited = new LinkedHashSet<>();
        ColorStyleCategory cursor = parent;
        while (cursor != null) {
            if (!visited.add(cursor.id())) throw new IllegalArgumentException("分类层级存在循环");
            if (categoryId != null && cursor.id() == categoryId) {
                throw new IllegalArgumentException("分类不能移动到自己的子分类下");
            }
            cursor = cursor.parentId() == null ? null : category(cursor.parentId());
        }
        return parentId;
    }

    private void requireNoActiveChildren(long id) {
        boolean hasActiveChild = repository.findCategories(null, "ACTIVE").stream()
                .anyMatch(category -> Long.valueOf(id).equals(category.parentId()));
        if (hasActiveChild) throw new IllegalArgumentException("请先停用该分类下的启用子分类");
    }

    private List<Long> categoryIds(List<Long> values, boolean mustBeActive) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("至少选择一个线上试色分类");
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : values) {
            if (id == null || id <= 0) throw new IllegalArgumentException("线上试色分类无效");
            ColorStyleCategory category = category(id);
            if (mustBeActive && !"ACTIVE".equals(category.status())) {
                throw new IllegalArgumentException("启用色号只能选择启用分类");
            }
            unique.add(id);
        }
        if (unique.size() > 20) throw new IllegalArgumentException("一个色号最多选择20个分类");
        return List.copyOf(unique);
    }

    private long operatorId(String username) {
        return accessCatalog.userIdentity(username).id();
    }

    private static String status(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("状态不能为空");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new IllegalArgumentException("状态无效");
        return normalized;
    }

    private static String optionalStatus(String value) {
        return value == null || value.isBlank() ? null : status(value);
    }

    private static String code(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String text(String value) {
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String optional(String value, int max, String field) {
        String normalized = optionalText(value);
        if (normalized != null && normalized.length() > max) {
            throw new IllegalArgumentException(field + "不能超过" + max + "个字符");
        }
        return normalized;
    }

    private void discard(long fileId, String purpose, RuntimeException original) {
        try {
            files.discardManagedImage(fileId, purpose);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private static Map<String, Object> categorySnapshot(ColorStyleCategory category) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("parentId", category.parentId());
        values.put("code", category.code());
        values.put("name", category.name());
        values.put("imageName", category.imageName());
        values.put("sortNo", category.sortNo());
        values.put("status", category.status());
        return values;
    }

    private static Map<String, Object> styleSnapshot(ColorStyle style) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", style.code());
        values.put("name", style.name());
        values.put("description", style.description());
        values.put("sortNo", style.sortNo());
        values.put("status", style.status());
        values.put("categoryIds", new ArrayList<>(style.categoryIds()));
        return values;
    }

    private static Map<String, Object> assetSnapshot(ColorStyleAsset asset) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("colorStyleId", asset.colorStyleId());
        values.put("fileName", asset.fileName());
        values.put("sortNo", asset.sortNo());
        values.put("status", asset.status());
        return values;
    }
}
