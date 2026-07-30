package cn.iocoder.yudao.module.yuezhijian.service.store;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.store.vo.StoreProfileRespVO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.store.vo.StoreProfileSaveReqVO;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.store.StoreProfileDO;
import cn.iocoder.yudao.module.yuezhijian.dal.mysql.store.StoreProfileMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.module.yuezhijian.enums.ErrorCodeConstants.*;

@Service
public class StoreProfileServiceImpl implements StoreProfileService {

    @Resource
    private StoreProfileMapper storeProfileMapper;
    @Resource
    private DeptApi deptApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreProfileRespVO save(StoreProfileSaveReqVO reqVO) {
        deptApi.validateDeptList(Collections.singleton(reqVO.getDeptId()));
        String storeCode = reqVO.getStoreCode().trim().toUpperCase();
        StoreProfileDO existingCode = DataPermissionUtils.executeIgnore(
                () -> storeProfileMapper.selectByStoreCode(storeCode));
        StoreProfileDO current = storeProfileMapper.selectByDeptId(reqVO.getDeptId());
        if (existingCode != null && (current == null || !existingCode.getId().equals(current.getId()))) {
            throw exception(STORE_CODE_EXISTS);
        }

        StoreProfileDO profile = BeanUtils.toBean(reqVO, StoreProfileDO.class);
        profile.setStoreCode(storeCode);
        profile.setStoreLevel(StrUtil.trim(reqVO.getStoreLevel()));
        if (current == null) {
            storeProfileMapper.insert(profile);
        } else {
            if (reqVO.getVersion() == null || !reqVO.getVersion().equals(current.getVersion())) {
                throw exception(DATA_CHANGED);
            }
            profile.setId(current.getId());
            profile.setVersion(current.getVersion() + 1);
            if (storeProfileMapper.updateByIdAndVersion(profile, current.getVersion()) != 1) {
                throw exception(DATA_CHANGED);
            }
        }
        return toResp(storeProfileMapper.selectById(profile.getId()), deptApi.getDept(reqVO.getDeptId()));
    }

    @Override
    public StoreProfileRespVO getByDeptId(Long deptId) {
        StoreProfileDO profile = storeProfileMapper.selectByDeptId(deptId);
        if (profile == null) {
            throw exception(STORE_PROFILE_NOT_EXISTS);
        }
        return toResp(profile, deptApi.getDept(deptId));
    }

    @Override
    public List<StoreProfileRespVO> getList() {
        List<StoreProfileDO> profiles = storeProfileMapper.selectList();
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(convertSet(profiles, StoreProfileDO::getDeptId));
        return profiles.stream().map(profile -> toResp(profile, deptMap.get(profile.getDeptId()))).toList();
    }

    private StoreProfileRespVO toResp(StoreProfileDO profile, DeptRespDTO dept) {
        StoreProfileRespVO resp = BeanUtils.toBean(profile, StoreProfileRespVO.class);
        if (dept != null) {
            resp.setDeptName(dept.getName());
        }
        return resp;
    }

}
