package cn.iocoder.yudao.module.yuezhijian.framework.datapermission;

import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRuleCustomizer;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.employee.EmployeeProfileDO;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.member.MemberProfileDO;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.store.StoreProfileDO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将门店数据范围接入芋道部门权限：总部角色可看全部，门店角色只看授权部门，
 * SELF 范围的员工只能看到自己作为顾问负责的会员。
 */
@Configuration(proxyBeanMethods = false)
public class YuezhijianDataPermissionConfiguration {

    @Bean
    public DeptDataPermissionRuleCustomizer yuezhijianDeptDataPermissionRuleCustomizer() {
        return rule -> {
            rule.addDeptColumn(StoreProfileDO.class, "dept_id");
            rule.addDeptColumn(EmployeeProfileDO.class, "primary_store_dept_id");
            rule.addUserColumn(EmployeeProfileDO.class, "user_id");
            rule.addDeptColumn(MemberProfileDO.class, "owner_store_dept_id");
            rule.addUserColumn(MemberProfileDO.class, "advisor_user_id");
        };
    }

}
