package com.yuezhijian.server.servicearea;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@Profile("sqlserver")
public class SqlServerServiceAreaRepository implements ServiceAreaRepository {
    private final ServiceAreaMapper mapper;

    public SqlServerServiceAreaRepository(ServiceAreaMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ServiceArea> findAll(Long storeId, String keyword, String status) {
        return mapper.findAll(storeId, keyword, status);
    }

    @Override
    public Optional<ServiceArea> find(long id) {
        return Optional.ofNullable(mapper.find(id));
    }

    @Override
    public ServiceArea create(NewServiceArea area) {
        try {
            return find(mapper.insert(area)).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("该门店已存在相同服务地址");
        }
    }

    @Override
    public ServiceArea update(ServiceAreaUpdate update) {
        try {
            if (mapper.update(update) == 0) {
                if (mapper.find(update.id()) == null) throw new ResourceNotFoundException("服务小区不存在");
                throw new DuplicateResourceException("服务小区已被他人修改，请刷新后重试");
            }
            return find(update.id()).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("该门店已存在相同服务地址");
        }
    }
}
