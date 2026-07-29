package com.yuezhijian.server.banner;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryBannerRepository implements BannerRepository {
    private final List<Banner> banners = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public synchronized List<Banner> findAll(String positionCode, String keyword, String status) {
        String normalizedKeyword = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return banners.stream()
                .filter(banner -> positionCode == null || positionCode.equals(banner.positionCode()))
                .filter(banner -> status == null || status.equals(banner.status()))
                .filter(banner -> normalizedKeyword == null
                        || banner.title().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .sorted(order())
                .toList();
    }

    @Override
    public synchronized List<Banner> findActive(String positionCode, LocalDateTime now) {
        return banners.stream()
                .filter(banner -> positionCode.equals(banner.positionCode()))
                .filter(banner -> "ACTIVE".equals(banner.status()))
                .filter(banner -> banner.validFrom() == null || !banner.validFrom().isAfter(now))
                .filter(banner -> banner.validTo() == null || !banner.validTo().isBefore(now))
                .sorted(order())
                .toList();
    }

    @Override
    public synchronized Optional<Banner> find(long id) {
        return banners.stream().filter(banner -> banner.id() == id).findFirst();
    }

    @Override
    public synchronized Banner create(NewBanner draft) {
        long id = ids.incrementAndGet();
        Banner created = new Banner(
                id, draft.positionCode(), draft.title(), draft.imageFileId(), draft.imageName(),
                draft.imageContentType(), draft.linkType(), draft.linkValue(), draft.sortNo(),
                draft.validFrom(), draft.validTo(), "ACTIVE", LocalDateTime.now(), draft.operatorId(),
                operatorName(draft.operatorId()), "1");
        banners.add(created);
        return created;
    }

    @Override
    public synchronized Banner update(BannerUpdate update) {
        Banner current = requireVersion(update.id(), update.version());
        Banner saved = new Banner(
                current.id(), update.positionCode(), update.title(), current.imageFileId(), current.imageName(),
                current.imageContentType(), update.linkType(), update.linkValue(), update.sortNo(),
                update.validFrom(), update.validTo(), update.status(), LocalDateTime.now(), update.operatorId(),
                operatorName(update.operatorId()), nextVersion(current.version()));
        banners.set(banners.indexOf(current), saved);
        return saved;
    }

    @Override
    public synchronized Banner replaceImage(BannerImageUpdate update) {
        Banner current = requireVersion(update.id(), update.version());
        Banner saved = new Banner(
                current.id(), current.positionCode(), current.title(), update.imageFileId(), update.imageName(),
                update.imageContentType(), current.linkType(), current.linkValue(), current.sortNo(),
                current.validFrom(), current.validTo(), current.status(), LocalDateTime.now(), update.operatorId(),
                operatorName(update.operatorId()), nextVersion(current.version()));
        banners.set(banners.indexOf(current), saved);
        return saved;
    }

    private Banner requireVersion(long id, String version) {
        Banner current = find(id).orElseThrow(() -> new ResourceNotFoundException("首页图片不存在"));
        if (!current.version().equals(version)) {
            throw new DuplicateResourceException("首页图片已被他人修改，请刷新后重试");
        }
        return current;
    }

    private static Comparator<Banner> order() {
        return Comparator.comparing(Banner::positionCode)
                .thenComparingInt(Banner::sortNo)
                .thenComparingLong(Banner::id);
    }

    private static String operatorName(long operatorId) {
        return operatorId == 1L ? "本地管理员" : "用户" + operatorId;
    }

    private static String nextVersion(String version) {
        return String.valueOf(Long.parseLong(version) + 1);
    }
}
