package com.yuezhijian.server.banner;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@Profile("sqlserver")
public class SqlServerBannerRepository implements BannerRepository {
    private final BannerMapper mapper;

    public SqlServerBannerRepository(BannerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Banner> findAll(String positionCode, String keyword, String status) {
        return mapper.findAll(positionCode, keyword, status);
    }

    @Override
    public List<Banner> findActive(String positionCode, LocalDateTime now) {
        return mapper.findActive(positionCode, now);
    }

    @Override
    public Optional<Banner> find(long id) {
        return Optional.ofNullable(mapper.find(id));
    }

    @Override
    public Banner create(NewBanner banner) {
        try {
            return find(mapper.insert(banner)).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("首页图片配置与现有数据冲突");
        }
    }

    @Override
    public Banner update(BannerUpdate update) {
        return afterUpdate(update.id(), update.version(), mapper.update(update));
    }

    @Override
    public Banner replaceImage(BannerImageUpdate update) {
        return afterUpdate(update.id(), update.version(), mapper.replaceImage(update));
    }

    private Banner afterUpdate(long id, String version, int affectedRows) {
        if (affectedRows == 0) {
            if (mapper.find(id) == null) throw new ResourceNotFoundException("首页图片不存在");
            throw new DuplicateResourceException("首页图片已被他人修改，请刷新后重试");
        }
        return find(id).orElseThrow();
    }
}
