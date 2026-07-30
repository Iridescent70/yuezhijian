package com.yuezhijian.server.colorstyle;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@Profile("sqlserver")
public class SqlServerColorStyleRepository implements ColorStyleRepository {
    private final ColorStyleMapper mapper;

    public SqlServerColorStyleRepository(ColorStyleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ColorStyleCategory> findCategories(String keyword, String status) {
        return mapper.findCategories(keyword, status);
    }

    @Override
    public Optional<ColorStyleCategory> findCategory(long id) {
        return Optional.ofNullable(mapper.findCategory(id));
    }

    @Override
    public ColorStyleCategory createCategory(NewColorStyleCategory category) {
        try {
            return findCategory(mapper.insertCategory(category)).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("线上试色分类编码已存在或父分类无效");
        }
    }

    @Override
    public ColorStyleCategory updateCategory(ColorStyleCategoryUpdate update) {
        try {
            return afterCategoryUpdate(update.id(), mapper.updateCategory(update));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("线上试色分类与现有数据冲突");
        }
    }

    @Override
    public ColorStyleCategory replaceCategoryImage(ColorStyleCategoryImageUpdate update) {
        return afterCategoryUpdate(update.id(), mapper.replaceCategoryImage(update));
    }

    @Override
    public boolean hasActiveStyleInCategory(long categoryId) {
        return mapper.hasActiveStyleInCategory(categoryId);
    }

    @Override
    public PageResult<ColorStyle> findStyles(
            Long categoryId, String keyword, String status, int page, int size) {
        List<ColorStyle> items = mapper.findStyles(
                categoryId, keyword, status, (page - 1) * size, size).stream().map(this::hydrate).toList();
        return new PageResult<>(items, page, size, mapper.countStyles(categoryId, keyword, status));
    }

    @Override
    public Optional<ColorStyle> findStyle(long id) {
        ColorStyleRow row = mapper.findStyle(id);
        return row == null ? Optional.empty() : Optional.of(hydrate(row));
    }

    @Override
    public ColorStyle createStyle(NewColorStyle style) {
        try {
            long id = mapper.insertStyle(style);
            replaceAssignments(id, style.categoryIds(), style.operatorId());
            return findStyle(id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("线上试色色号已存在或分类无效");
        }
    }

    @Override
    public ColorStyle updateStyle(ColorStyleUpdate update) {
        try {
            if (mapper.updateStyle(update) == 0) {
                if (mapper.findStyle(update.id()) == null) {
                    throw new ResourceNotFoundException("线上试色色号不存在");
                }
                throw new DuplicateResourceException("线上试色色号已被他人修改，请刷新后重试");
            }
            replaceAssignments(update.id(), update.categoryIds(), update.operatorId());
            return findStyle(update.id()).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("线上试色色号与现有数据冲突");
        }
    }

    @Override
    public Optional<ColorStyleAsset> findAsset(long styleId, long assetId) {
        return Optional.ofNullable(mapper.findAsset(styleId, assetId));
    }

    @Override
    public int activeAssetCountForUpdate(long styleId) {
        return mapper.activeAssetCountForUpdate(styleId);
    }

    @Override
    public ColorStyleAsset createAsset(NewColorStyleAsset asset) {
        try {
            long id = mapper.insertAsset(asset);
            return findAsset(asset.colorStyleId(), id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("线上试色素材与现有数据冲突");
        }
    }

    @Override
    public ColorStyleAsset updateAsset(ColorStyleAssetUpdate update) {
        if (mapper.updateAsset(update) == 0) {
            if (mapper.findAsset(update.colorStyleId(), update.id()) == null) {
                throw new ResourceNotFoundException("线上试色素材不存在");
            }
            throw new DuplicateResourceException("线上试色素材已被他人修改，请刷新后重试");
        }
        return findAsset(update.colorStyleId(), update.id()).orElseThrow();
    }

    private ColorStyleCategory afterCategoryUpdate(long id, int affectedRows) {
        if (affectedRows == 0) {
            if (mapper.findCategory(id) == null) throw new ResourceNotFoundException("线上试色分类不存在");
            throw new DuplicateResourceException("线上试色分类已被他人修改，请刷新后重试");
        }
        return findCategory(id).orElseThrow();
    }

    private ColorStyle hydrate(ColorStyleRow row) {
        return new ColorStyle(
                row.id(), row.code(), row.name(), row.description(), row.sortNo(), row.status(),
                row.updatedAt(), row.updatedBy(), row.updatedByName(), row.version(),
                mapper.findCategoryIds(row.id()), mapper.findAssets(row.id()));
    }

    private void replaceAssignments(long styleId, List<Long> categoryIds, long operatorId) {
        mapper.deleteAssignments(styleId);
        categoryIds.forEach(categoryId -> mapper.insertAssignment(styleId, categoryId, operatorId));
    }
}
