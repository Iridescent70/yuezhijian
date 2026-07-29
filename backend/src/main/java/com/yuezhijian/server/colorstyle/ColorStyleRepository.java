package com.yuezhijian.server.colorstyle;

import com.yuezhijian.server.common.PageResult;
import java.util.List;
import java.util.Optional;

public interface ColorStyleRepository {
    List<ColorStyleCategory> findCategories(String keyword, String status);

    Optional<ColorStyleCategory> findCategory(long id);

    ColorStyleCategory createCategory(NewColorStyleCategory category);

    ColorStyleCategory updateCategory(ColorStyleCategoryUpdate update);

    ColorStyleCategory replaceCategoryImage(ColorStyleCategoryImageUpdate update);

    boolean hasActiveStyleInCategory(long categoryId);

    PageResult<ColorStyle> findStyles(
            Long categoryId, String keyword, String status, int page, int size);

    Optional<ColorStyle> findStyle(long id);

    ColorStyle createStyle(NewColorStyle style);

    ColorStyle updateStyle(ColorStyleUpdate update);

    Optional<ColorStyleAsset> findAsset(long styleId, long assetId);

    int activeAssetCountForUpdate(long styleId);

    ColorStyleAsset createAsset(NewColorStyleAsset asset);

    ColorStyleAsset updateAsset(ColorStyleAssetUpdate update);
}
