<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createPosition, getPosition, getPositions, updatePosition } from '@/api/masterData'
import { useAuthStore } from '@/stores/auth'
import type { PositionOption } from '@/types/api'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const positions = ref<PositionOption[]>([])
const form = reactive({
  code: '', name: '', level: 0, serviceRatePercent: 0, salesRatePercent: 0,
  status: 'ACTIVE', version: '',
})

async function load() {
  loading.value = true
  try {
    positions.value = await getPositions(false)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '职务数据加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    code: '', name: '', level: 0, serviceRatePercent: 0, salesRatePercent: 0,
    status: 'ACTIVE', version: '',
  })
  dialogVisible.value = true
}

async function openEdit(value: unknown) {
  const row = value as PositionOption
  try {
    const detail = await getPosition(row.id)
    editingId.value = detail.id
    Object.assign(form, {
      code: detail.code,
      name: detail.name,
      level: detail.level,
      serviceRatePercent: Number((detail.defaultServiceRate * 100).toFixed(4)),
      salesRatePercent: Number((detail.defaultSalesRate * 100).toFixed(4)),
      status: detail.status,
      version: detail.version,
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '职务详情加载失败')
  }
}

async function submit() {
  if (!form.code.trim() || !form.name.trim()) {
    ElMessage.warning('请填写职务编号和名称')
    return
  }
  saving.value = true
  const serviceRate = Number((form.serviceRatePercent / 100).toFixed(6))
  const salesRate = Number((form.salesRatePercent / 100).toFixed(6))
  try {
    if (editingId.value) {
      await updatePosition(editingId.value, {
        name: form.name,
        level: form.level,
        defaultServiceRate: serviceRate,
        defaultSalesRate: salesRate,
        status: form.status,
        version: form.version,
      })
      ElMessage.success('职务资料已更新')
    } else {
      await createPosition({
        code: form.code,
        name: form.name,
        level: form.level,
        defaultServiceRate: serviceRate,
        defaultSalesRate: salesRate,
      })
      ElMessage.success('职务已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '职务保存失败')
  } finally {
    saving.value = false
  }
}

function percent(value: number) {
  return `${Number((value * 100).toFixed(4))}%`
}

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>职务管理</h1><p>维护员工职务、等级和默认提成比例；具体业务方案可覆盖默认值。</p></div>
      <el-button v-if="auth.hasPermission('org:position:manage')" type="primary" @click="openCreate">新建职务</el-button>
    </div>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="positions" stripe row-key="id">
        <el-table-column prop="code" label="职务编号" width="170" />
        <el-table-column prop="name" label="职务名称" min-width="180" />
        <el-table-column prop="level" label="等级" width="90" />
        <el-table-column label="默认服务提成" width="150"><template #default="scope">{{ percent(scope.row.defaultServiceRate) }}</template></el-table-column>
        <el-table-column label="默认销售提成" width="150"><template #default="scope">{{ percent(scope.row.defaultSalesRate) }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column v-if="auth.hasPermission('org:position:manage')" label="操作" width="90" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑职务' : '新建职务'" width="620px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="职务编号" required><el-input v-model="form.code" maxlength="64" :disabled="Boolean(editingId)" /></el-form-item>
        <el-form-item label="职务名称" required><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="职务等级"><el-input-number v-model="form.level" :min="0" :max="999" /></el-form-item>
        <el-form-item label="默认服务提成"><el-input-number v-model="form.serviceRatePercent" :min="0" :max="100" :precision="4" :step="0.1" /><span class="rate-suffix">%</span></el-form-item>
        <el-form-item label="默认销售提成"><el-input-number v-model="form.salesRatePercent" :min="0" :max="100" :precision="4" :step="0.1" /><span class="rate-suffix">%</span></el-form-item>
        <el-form-item v-if="editingId" label="状态"><el-radio-group v-model="form.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.rate-suffix { margin-left: 8px; color: var(--el-text-color-secondary); }
</style>
