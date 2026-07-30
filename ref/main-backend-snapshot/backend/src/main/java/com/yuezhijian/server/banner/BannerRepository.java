package com.yuezhijian.server.banner;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BannerRepository {
    List<Banner> findAll(String positionCode, String keyword, String status);

    List<Banner> findActive(String positionCode, LocalDateTime now);

    Optional<Banner> find(long id);

    Banner create(NewBanner banner);

    Banner update(BannerUpdate update);

    Banner replaceImage(BannerImageUpdate update);
}
