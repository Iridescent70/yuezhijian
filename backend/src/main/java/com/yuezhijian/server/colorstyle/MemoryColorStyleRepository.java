package com.yuezhijian.server.colorstyle;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryColorStyleRepository implements ColorStyleRepository {
    private final List<ColorStyleCategory> categories = new ArrayList<>();
    private final List<ColorStyle> styles = new ArrayList<>();
    private final AtomicLong categoryIds = new AtomicLong();
    private final AtomicLong styleIds = new AtomicLong();
    private final AtomicLong assetIds = new AtomicLong();

    @Override
    public synchronized List<ColorStyleCategory> findCategories(String keyword, String status) {
        String search = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return categories.stream()
                .filter(category -> status == null || status.equals(category.status()))
                .filter(category -> search == null
                        || category.code().toLowerCase(Locale.ROOT).contains(search)
                        || category.name().toLowerCase(Locale.ROOT).contains(search))
                .sorted(Comparator.comparingInt(ColorStyleCategory::sortNo)
                        .thenComparingLong(ColorStyleCategory::id))
                .toList();
    }

    @Override
    public synchronized Optional<ColorStyleCategory> findCategory(long id) {
        return categories.stream().filter(category -> category.id() == id).findFirst();
    }

    @Override
    public synchronized ColorStyleCategory createCategory(NewColorStyleCategory draft) {
        requireUniqueCategoryCode(draft.code());
        long id = categoryIds.incrementAndGet();
        ColorStyleCategory created = new ColorStyleCategory(
                id, draft.parentId(), draft.code(), draft.name(), null, null, null, draft.sortNo(),
                "ACTIVE", LocalDateTime.now(), draft.operatorId(), operatorName(draft.operatorId()), "1");
        categories.add(created);
        return created;
    }

    @Override
    public synchronized ColorStyleCategory updateCategory(ColorStyleCategoryUpdate update) {
        ColorStyleCategory current = requireCategoryVersion(update.id(), update.version());
        ColorStyleCategory saved = new ColorStyleCategory(
                current.id(), update.parentId(), current.code(), update.name(), current.imageFileId(),
                current.imageName(), current.imageContentType(), update.sortNo(), update.status(),
                LocalDateTime.now(), update.operatorId(), operatorName(update.operatorId()),
                nextVersion(current.version()));
        categories.set(categories.indexOf(current), saved);
        return saved;
    }

    @Override
    public synchronized ColorStyleCategory replaceCategoryImage(ColorStyleCategoryImageUpdate update) {
        ColorStyleCategory current = requireCategoryVersion(update.id(), update.version());
        ColorStyleCategory saved = new ColorStyleCategory(
                current.id(), current.parentId(), current.code(), current.name(), update.imageFileId(),
                update.imageName(), update.imageContentType(), current.sortNo(), current.status(),
                LocalDateTime.now(), update.operatorId(), operatorName(update.operatorId()),
                nextVersion(current.version()));
        categories.set(categories.indexOf(current), saved);
        return saved;
    }

    @Override
    public synchronized boolean hasActiveStyleInCategory(long categoryId) {
        return styles.stream().anyMatch(style -> "ACTIVE".equals(style.status())
                && style.categoryIds().contains(categoryId));
    }

    @Override
    public synchronized PageResult<ColorStyle> findStyles(
            Long categoryId, String keyword, String status, int page, int size) {
        String search = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        List<ColorStyle> matching = styles.stream()
                .filter(style -> categoryId == null || style.categoryIds().contains(categoryId))
                .filter(style -> status == null || status.equals(style.status()))
                .filter(style -> search == null
                        || style.code().toLowerCase(Locale.ROOT).contains(search)
                        || style.name().toLowerCase(Locale.ROOT).contains(search))
                .sorted(Comparator.comparingInt(ColorStyle::sortNo).thenComparingLong(ColorStyle::id))
                .toList();
        int from = Math.min((page - 1) * size, matching.size());
        int to = Math.min(from + size, matching.size());
        return new PageResult<>(matching.subList(from, to), page, size, matching.size());
    }

    @Override
    public synchronized Optional<ColorStyle> findStyle(long id) {
        return styles.stream().filter(style -> style.id() == id).findFirst();
    }

    @Override
    public synchronized ColorStyle createStyle(NewColorStyle draft) {
        requireUniqueStyleCode(draft.code());
        long id = styleIds.incrementAndGet();
        ColorStyle created = new ColorStyle(
                id, draft.code(), draft.name(), draft.description(), draft.sortNo(), "ACTIVE",
                LocalDateTime.now(), draft.operatorId(), operatorName(draft.operatorId()), "1",
                immutableIds(draft.categoryIds()), List.of());
        styles.add(created);
        return created;
    }

    @Override
    public synchronized ColorStyle updateStyle(ColorStyleUpdate update) {
        ColorStyle current = requireStyleVersion(update.id(), update.version());
        ColorStyle saved = new ColorStyle(
                current.id(), current.code(), update.name(), update.description(), update.sortNo(), update.status(),
                LocalDateTime.now(), update.operatorId(), operatorName(update.operatorId()),
                nextVersion(current.version()), immutableIds(update.categoryIds()), current.assets());
        styles.set(styles.indexOf(current), saved);
        return saved;
    }

    @Override
    public synchronized Optional<ColorStyleAsset> findAsset(long styleId, long assetId) {
        return findStyle(styleId).stream().flatMap(style -> style.assets().stream())
                .filter(asset -> asset.id() == assetId).findFirst();
    }

    @Override
    public synchronized int activeAssetCountForUpdate(long styleId) {
        return Math.toIntExact(findStyle(styleId).stream().flatMap(style -> style.assets().stream())
                .filter(asset -> "ACTIVE".equals(asset.status())).count());
    }

    @Override
    public synchronized ColorStyleAsset createAsset(NewColorStyleAsset draft) {
        ColorStyle current = findStyle(draft.colorStyleId())
                .orElseThrow(() -> new ResourceNotFoundException("线上试色色号不存在"));
        if (current.assets().stream().filter(asset -> "ACTIVE".equals(asset.status())).count() >= 20) {
            throw new DuplicateResourceException("每个色号最多保留20张启用素材");
        }
        ColorStyleAsset created = new ColorStyleAsset(
                assetIds.incrementAndGet(), current.id(), draft.fileId(), draft.fileName(), draft.contentType(),
                draft.sortNo(), "ACTIVE", LocalDateTime.now(), "1");
        List<ColorStyleAsset> assets = new ArrayList<>(current.assets());
        assets.add(created);
        replaceStyleAssets(current, assets);
        return created;
    }

    @Override
    public synchronized ColorStyleAsset updateAsset(ColorStyleAssetUpdate update) {
        ColorStyle current = findStyle(update.colorStyleId())
                .orElseThrow(() -> new ResourceNotFoundException("线上试色色号不存在"));
        ColorStyleAsset asset = findAsset(update.colorStyleId(), update.id())
                .orElseThrow(() -> new ResourceNotFoundException("线上试色素材不存在"));
        if (!asset.version().equals(update.version())) {
            throw new DuplicateResourceException("线上试色素材已被他人修改，请刷新后重试");
        }
        if ("ACTIVE".equals(update.status()) && "DISABLED".equals(asset.status())
                && current.assets().stream().filter(item -> "ACTIVE".equals(item.status())).count() >= 20) {
            throw new DuplicateResourceException("每个色号最多保留20张启用素材");
        }
        ColorStyleAsset saved = new ColorStyleAsset(
                asset.id(), asset.colorStyleId(), asset.fileId(), asset.fileName(), asset.contentType(),
                update.sortNo(), update.status(), LocalDateTime.now(), nextVersion(asset.version()));
        List<ColorStyleAsset> assets = new ArrayList<>(current.assets());
        assets.set(assets.indexOf(asset), saved);
        replaceStyleAssets(current, assets);
        return saved;
    }

    private void replaceStyleAssets(ColorStyle current, List<ColorStyleAsset> assets) {
        List<ColorStyleAsset> ordered = assets.stream()
                .sorted(Comparator.comparingInt(ColorStyleAsset::sortNo).thenComparingLong(ColorStyleAsset::id))
                .toList();
        ColorStyle saved = new ColorStyle(
                current.id(), current.code(), current.name(), current.description(), current.sortNo(), current.status(),
                current.updatedAt(), current.updatedBy(), current.updatedByName(), current.version(),
                current.categoryIds(), ordered);
        styles.set(styles.indexOf(current), saved);
    }

    private ColorStyleCategory requireCategoryVersion(long id, String version) {
        ColorStyleCategory current = findCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("线上试色分类不存在"));
        if (!current.version().equals(version)) {
            throw new DuplicateResourceException("线上试色分类已被他人修改，请刷新后重试");
        }
        return current;
    }

    private ColorStyle requireStyleVersion(long id, String version) {
        ColorStyle current = findStyle(id)
                .orElseThrow(() -> new ResourceNotFoundException("线上试色色号不存在"));
        if (!current.version().equals(version)) {
            throw new DuplicateResourceException("线上试色色号已被他人修改，请刷新后重试");
        }
        return current;
    }

    private void requireUniqueCategoryCode(String code) {
        if (categories.stream().anyMatch(item -> item.code().equalsIgnoreCase(code))) {
            throw new DuplicateResourceException("线上试色分类编码已存在");
        }
    }

    private void requireUniqueStyleCode(String code) {
        if (styles.stream().anyMatch(item -> item.code().equalsIgnoreCase(code))) {
            throw new DuplicateResourceException("线上试色色号已存在");
        }
    }

    private static List<Long> immutableIds(List<Long> ids) {
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    private static String operatorName(long operatorId) {
        return operatorId == 1L ? "本地管理员" : "用户" + operatorId;
    }

    private static String nextVersion(String version) {
        return String.valueOf(Long.parseLong(version) + 1);
    }
}
