package cn.iocoder.yudao.module.yuezhijian.service.member;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.TerminalEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserCreateReqDTO;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.member.framework.security.MemberMobileProtectionUtils;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo.MemberProfileCreateReqVO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo.MemberProfilePageReqVO;
import cn.iocoder.yudao.module.yuezhijian.controller.admin.member.vo.MemberProfileRespVO;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.employee.EmployeeProfileDO;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.member.MemberProfileDO;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.member.MembershipCardDO;
import cn.iocoder.yudao.module.yuezhijian.dal.dataobject.store.StoreProfileDO;
import cn.iocoder.yudao.module.yuezhijian.dal.mysql.employee.EmployeeProfileMapper;
import cn.iocoder.yudao.module.yuezhijian.dal.mysql.member.MemberProfileMapper;
import cn.iocoder.yudao.module.yuezhijian.dal.mysql.member.MembershipCardMapper;
import cn.iocoder.yudao.module.yuezhijian.dal.mysql.store.StoreProfileMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.yudao.module.yuezhijian.enums.ErrorCodeConstants.*;

@Service
public class MemberProfileServiceImpl implements MemberProfileService {

    @Resource
    private MemberProfileMapper memberProfileMapper;
    @Resource
    private MembershipCardMapper membershipCardMapper;
    @Resource
    private StoreProfileMapper storeProfileMapper;
    @Resource
    private EmployeeProfileMapper employeeProfileMapper;
    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private DeptApi deptApi;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberProfileRespVO create(MemberProfileCreateReqVO reqVO) {
        String mobile = MemberMobileProtectionUtils.normalize(reqVO.getMobile());
        String mobileHash = MemberMobileProtectionUtils.searchableHash(mobile);
        if (DataPermissionUtils.executeIgnore(() -> memberProfileMapper.selectByMobileHash(mobileHash)) != null) {
            throw exception(MEMBER_MOBILE_EXISTS);
        }
        Long ownerStoreDeptId = reqVO.getOwnerStoreDeptId() == null
                ? reqVO.getJoinStoreDeptId() : reqVO.getOwnerStoreDeptId();
        requireStore(reqVO.getJoinStoreDeptId());
        requireStore(ownerStoreDeptId);
        validateAdvisor(reqVO.getAdvisorUserId(), ownerStoreDeptId);
        String requestedCardNo = StrUtil.trim(reqVO.getMembershipCardNo());
        if (StrUtil.isNotBlank(requestedCardNo)) {
            requestedCardNo = requestedCardNo.toUpperCase();
            String finalRequestedCardNo = requestedCardNo;
            if (DataPermissionUtils.executeIgnore(
                    () -> membershipCardMapper.selectByCardNo(finalRequestedCardNo)) != null) {
                throw exception(MEMBER_CARD_EXISTS);
            }
        }

        MemberUserCreateReqDTO accountReq = new MemberUserCreateReqDTO();
        accountReq.setMobile(mobile);
        accountReq.setNickname(StrUtil.blankToDefault(reqVO.getNickname(), reqVO.getFullName()).trim());
        accountReq.setName(reqVO.getFullName().trim());
        accountReq.setSex(reqVO.getSex());
        accountReq.setBirthday(reqVO.getBirthday() == null ? null : reqVO.getBirthday().atStartOfDay());
        accountReq.setEmail(StrUtil.trim(reqVO.getEmail()));
        accountReq.setRegisterIp(getClientIP());
        accountReq.setRegisterTerminal(TerminalEnum.UNKNOWN.getTerminal());

        try {
            MemberUserRespDTO account = memberUserApi.createUser(accountReq);
            MemberProfileDO profile = new MemberProfileDO();
            profile.setMemberUserId(account.getId());
            profile.setMemberNo(String.format("M%012d", account.getId()));
            profile.setFullName(reqVO.getFullName().trim());
            profile.setMobileHash(mobileHash);
            profile.setMobileLast4(MemberMobileProtectionUtils.last4(mobile));
            profile.setJoinStoreDeptId(reqVO.getJoinStoreDeptId());
            profile.setOwnerStoreDeptId(ownerStoreDeptId);
            profile.setAdvisorUserId(reqVO.getAdvisorUserId());
            profile.setSourceType(StrUtil.blankToDefault(reqVO.getSourceType(), "MANUAL"));
            profile.setSpecial(false);
            profile.setLifecycleStatus("ACTIVE");
            memberProfileMapper.insert(profile);

            MembershipCardDO card = new MembershipCardDO();
            card.setMemberProfileId(profile.getId());
            card.setCardNo(StrUtil.blankToDefault(requestedCardNo,
                    String.format("C%012d", profile.getId())));
            card.setRegisterStoreDeptId(reqVO.getJoinStoreDeptId());
            card.setStatus("ACTIVE");
            membershipCardMapper.insert(card);
            return toResp(profile, card, account, null, null, null);
        } catch (DuplicateKeyException exception) {
            throw exception(DATA_CHANGED);
        }
    }

    @Override
    public MemberProfileRespVO get(Long id) {
        MemberProfileDO profile = memberProfileMapper.selectById(id);
        if (profile == null) {
            throw exception(MEMBER_PROFILE_NOT_EXISTS);
        }
        return enrich(profile);
    }

    @Override
    public PageResult<MemberProfileRespVO> getPage(MemberProfilePageReqVO reqVO) {
        PageResult<MemberProfileDO> page = memberProfileMapper.selectPage(reqVO);
        List<MemberProfileDO> profiles = page.getList();
        Map<Long, MemberUserRespDTO> accountMap = memberUserApi.getUserMap(
                convertSet(profiles, MemberProfileDO::getMemberUserId));
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(profiles.stream()
                .flatMap(profile -> java.util.stream.Stream.of(profile.getJoinStoreDeptId(),
                        profile.getOwnerStoreDeptId())).collect(java.util.stream.Collectors.toSet()));
        Map<Long, AdminUserRespDTO> advisorMap = adminUserApi.getUserMap(profiles.stream()
                .map(MemberProfileDO::getAdvisorUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<MemberProfileRespVO> list = profiles.stream().map(profile -> toResp(profile,
                membershipCardMapper.selectActiveByMemberProfileId(profile.getId()),
                accountMap.get(profile.getMemberUserId()), deptMap.get(profile.getJoinStoreDeptId()),
                deptMap.get(profile.getOwnerStoreDeptId()), advisorMap.get(profile.getAdvisorUserId()))).toList();
        return new PageResult<>(list, page.getTotal());
    }

    private MemberProfileRespVO enrich(MemberProfileDO profile) {
        return toResp(profile, membershipCardMapper.selectActiveByMemberProfileId(profile.getId()),
                memberUserApi.getUser(profile.getMemberUserId()), deptApi.getDept(profile.getJoinStoreDeptId()),
                deptApi.getDept(profile.getOwnerStoreDeptId()),
                profile.getAdvisorUserId() == null ? null : adminUserApi.getUser(profile.getAdvisorUserId()));
    }

    private StoreProfileDO requireStore(Long deptId) {
        StoreProfileDO store = storeProfileMapper.selectByDeptId(deptId);
        if (store == null) {
            throw exception(STORE_PROFILE_NOT_EXISTS);
        }
        return store;
    }

    private void validateAdvisor(Long advisorUserId, Long ownerStoreDeptId) {
        if (advisorUserId == null) {
            return;
        }
        AdminUserRespDTO advisor = adminUserApi.getUser(advisorUserId);
        EmployeeProfileDO employee = employeeProfileMapper.selectByUserId(advisorUserId);
        if (advisor == null || !CommonStatusEnum.isEnable(advisor.getStatus()) || employee == null
                || !ownerStoreDeptId.equals(employee.getPrimaryStoreDeptId())
                || !"ACTIVE".equals(employee.getEmploymentStatus())) {
            throw exception(MEMBER_ADVISOR_INVALID);
        }
    }

    private MemberProfileRespVO toResp(MemberProfileDO profile, MembershipCardDO card,
                                       MemberUserRespDTO account, DeptRespDTO joinStore,
                                       DeptRespDTO ownerStore, AdminUserRespDTO advisor) {
        MemberProfileRespVO resp = BeanUtils.toBean(profile, MemberProfileRespVO.class);
        resp.setMaskedMobile("*******" + profile.getMobileLast4());
        if (card != null) {
            resp.setMembershipCardNo(card.getCardNo());
        }
        if (joinStore != null) {
            resp.setJoinStoreName(joinStore.getName());
        }
        if (ownerStore != null) {
            resp.setOwnerStoreName(ownerStore.getName());
        }
        if (advisor != null) {
            resp.setAdvisorName(advisor.getNickname());
        }
        return resp;
    }

}
