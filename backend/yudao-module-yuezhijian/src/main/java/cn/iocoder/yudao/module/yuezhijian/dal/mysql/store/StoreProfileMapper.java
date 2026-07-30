package cn.iocoder.yudao.module.yuezhijian.dal.mysql.store;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.store.StoreProfileDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StoreProfileMapper extends BaseMapperX<StoreProfileDO> {

    default StoreProfileDO selectByDeptId(Long deptId) {
        return selectOne(StoreProfileDO::getDeptId, deptId);
    }

    default StoreProfileDO selectByStoreCode(String storeCode) {
        return selectOne(StoreProfileDO::getStoreCode, storeCode);
    }

    default int updateByIdAndVersion(StoreProfileDO profile, Integer originalVersion) {
        return update(profile, new LambdaQueryWrapperX<StoreProfileDO>()
                .eq(StoreProfileDO::getId, profile.getId())
                .eq(StoreProfileDO::getVersion, originalVersion));
    }

}
