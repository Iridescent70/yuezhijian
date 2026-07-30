<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createEmployee, getEmployee, getEmployees, getPositions, updateEmployee } from '@/api/masterData'
import { useAuthStore } from '@/stores/auth'
import type { EmployeeSummary, PositionOption } from '@/types/api'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const employees = ref<EmployeeSummary[]>([])
const positions = ref<PositionOption[]>([])
const filters = reactive<{ keyword: string; storeId?: number }>({ keyword: '', storeId: undefined })
const form = reactive({
  employeeNo: '', name: '', mobile: '', positionId: undefined as number | undefined,
  primaryStoreId: auth.user?.currentStoreId as number | undefined, hireDate: '', leaveDate: '',
  canService: true, canSell: true, status: 'ACTIVE', version: '',
})

async function load() {
  loading.value = true
  try {
    ;[employees.value, positions.value] = await Promise.all([
      getEmployees({ keyword: filters.keyword || undefined, storeId: filters.storeId }),
      getPositions(),
    ])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '员工数据加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    employeeNo: '', name: '', mobile: '', positionId: positions.value[0]?.id,
    primaryStoreId: auth.user?.currentStoreId, hireDate: '', leaveDate: '',
    canService: true, canSell: true, status: 'ACTIVE', version: '',
  })
  dialogVisible.value = true
}

async function openEdit(value: unknown) {
  const row = value as EmployeeSummary
  try {
    const detail = await getEmployee(row.id)
    editingId.value = detail.id
    Object.assign(form, {
      employeeNo: detail.employeeNo,
      name: detail.name,
      mobile: '',
      positionId: detail.positionId,
      primaryStoreId: detail.storeId,
      hireDate: detail.hireDate ?? '',
      leaveDate: detail.leaveDate ?? '',
      canService: detail.canService,
      canSell: detail.canSell,
      status: detail.status,
      version: detail.version,
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '员工详情加载失败')
  }
}

async function submit() {
  if (!form.employeeNo.trim() || !form.name.trim() || !form.positionId || !form.primaryStoreId) {
    ElMessage.warning('请填写员工编号、姓名、职务和所属门店')
    return
  }
  if (editingId.value && form.status === 'LEFT' && !form.leaveDate) {
    ElMessage.warning('离职员工必须填写离职日期')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateEmployee(editingId.value, {
        name: form.name,
        mobile: form.mobile || undefined,
        positionId: form.positionId,
        primaryStoreId: form.primaryStoreId,
        hireDate: form.hireDate || undefined,
        leaveDate: form.status === 'LEFT' ? form.leaveDate || undefined : undefined,
        canService: form.canService,
        canSell: form.canSell,
        status: form.status,
        version: form.version,
      })
      ElMessage.success('员工资料已更新')
    } else {
      await createEmployee({
        employeeNo: form.employeeNo,
        name: form.name,
        mobile: form.mobile || undefined,
        positionId: form.positionId,
        primaryStoreId: form.primaryStoreId,
        hireDate: form.hireDate || undefined,
        canService: form.canService,
        canSell: form.canSell,
      })
      ElMessage.success('员工已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '员工创建失败')
  } finally {
    saving.value = false
  }
}

function employeeStatus(status: string) {
  return status === 'ACTIVE' ? '在职' : status === 'DISABLED' ? '停用' : status === 'LEFT' ? '离职' : status
}

function employeeStatusType(status: string): 'success' | 'info' | 'warning' {
  return status === 'ACTIVE' ? 'success' : status === 'LEFT' ? 'info' : 'warning'
}

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>员工管理</h1><p>维护门店员工、职务和可服务范围，手机号只显示后四位。</p></div>
      <el-button v-if="auth.hasPermission('org:employee:manage')" type="primary" @click="openCreate">新建员工</el-button>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="员工查询"><el-input v-model="filters.keyword" clearable placeholder="姓名或员工编号" /></el-form-item>
        <el-form-item label="所属门店">
          <el-select v-model="filters.storeId" clearable placeholder="全部门店" class="master-filter-select">
            <el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="employees" stripe row-key="id">
        <el-table-column prop="employeeNo" label="员工编号" width="130" />
        <el-table-column prop="name" label="姓名" min-width="140" />
        <el-table-column prop="maskedMobile" label="手机号" width="150"><template #default="scope">{{ scope.row.maskedMobile ?? '—' }}</template></el-table-column>
        <el-table-column prop="positionName" label="职务" width="150" />
        <el-table-column prop="storeName" label="所属门店" min-width="180" />
        <el-table-column prop="hireDate" label="入职日期" width="120"><template #default="scope">{{ scope.row.hireDate ?? '—' }}</template></el-table-column>
        <el-table-column label="服务/销售" width="150"><template #default="scope">{{ scope.row.canService ? '可服务' : '不可服务' }} / {{ scope.row.canSell ? '可销售' : '不可销售' }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="employeeStatusType(scope.row.status)">{{ employeeStatus(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column v-if="auth.hasPermission('org:employee:manage')" label="操作" width="90" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑员工' : '新建员工'" width="680px" destroy-on-close>
      <el-form label-width="90px" class="dialog-form-grid">
        <el-form-item label="员工编号" required><el-input v-model="form.employeeNo" maxlength="64" :disabled="Boolean(editingId)" /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.mobile" maxlength="11" :placeholder="editingId ? '留空保留原手机号' : '选填，入库加密'" /></el-form-item>
        <el-form-item label="职务" required>
          <el-select v-model="form.positionId"><el-option v-for="item in positions" :key="item.id" :label="item.name" :value="item.id" /></el-select>
        </el-form-item>
        <el-form-item label="所属门店" required>
          <el-select v-model="form.primaryStoreId"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select>
        </el-form-item>
        <el-form-item label="入职日期"><el-date-picker v-model="form.hireDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" /></el-form-item>
        <el-form-item v-if="editingId" label="员工状态" required>
          <el-select v-model="form.status"><el-option label="在职" value="ACTIVE" /><el-option label="停用" value="DISABLED" /><el-option label="离职" value="LEFT" /></el-select>
        </el-form-item>
        <el-form-item v-if="editingId && form.status === 'LEFT'" label="离职日期" required><el-date-picker v-model="form.leaveDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" /></el-form-item>
        <el-form-item label="业务能力"><el-checkbox v-model="form.canService">可服务</el-checkbox><el-checkbox v-model="form.canSell">可销售</el-checkbox></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </section>
</template>
