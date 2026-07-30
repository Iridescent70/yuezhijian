<template>
  <ContentWrap>
    <div class="mb-15px">
      <el-button v-hasPermi="['yuezhijian:store:update']" type="primary" plain @click="openForm()">
        <Icon class="mr-5px" icon="ep:plus" />新增门店档案
      </el-button>
      <span class="ml-12px text-13px text-gray-500"
        >组织层级在“系统管理 → 部门管理”维护，本页只维护门店业务属性。</span
      >
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="门店编码" prop="storeCode" min-width="120" />
      <el-table-column label="部门/门店" prop="deptName" min-width="150" />
      <el-table-column label="等级" prop="storeLevel" width="100" />
      <el-table-column label="地区" min-width="180">
        <template #default="scope">
          {{
            [scope.row.province, scope.row.city, scope.row.district].filter(Boolean).join(' / ') ||
            '-'
          }}
        </template>
      </el-table-column>
      <el-table-column label="地址" prop="address" min-width="220" show-overflow-tooltip />
      <el-table-column label="更新时间" prop="updateTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" fixed="right" width="90">
        <template #default="scope">
          <el-button
            v-hasPermi="['yuezhijian:store:update']"
            link
            type="primary"
            @click="openForm(scope.row)"
            >编辑</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <Dialog v-model="dialogVisible" title="门店业务档案" width="680px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="rules"
      label-width="100px"
    >
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="门店部门" prop="deptId">
            <el-select
              v-model="formData.deptId"
              class="w-100%"
              filterable
              :disabled="Boolean(formData.id)"
            >
              <el-option
                v-for="dept in deptList"
                :key="dept.id"
                :label="dept.name"
                :value="dept.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="门店编码" prop="storeCode">
            <el-input v-model="formData.storeCode" maxlength="32" />
          </el-form-item>
        </el-col>
        <el-col :span="12"
          ><el-form-item label="门店等级"><el-input v-model="formData.storeLevel" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="省"><el-input v-model="formData.province" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="市"><el-input v-model="formData.city" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="区县"><el-input v-model="formData.district" /></el-form-item
        ></el-col>
        <el-col :span="24"
          ><el-form-item label="详细地址"
            ><el-input v-model="formData.address" maxlength="255" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="经度"
            ><el-input-number
              v-model="formData.longitude"
              :min="-180"
              :max="180"
              :precision="7"
              class="w-100%" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="纬度"
            ><el-input-number
              v-model="formData.latitude"
              :min="-90"
              :max="90"
              :precision="7"
              class="w-100%" /></el-form-item
        ></el-col>
        <el-col :span="24">
          <el-form-item label="营业时间 JSON"
            ><el-input
              v-model="formData.businessHoursJson"
              type="textarea"
              :rows="3"
              placeholder="例如：周一 09:00-21:00（JSON）"
          /></el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button type="primary" :disabled="formLoading" @click="submitForm">保存</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as DeptApi from '@/api/system/dept'
import * as StoreApi from '@/api/yuezhijian/store'
import { dateFormatter } from '@/utils/formatTime'

defineOptions({ name: 'YuezhijianStore' })

const message = useMessage()
const loading = ref(false)
const formLoading = ref(false)
const dialogVisible = ref(false)
const list = ref<StoreApi.StoreProfileVO[]>([])
const deptList = ref<DeptApi.DeptVO[]>([])
const formRef = ref()
const emptyForm = (): StoreApi.StoreProfileVO => ({
  deptId: undefined,
  storeCode: '',
  version: undefined
})
const formData = ref<StoreApi.StoreProfileVO>(emptyForm())
const rules = {
  deptId: [{ required: true, message: '请选择门店部门', trigger: 'change' }],
  storeCode: [{ required: true, message: '请输入门店编码', trigger: 'blur' }]
}

const getList = async () => {
  loading.value = true
  try {
    list.value = await StoreApi.getStoreProfileList()
  } finally {
    loading.value = false
  }
}

const openForm = (row?: StoreApi.StoreProfileVO) => {
  formData.value = row ? { ...row } : emptyForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

const submitForm = async () => {
  if (!(await formRef.value?.validate())) return
  formLoading.value = true
  try {
    await StoreApi.saveStoreProfile(formData.value)
    message.success('门店档案已保存')
    dialogVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}

onMounted(async () => {
  const [depts] = await Promise.all([DeptApi.getSimpleDeptList(), getList()])
  deptList.value = depts
})
</script>
