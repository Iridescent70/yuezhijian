package cn.iocoder.yudao.module.yuezhijian.dal.mysql.employee;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.employee.EmployeeProfileDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmployeeProfileMapper extends BaseMapperX<EmployeeProfileDO> {

    default EmployeeProfileDO selectByUserId(Long userId) {
        return selectOne(EmployeeProfileDO::getUserId, userId);
    }

    default EmployeeProfileDO selectByEmployeeNo(String employeeNo) {
        return selectOne(EmployeeProfileDO::getEmployeeNo, employeeNo);
    }

    default int updateByIdAndVersion(EmployeeProfileDO profile, Integer originalVersion) {
        return update(profile, new LambdaQueryWrapperX<EmployeeProfileDO>()
                .eq(EmployeeProfileDO::getId, profile.getId())
                .eq(EmployeeProfileDO::getVersion, originalVersion));
    }

}
