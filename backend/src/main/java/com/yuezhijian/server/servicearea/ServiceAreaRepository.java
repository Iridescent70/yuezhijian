package com.yuezhijian.server.servicearea;

import java.util.List;
import java.util.Optional;

public interface ServiceAreaRepository {
    List<ServiceArea> findAll(Long storeId, String keyword, String status);

    Optional<ServiceArea> find(long id);

    ServiceArea create(NewServiceArea area);

    ServiceArea update(ServiceAreaUpdate update);
}
