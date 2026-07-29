<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createCancelReason,
  getCancelReason,
  getCancelReasons,
  updateCancelReason,
} from '@/api/cancelReason'
import { useAuthStore } from '@/stores/auth'
import type { CancelReason, CancelReasonBusinessType } from '@/types/api'

const auth = useAuthStore()
const businessLabels: Record<CancelReasonBusinessType, string> = {
  APPOINTMENT: '预约取消/爽约',
  BILL: '账单作废',
  HOME_SERVICE: '到家服务取消',
}
const businessOptions = Object.entries(businessLabels) as Array<[CancelReasonBusinessType, string]>
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const rows = ref<CancelReason[]>([])
const filters = reactive<{
  businessType?: CancelReasonBusinessType
  keyword: string
  status?: 'ACTIVE' | 'DISABLED'
}>({ keyword: '' })
const form = reactive({
  businessType: 'APPOINTMENT' as CancelReasonBusinessType,
  code: '', name: '', requiresNote: false, sortNo: 10,
  status: 'ACTIVE' as 'ACTIVE' | 'DISABLED', version: '',
})

async function load() {
  loading.value = true
  try {
    rows.value = await getCancelReasons({
      businessType: filters.businessType,
      keyword: filters.keyword.trim() || undefined,
      status: filters.status,
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '取消原因加载失败')
  } finally {
    loading.value = false
  }
}

function search() { void load() }

function reset() {
  Object.assign(filters, { businessType: undefined, keyword: '', status: undefined })
  void load()
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    businessType: filters.businessType ?? 'APPOINTMENT', code: '', name: '',
    requiresNote: false, sortNo: 10, status: 'ACTIVE', version: '',
  })
  dialogVisible.value = true
}

async function openEdit(value: unknown) {
  const row = value as CancelReason
  try {
    const detail = await getCancelReason(row.id)
    editingId.value = detail.id
    Object.assign(form, {
      businessType: detail.businessType, code: detail.code, name: detail.name,
      requiresNote: detail.requiresNote, sortNo: detail.sortNo,
      status: detail.status, version: detail.version,
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '取消原因详情加载失败')
  }
}

async function submit() {
  const code = form.code.trim().toUpperCase()
  if (!/^[A-Z][A-Z0-9_]{0,63}$/.test(code)) {
    ElMessage.warning('原因编号只能使用大写字母、数字和下划线，且必须以字母开头')
    return
  }
  if (!form.name.trim()) { ElMessage.warning('请输入原因名称'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await updateCancelReason(editingId.value, {
        name: form.name.trim(), requiresNote: form.requiresNote, sortNo: form.sortNo,
        status: form.status, version: form.version,
      })
      ElMessage.success('取消原因已更新')
    } else {
      await createCancelReason({
        businessType: form.businessType, code, name: form.name.trim(),
        requiresNote: form.requiresNote, sortNo: form.sortNo,
      })
      ElMessage.success('取消原因已新建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '取消原因保存失败')
  } finally {
    saving.value = false
  }
}

function dateTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '—' }

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>取消原因</h1><p>统一维护预约取消/爽约、账单作废和到家服务取消时可选择的原因。</p></div>
      <el-button v-if="auth.hasPermission('system:cancel-reason:manage')" type="primary" @click="openCreate">新建取消原因</el-button>
    </div>
    <el-alert type="info" :closable="false" show-icon>
      停用后不再出现在新业务选择项中，历史预约和账单仍保留原原因编号；编号和适用业务创建后不能修改。
    </el-alert>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="search">
        <el-form-item label="适用业务"><el-select v-model="filters.businessType" clearable placeholder="全部" style="width: 170px"><el-option v-for="[value, label] in businessOptions" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="查询"><el-input v-model="filters.keyword" clearable maxlength="200" placeholder="编号或名称" style="width: 190px" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column label="适用业务" min-width="145"><template #default="scope">{{ businessLabels[scope.row.businessType as CancelReasonBusinessType] ?? scope.row.businessType }}</template></el-table-column>
        <el-table-column prop="code" label="原因编号" min-width="160" />
        <el-table-column prop="name" label="原因名称" min-width="190" />
        <el-table-column label="必须说明" width="95"><template #default="scope">{{ scope.row.requiresNote ? '是' : '否' }}</template></el-table-column>
        <el-table-column prop="sortNo" label="排序" width="80" />
        <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="updatedByName" label="操作人" width="120" />
        <el-table-column label="操作时间" width="170"><template #default="scope">{{ dateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column v-if="auth.hasPermission('system:cancel-reason:manage')" label="操作" width="80" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑取消原因' : '新建取消原因'" width="600px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="适用业务" required><el-select v-model="form.businessType" :disabled="Boolean(editingId)" class="dialog-full-control"><el-option v-for="[value, label] in businessOptions" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="原因编号" required><el-input v-model="form.code" :disabled="Boolean(editingId)" maxlength="64" placeholder="如 CUSTOMER_CHANGE" /></el-form-item>
        <el-form-item label="原因名称" required><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item label="必须说明"><el-switch v-model="form.requiresNote" /></el-form-item>
        <el-form-item label="显示顺序"><el-input-number v-model="form.sortNo" :min="0" :max="9999" /></el-form-item>
        <el-form-item v-if="editingId" label="状态"><el-radio-group v-model="form.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.el-alert { margin-bottom: 16px; }
</style>
