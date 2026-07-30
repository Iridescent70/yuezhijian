package cn.iocoder.yudao.module.yuezhijian.service.employee;

import cn.iocoder.yudao.module.yuezhijian.controller.admin.employee.vo.EmployeeProfileRespVO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.employee.vo.EmployeeProfileSaveReqVO;

import java.util.List;

public interface EmployeeProfileService {

    EmployeeProfileRespVO save(EmployeeProfileSaveReqVO reqVO);

    EmployeeProfileRespVO getByUserId(Long userId);

    List<EmployeeProfileRespVO> getList();

}
