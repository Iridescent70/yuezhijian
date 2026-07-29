package com.yuezhijian.server.cancelreason;

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
public class MemoryCancelReasonRepository implements CancelReasonRepository {
    private final List<CancelReason> reasons = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(7);

    public MemoryCancelReasonRepository() {
        seed(1, "APPOINTMENT", "CUSTOMER_CHANGE", "客户行程有变", false, 10);
        seed(2, "APPOINTMENT", "STORE_CAPACITY", "门店接待能力不足", true, 20);
        seed(3, "APPOINTMENT", "CUSTOMER_NO_SHOW", "客户未按时到店", false, 30);
        seed(4, "APPOINTMENT", "OTHER", "其他", true, 99);
        seed(5, "BILL", "CUSTOMER_CHANGE", "客户取消消费", false, 10);
        seed(6, "BILL", "BILL_ERROR", "开单错误", true, 20);
        seed(7, "BILL", "OTHER", "其他", true, 99);
    }

    @Override
    public synchronized List<CancelReason> findAll(String businessType, String keyword, String status) {
        String normalizedKeyword = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return reasons.stream()
                .filter(reason -> businessType == null || businessType.equals(reason.businessType()))
                .filter(reason -> status == null || status.equals(reason.status()))
                .filter(reason -> normalizedKeyword == null
                        || reason.code().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || reason.name().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .sorted(Comparator.comparing(CancelReason::businessType)
                        .thenComparingInt(CancelReason::sortNo)
                        .thenComparingLong(CancelReason::id))
                .toList();
    }

    @Override
    public synchronized Optional<CancelReason> find(long id) {
        return reasons.stream().filter(reason -> reason.id() == id).findFirst();
    }

    @Override
    public synchronized Optional<CancelReason> findActive(String businessType, String code) {
        return reasons.stream().filter(reason -> reason.businessType().equals(businessType)
                        && reason.code().equals(code) && "ACTIVE".equals(reason.status()))
                .findFirst();
    }

    @Override
    public synchronized CancelReason create(NewCancelReason draft) {
        requireUnique(draft.businessType(), draft.code(), null);
        long id = ids.incrementAndGet();
        CancelReason created = new CancelReason(
                id, draft.businessType(), draft.code(), draft.name(), draft.requiresNote(), draft.sortNo(),
                "ACTIVE", LocalDateTime.now(), draft.operatorId(), operatorName(draft.operatorId()), "1");
        reasons.add(created);
        return created;
    }

    @Override
    public synchronized CancelReason update(CancelReasonUpdate update) {
        CancelReason current = find(update.id())
                .orElseThrow(() -> new ResourceNotFoundException("取消原因不存在"));
        if (!current.version().equals(update.version())) {
            throw new DuplicateResourceException("取消原因已被他人修改，请刷新后重试");
        }
        CancelReason saved = new CancelReason(
                current.id(), current.businessType(), current.code(), update.name(), update.requiresNote(),
                update.sortNo(), update.status(), LocalDateTime.now(), update.operatorId(),
                operatorName(update.operatorId()), nextVersion(current.version()));
        reasons.set(reasons.indexOf(current), saved);
        return saved;
    }

    private void seed(long id, String businessType, String code, String name, boolean requiresNote, int sortNo) {
        reasons.add(new CancelReason(
                id, businessType, code, name, requiresNote, sortNo, "ACTIVE",
                LocalDateTime.now(), null, "系统初始化", "1"));
    }

    private void requireUnique(String businessType, String code, Long excludedId) {
        boolean exists = reasons.stream().anyMatch(reason -> reason.businessType().equals(businessType)
                && reason.code().equalsIgnoreCase(code)
                && (excludedId == null || reason.id() != excludedId));
        if (exists) throw new DuplicateResourceException("该业务类型下的原因编号已存在");
    }

    private static String operatorName(long operatorId) {
        return operatorId == 1L ? "本地管理员" : "用户" + operatorId;
    }

    private static String nextVersion(String version) {
        return String.valueOf(Long.parseLong(version) + 1);
    }
}
