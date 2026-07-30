package cn.iocoder.yudao.module.yuezhijian.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * 悦指间错误码，区间 1-120-000-000。
 */
public interface ErrorCodeConstants {

    ErrorCode STORE_PROFILE_NOT_EXISTS = new ErrorCode(1_120_000_001, "门店档案不存在");
    ErrorCode STORE_CODE_EXISTS = new ErrorCode(1_120_000_002, "门店编码已存在");
    ErrorCode EMPLOYEE_PROFILE_NOT_EXISTS = new ErrorCode(1_120_001_001, "员工档案不存在");
    ErrorCode EMPLOYEE_NO_EXISTS = new ErrorCode(1_120_001_002, "员工编号已存在");
    ErrorCode EMPLOYEE_STORE_MISMATCH = new ErrorCode(1_120_001_003, "员工账号所属部门与主门店不一致");
    ErrorCode MEMBER_PROFILE_NOT_EXISTS = new ErrorCode(1_120_002_001, "会员档案不存在");
    ErrorCode MEMBER_MOBILE_EXISTS = new ErrorCode(1_120_002_002, "该手机号已经存在会员档案");
    ErrorCode MEMBER_ADVISOR_INVALID = new ErrorCode(1_120_002_003, "顾问不存在、已停用或不属于会员归属门店");
    ErrorCode MEMBER_CARD_EXISTS = new ErrorCode(1_120_002_004, "会员卡号已存在");
    ErrorCode DATA_CHANGED = new ErrorCode(1_120_009_001, "数据已被他人修改，请刷新后重试");

}
