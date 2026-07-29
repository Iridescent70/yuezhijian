package com.yuezhijian.server.cancelreason;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@Profile("sqlserver")
public class SqlServerCancelReasonRepository implements CancelReasonRepository {
    private final CancelReasonMapper mapper;

    public SqlServerCancelReasonRepository(CancelReasonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<CancelReason> findAll(String businessType, String keyword, String status) {
        return mapper.findAll(businessType, keyword, status);
    }

    @Override
    public Optional<CancelReason> find(long id) {
        return Optional.ofNullable(mapper.find(id));
    }

    @Override
    public Optional<CancelReason> findActive(String businessType, String code) {
        return Optional.ofNullable(mapper.findActive(businessType, code));
    }

    @Override
    public CancelReason create(NewCancelReason reason) {
        try {
            return find(mapper.insert(reason)).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("该业务类型下的原因编号已存在");
        }
    }

    @Override
    public CancelReason update(CancelReasonUpdate update) {
        try {
            if (mapper.update(update) == 0) {
                if (mapper.find(update.id()) == null) throw new ResourceNotFoundException("取消原因不存在");
                throw new DuplicateResourceException("取消原因已被他人修改，请刷新后重试");
            }
            return find(update.id()).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("取消原因配置与现有数据冲突");
        }
    }
}
