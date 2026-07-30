package cn.iocoder.yudao.module.yuezhijian.service.employee;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.employee.vo.EmployeeProfileRespVO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.employee.vo.EmployeeProfileSaveReqVO;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.employee.EmployeeProfileDO;
import cn.iocoder.yudao.module.yuezhijian.dal.mysql.employee.EmployeeProfileMapper;
import cn.iocoder.yudao.module.yuezhijian.dal.mysql.store.StoreProfileMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.module.yuezhijian.enums.ErrorCodeConstants.*;

@Service
public class EmployeeProfileServiceImpl implements EmployeeProfileService {

    @Resource
    private EmployeeProfileMapper employeeProfileMapper;
    @Resource
    private StoreProfileMapper storeProfileMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileRespVO save(EmployeeProfileSaveReqVO reqVO) {
        adminUserApi.validateUser(reqVO.getUserId());
        AdminUserRespDTO user = adminUserApi.getUser(reqVO.getUserId());
        if (!reqVO.getPrimaryStoreDeptId().equals(user.getDeptId())) {
            throw exception(EMPLOYEE_STORE_MISMATCH);
        }
        if (storeProfileMapper.selectByDeptId(reqVO.getPrimaryStoreDeptId()) == null) {
            throw exception(STORE_PROFILE_NOT_EXISTS);
        }
        if (reqVO.getLeaveDate() != null && reqVO.getHireDate() != null
                && reqVO.getLeaveDate().isBefore(reqVO.getHireDate())) {
            throw new IllegalArgumentException("离职日期不能早于入职日期");
        }

        String employeeNo = reqVO.getEmployeeNo().trim().toUpperCase();
        EmployeeProfileDO existingNo = DataPermissionUtils.executeIgnore(
                () -> employeeProfileMapper.selectByEmployeeNo(employeeNo));
        EmployeeProfileDO current = employeeProfileMapper.selectByUserId(reqVO.getUserId());
        if (existingNo != null && (current == null || !existingNo.getId().equals(current.getId()))) {
            throw exception(EMPLOYEE_NO_EXISTS);
        }

        EmployeeProfileDO profile = BeanUtils.toBean(reqVO, EmployeeProfileDO.class);
        profile.setEmployeeNo(employeeNo);
        if (current == null) {
            employeeProfileMapper.insert(profile);
        } else {
            if (reqVO.getVersion() == null || !reqVO.getVersion().equals(current.getVersion())) {
                throw exception(DATA_CHANGED);
            }
            profile.setId(current.getId());
            profile.setVersion(current.getVersion() + 1);
            if (employeeProfileMapper.updateByIdAndVersion(profile, current.getVersion()) != 1) {
                throw exception(DATA_CHANGED);
            }
        }
        return toResp(employeeProfileMapper.selectById(profile.getId()), user,
                deptApi.getDept(reqVO.getPrimaryStoreDeptId()));
    }

    @Override
    public EmployeeProfileRespVO getByUserId(Long userId) {
        EmployeeProfileDO profile = employeeProfileMapper.selectByUserId(userId);
        if (profile == null) {
            throw exception(EMPLOYEE_PROFILE_NOT_EXISTS);
        }
        return toResp(profile, adminUserApi.getUser(userId), deptApi.getDept(profile.getPrimaryStoreDeptId()));
    }

    @Override
    public List<EmployeeProfileRespVO> getList() {
        List<EmployeeProfileDO> profiles = employeeProfileMapper.selectList();
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(convertSet(profiles, EmployeeProfileDO::getUserId));
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(
                convertSet(profiles, EmployeeProfileDO::getPrimaryStoreDeptId));
        return profiles.stream().map(profile -> toResp(profile, userMap.get(profile.getUserId()),
                deptMap.get(profile.getPrimaryStoreDeptId()))).toList();
    }

    private EmployeeProfileRespVO toResp(EmployeeProfileDO profile, AdminUserRespDTO user, DeptRespDTO dept) {
        EmployeeProfileRespVO resp = BeanUtils.toBean(profile, EmployeeProfileRespVO.class);
        if (user != null) {
            resp.setNickname(user.getNickname());
            resp.setPostIds(user.getPostIds());
        }
        if (dept != null) {
            resp.setPrimaryStoreName(dept.getName());
        }
        return resp;
    }

}
