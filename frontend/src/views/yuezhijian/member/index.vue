<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="76px"
    >
      <el-form-item label="会员编号" prop="memberNo"
        ><el-input
          v-model="queryParams.memberNo"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
      /></el-form-item>
      <el-form-item label="姓名" prop="fullName"
        ><el-input
          v-model="queryParams.fullName"
          clearable
          class="!w-180px"
          @keyup.enter="handleQuery"
      /></el-form-item>
      <el-form-item label="手机号" prop="mobile"
        ><el-input
          v-model="queryParams.mobile"
          clearable
          class="!w-180px"
          maxlength="11"
          @keyup.enter="handleQuery"
      /></el-form-item>
      <el-form-item label="归属门店" prop="ownerStoreDeptId">
        <el-select v-model="queryParams.ownerStoreDeptId" clearable class="!w-200px">
          <el-option
            v-for="store in storeList"
            :key="store.deptId"
            :label="store.deptName"
            :value="store.deptId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="lifecycleStatus">
        <el-select v-model="queryParams.lifecycleStatus" clearable class="!w-140px">
          <el-option label="正常" value="ACTIVE" /><el-option
            label="冻结"
            value="FROZEN"
          /><el-option label="流失" value="LOST" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon class="mr-5px" icon="ep:refresh" />重置</el-button>
        <el-button
          v-hasPermi="['yuezhijian:member:create']"
          type="primary"
          plain
          @click="openCreate"
          ><Icon class="mr-5px" icon="ep:plus" />新建会员</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="会员编号" prop="memberNo" width="150" />
      <el-table-column label="会员卡号" prop="membershipCardNo" width="150" />
      <el-table-column label="姓名" prop="fullName" min-width="110" />
      <el-table-column label="手机号" prop="maskedMobile" width="125" />
      <el-table-column label="入会门店" prop="joinStoreName" min-width="130" />
      <el-table-column label="归属门店" prop="ownerStoreName" min-width="130" />
      <el-table-column label="顾问" prop="advisorName" min-width="100"
        ><template #default="scope">{{ scope.row.advisorName || '-' }}</template></el-table-column
      >
      <el-table-column label="来源" prop="sourceType" width="100" />
      <el-table-column label="状态" width="90"
        ><template #default="scope"
          ><el-tag :type="scope.row.lifecycleStatus === 'ACTIVE' ? 'success' : 'warning'">{{
            lifecycleLabel(scope.row.lifecycleStatus)
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="建档时间" prop="createTime" width="180" :formatter="dateFormatter" />
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog v-model="dialogVisible" title="新建会员" width="660px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="rules"
      label-width="92px"
    >
      <el-row :gutter="16">
        <el-col :span="12"
          ><el-form-item label="会员姓名" prop="fullName"
            ><el-input v-model="formData.fullName" maxlength="30" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="昵称"
            ><el-input v-model="formData.nickname" maxlength="30" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="手机号" prop="mobile"
            ><el-input v-model="formData.mobile" maxlength="11" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="会员卡号"
            ><el-input
              v-model="formData.membershipCardNo"
              maxlength="64"
              placeholder="不填则自动生成" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="性别"
            ><el-radio-group v-model="formData.sex"
              ><el-radio :value="1">男</el-radio><el-radio :value="2">女</el-radio
              ><el-radio :value="0">未知</el-radio></el-radio-group
            ></el-form-item
          ></el-col
        >
        <el-col :span="12"
          ><el-form-item label="生日"
            ><el-date-picker
              v-model="formData.birthday"
              type="date"
              value-format="YYYY-MM-DD"
              class="w-100%" /></el-form-item
        ></el-col>
        <el-col :span="24"
          ><el-form-item label="邮箱"
            ><el-input v-model="formData.email" maxlength="50" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="入会门店" prop="joinStoreDeptId"
            ><el-select
              v-model="formData.joinStoreDeptId"
              class="w-100%"
              @change="defaultOwnerStore"
              ><el-option
                v-for="store in storeList"
                :key="store.deptId"
                :label="store.deptName"
                :value="store.deptId" /></el-select></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="归属门店" prop="ownerStoreDeptId"
            ><el-select v-model="formData.ownerStoreDeptId" class="w-100%"
              ><el-option
                v-for="store in storeList"
                :key="store.deptId"
                :label="store.deptName"
                :value="store.deptId" /></el-select></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="顾问"
            ><el-select v-model="formData.advisorUserId" clearable filterable class="w-100%"
              ><el-option
                v-for="employee in advisorList"
                :key="employee.userId"
                :label="employee.nickname"
                :value="employee.userId" /></el-select></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="来源"
            ><el-select v-model="formData.sourceType" class="w-100%"
              ><el-option label="手工建档" value="MANUAL" /><el-option
                label="批量导入"
                value="IMPORT" /><el-option label="线上" value="ONLINE" /><el-option
                label="转介绍"
                value="REFERRAL" /></el-select></el-form-item
        ></el-col>
      </el-row>
    </el-form>
    <template #footer
      ><el-button type="primary" :disabled="formLoading" @click="submitCreate">创建</el-button
      ><el-button @click="dialogVisible = false">取消</el-button></template
    >
  </Dialog>
</template>

<script lang="ts" setup>
import * as EmployeeApi from '@/api/yuezhijian/employee'
import * as MemberApi from '@/api/yuezhijian/member'
import * as StoreApi from '@/api/yuezhijian/store'
import { dateFormatter } from '@/utils/formatTime'

defineOptions({ name: 'YuezhijianMember' })

const message = useMessage()
const loading = ref(false)
const formLoading = ref(false)
const total = ref(0)
const list = ref<MemberApi.MemberProfileVO[]>([])
const storeList = ref<StoreApi.StoreProfileVO[]>([])
const advisorList = ref<EmployeeApi.EmployeeProfileVO[]>([])
const queryFormRef = ref()
const queryParams = reactive<MemberApi.MemberProfilePageReqVO>({ pageNo: 1, pageSize: 10 })
const dialogVisible = ref(false)
const formRef = ref()
const emptyForm = (): MemberApi.MemberProfileCreateReqVO => ({ sex: 2, sourceType: 'MANUAL' })
const formData = ref<MemberApi.MemberProfileCreateReqVO>(emptyForm())
const rules = {
  fullName: [{ required: true, message: '请输入会员姓名', trigger: 'blur' }],
  mobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的11位手机号', trigger: 'blur' }
  ],
  joinStoreDeptId: [{ required: true, message: '请选择入会门店', trigger: 'change' }],
  ownerStoreDeptId: [{ required: true, message: '请选择归属门店', trigger: 'change' }]
}

const lifecycleLabels: Record<string, string> = { ACTIVE: '正常', FROZEN: '冻结', LOST: '流失' }
const lifecycleLabel = (status?: string) => lifecycleLabels[status || ''] || status || '-'
const getList = async () => {
  loading.value = true
  try {
    const data = await MemberApi.getMemberProfilePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}
const openCreate = () => {
  formData.value = emptyForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const defaultOwnerStore = (deptId: number) => {
  if (!formData.value.ownerStoreDeptId) formData.value.ownerStoreDeptId = deptId
}
const submitCreate = async () => {
  if (!(await formRef.value?.validate())) return
  formLoading.value = true
  try {
    await MemberApi.createMemberProfile(formData.value)
    message.success('会员已建档')
    dialogVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}

onMounted(async () => {
  const [stores, employees] = await Promise.all([
    StoreApi.getStoreProfileList(),
    EmployeeApi.getEmployeeProfileList()
  ])
  storeList.value = stores
  advisorList.value = employees.filter((employee) => employee.employmentStatus === 'ACTIVE')
  await getList()
})
</script>
