<template>
  <ContentWrap>
    <div class="mb-15px">
      <el-button
        v-hasPermi="['yuezhijian:employee:update']"
        type="primary"
        plain
        @click="openForm()"
      >
        <Icon class="mr-5px" icon="ep:plus" />新增员工档案
      </el-button>
      <span class="ml-12px text-13px text-gray-500"
        >登录账号、所属部门和岗位继续复用芋道用户/岗位管理。</span
      >
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="员工编号" prop="employeeNo" min-width="120" />
      <el-table-column label="姓名" prop="nickname" min-width="120" />
      <el-table-column label="主门店" prop="primaryStoreName" min-width="150" />
      <el-table-column label="服务" width="80"
        ><template #default="scope"
          ><el-tag :type="scope.row.canService ? 'success' : 'info'">{{
            scope.row.canService ? '可服务' : '否'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="销售" width="80"
        ><template #default="scope"
          ><el-tag :type="scope.row.canSell ? 'success' : 'info'">{{
            scope.row.canSell ? '可销售' : '否'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="在职状态" width="100"
        ><template #default="scope"
          ><el-tag :type="scope.row.employmentStatus === 'ACTIVE' ? 'success' : 'info'">{{
            scope.row.employmentStatus === 'ACTIVE' ? '在职' : '离职'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="入职日期" prop="hireDate" width="120" />
      <el-table-column label="操作" fixed="right" width="90">
        <template #default="scope"
          ><el-button
            v-hasPermi="['yuezhijian:employee:update']"
            link
            type="primary"
            @click="openForm(scope.row)"
            >编辑</el-button
          ></template
        >
      </el-table-column>
    </el-table>
  </ContentWrap>

  <Dialog v-model="dialogVisible" title="员工业务档案" width="620px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="系统用户" prop="userId">
        <el-select
          v-model="formData.userId"
          class="w-100%"
          filterable
          :disabled="Boolean(formData.id)"
          @change="syncStoreFromUser"
        >
          <el-option
            v-for="user in userList"
            :key="user.id"
            :label="`${user.nickname}（${user.username}）`"
            :value="user.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="员工编号" prop="employeeNo"
        ><el-input v-model="formData.employeeNo" maxlength="32"
      /></el-form-item>
      <el-form-item label="主门店" prop="primaryStoreDeptId">
        <el-select v-model="formData.primaryStoreDeptId" class="w-100%">
          <el-option
            v-for="store in storeList"
            :key="store.deptId"
            :label="`${store.deptName}（${store.storeCode}）`"
            :value="store.deptId"
          />
        </el-select>
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12"
          ><el-form-item label="入职日期"
            ><el-date-picker
              v-model="formData.hireDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="w-100%" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="离职日期"
            ><el-date-picker
              v-model="formData.leaveDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="w-100%" /></el-form-item
        ></el-col>
      </el-row>
      <el-form-item label="业务能力">
        <el-checkbox v-model="formData.canService">可提供服务</el-checkbox>
        <el-checkbox v-model="formData.canSell">可参与销售</el-checkbox>
      </el-form-item>
      <el-form-item label="在职状态" prop="employmentStatus">
        <el-radio-group v-model="formData.employmentStatus"
          ><el-radio value="ACTIVE">在职</el-radio
          ><el-radio value="LEAVE">离职</el-radio></el-radio-group
        >
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :disabled="formLoading" @click="submitForm">保存</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as UserApi from '@/api/system/user'
import * as EmployeeApi from '@/api/yuezhijian/employee'
import * as StoreApi from '@/api/yuezhijian/store'

defineOptions({ name: 'YuezhijianEmployee' })

const message = useMessage()
const loading = ref(false)
const formLoading = ref(false)
const dialogVisible = ref(false)
const list = ref<EmployeeApi.EmployeeProfileVO[]>([])
const userList = ref<UserApi.UserVO[]>([])
const storeList = ref<StoreApi.StoreProfileVO[]>([])
const formRef = ref()
const emptyForm = (): EmployeeApi.EmployeeProfileVO => ({
  employeeNo: '',
  canService: true,
  canSell: true,
  employmentStatus: 'ACTIVE'
})
const formData = ref<EmployeeApi.EmployeeProfileVO>(emptyForm())
const rules = {
  userId: [{ required: true, message: '请选择系统用户', trigger: 'change' }],
  employeeNo: [{ required: true, message: '请输入员工编号', trigger: 'blur' }],
  primaryStoreDeptId: [{ required: true, message: '请选择主门店', trigger: 'change' }],
  employmentStatus: [{ required: true, message: '请选择在职状态', trigger: 'change' }]
}

const getList = async () => {
  loading.value = true
  try {
    list.value = await EmployeeApi.getEmployeeProfileList()
  } finally {
    loading.value = false
  }
}

const openForm = (row?: EmployeeApi.EmployeeProfileVO) => {
  formData.value = row ? { ...row } : emptyForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

const syncStoreFromUser = (userId: number) => {
  const user = userList.value.find((item) => item.id === userId)
  if (user && storeList.value.some((store) => store.deptId === user.deptId)) {
    formData.value.primaryStoreDeptId = user.deptId
  }
}

const submitForm = async () => {
  if (!(await formRef.value?.validate())) return
  formLoading.value = true
  try {
    await EmployeeApi.saveEmployeeProfile(formData.value)
    message.success('员工档案已保存')
    dialogVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}

onMounted(async () => {
  const [users, stores] = await Promise.all([
    UserApi.getSimpleUserList(),
    StoreApi.getStoreProfileList()
  ])
  userList.value = users
  storeList.value = stores
  await getList()
})
</script>
