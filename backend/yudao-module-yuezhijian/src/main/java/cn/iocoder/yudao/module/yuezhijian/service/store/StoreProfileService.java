package cn.iocoder.yudao.module.yuezhijian.service.store;

import cn.iocoder.yudao.module.yuezhijian.controller.admin.store.vo.StoreProfileRespVO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.store.vo.StoreProfileSaveReqVO;

import java.util.List;

public interface StoreProfileService {

    StoreProfileRespVO save(StoreProfileSaveReqVO reqVO);

    StoreProfileRespVO getByDeptId(Long deptId);

    List<StoreProfileRespVO> getList();

}
