<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { createCommissionPlan, getCommissionPlans, updateCommissionPlan } from '@/api/commission'
import { getPositions } from '@/api/masterData'
import { getStores } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'
import type {
  CommissionCalculationMode, CommissionPlan, CommissionScene, PositionOption, StoreSummary,
} from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const status = ref('')
const rows = ref<CommissionPlan[]>([])
const stores = ref<StoreSummary[]>([])
const positions = ref<PositionOption[]>([])
const dialogVisible = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  code: '', name: '', scene: 'SERVICE' as CommissionScene,
  calculationMode: 'RATE' as CommissionCalculationMode,
  ratePercent: 10, fixedAmount: 0,
  storeId: undefined as number | undefined,
  positionId: undefined as number | undefined,
  effectiveFrom: new Date().toISOString().slice(0, 10),
  effectiveTo: '', status: 'ACTIVE', version: '',
})

const sceneLabels: Record<CommissionScene, string> = {
  SERVICE: '服务项目', CARD_SALE: '次卡销售', CARD_CONSUME: '次卡实耗',
}
const modeLabels: Record<CommissionCalculationMode, string> = {
  RATE: '按比例', FIXED: '固定金额', NONE: '不计提成',
}

async function load() {
  loading.value = true
  try {
    rows.value = await getCommissionPlans({ keyword: keyword.value || undefined, status: status.value || undefined })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提成方案加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined, code: '', name: '', scene: 'SERVICE', calculationMode: 'RATE', ratePercent: 10,
    fixedAmount: 0, storeId: undefined, positionId: undefined,
    effectiveFrom: new Date().toISOString().slice(0, 10), effectiveTo: '', status: 'ACTIVE', version: '',
  })
  dialogVisible.value = true
}

function openEdit(row: CommissionPlan) {
  Object.assign(form, {
    id: row.id, code: row.code, name: row.name, scene: row.scene,
    calculationMode: row.calculationMode, ratePercent: Number(((row.rate ?? 0) * 100).toFixed(4)),
    fixedAmount: row.fixedAmount ?? 0, storeId: row.storeId, positionId: row.positionId,
    effectiveFrom: row.effectiveFrom, effectiveTo: row.effectiveTo ?? '', status: row.status, version: row.version,
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.code.trim() || !form.name.trim() || !form.effectiveFrom) {
    ElMessage.warning('请填写方案编码、名称和生效日期')
    return
  }
  saving.value = true
  const payload = {
    code: form.code.trim().toUpperCase(), name: form.name.trim(), scene: form.scene,
    calculationMode: form.calculationMode,
    rate: form.calculationMode === 'RATE' ? form.ratePercent / 100 : undefined,
    fixedAmount: form.calculationMode === 'FIXED' ? form.fixedAmount : undefined,
    storeId: form.storeId, positionId: form.positionId,
    effectiveFrom: form.effectiveFrom, effectiveTo: form.effectiveTo || undefined,
  }
  try {
    if (form.id) {
      await updateCommissionPlan(form.id, { ...payload, status: form.status, version: form.version })
    } else {
      await createCommissionPlan(payload)
    }
    ElMessage.success(form.id ? '方案已更新并生成新规则版本' : '提成方案已创建')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '方案保存失败')
  } finally {
    saving.value = false
  }
}

function editRow(row: unknown) { openEdit(row as CommissionPlan) }
function scopeText(rowValue: unknown) {
  const row = rowValue as CommissionPlan
  return [row.storeName ?? '全部门店', row.positionName ?? '全部职务'].join(' / ')
}
function ruleText(rowValue: unknown) {
  const row = rowValue as CommissionPlan
  if (row.calculationMode === 'RATE') return `${Number(((row.rate ?? 0) * 100).toFixed(4))}%`
  if (row.calculationMode === 'FIXED') return `${formatMoney(row.fixedAmount ?? 0)} / 项`
  return '不计提成'
}

onMounted(async () => {
  try {
    [stores.value, positions.value] = await Promise.all([getStores(), getPositions()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '基础资料加载失败')
  }
  await load()
})
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>提成方案</h1><p>方案按门店、职务和生效日期匹配；每次修改都会增加规则版本，历史流水不重算。</p></div>
      <div class="title-actions">
        <el-button @click="router.push('/app/commission/simulator')">薪资测算</el-button>
        <el-button v-if="auth.hasPermission('commission:plan:manage')" type="primary" @click="openCreate">新建方案</el-button>
      </div>
    </div>
    <el-alert type="warning" :closable="false" show-icon>
      当前已落地比例、固定金额和不计提成三种基础规则；阶梯、门店等级等合同组合规则将在后续规则引擎中补齐。
    </el-alert>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="关键词"><el-input v-model="keyword" clearable placeholder="方案名称或编码" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="status" clearable placeholder="全部" style="width: 130px"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="INACTIVE" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column prop="code" label="编码" min-width="130" />
        <el-table-column prop="name" label="方案名称" min-width="180" />
        <el-table-column label="业务场景" width="110"><template #default="scope">{{ sceneLabels[scope.row.scene as CommissionScene] }}</template></el-table-column>
        <el-table-column label="适用范围" min-width="190"><template #default="scope">{{ scopeText(scope.row) }}</template></el-table-column>
        <el-table-column label="规则" width="130"><template #default="scope"><strong>{{ ruleText(scope.row) }}</strong></template></el-table-column>
        <el-table-column label="有效期" min-width="190"><template #default="scope">{{ scope.row.effectiveFrom }} 至 {{ scope.row.effectiveTo || '长期' }}</template></el-table-column>
        <el-table-column label="版本" width="80"><template #default="scope">v{{ scope.row.ruleVersion }}</template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right"><template #default="scope"><el-button v-if="auth.hasPermission('commission:plan:manage')" link type="primary" @click="editRow(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑提成方案' : '新建提成方案'" width="660px">
      <el-form label-width="100px">
        <div class="form-grid">
          <el-form-item label="方案编码"><el-input v-model="form.code" :disabled="Boolean(form.id)" placeholder="例如 SERVICE_TECH_A" /></el-form-item>
          <el-form-item label="方案名称"><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="业务场景"><el-select v-model="form.scene"><el-option v-for="(label, value) in sceneLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
          <el-form-item label="计算方式"><el-select v-model="form.calculationMode"><el-option v-for="(label, value) in modeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
          <el-form-item v-if="form.calculationMode === 'RATE'" label="提成比例"><el-input-number v-model="form.ratePercent" :min="0" :max="100" :precision="4" /><span class="unit">%</span></el-form-item>
          <el-form-item v-if="form.calculationMode === 'FIXED'" label="固定金额"><el-input-number v-model="form.fixedAmount" :min="0" :precision="2" /></el-form-item>
          <el-form-item label="适用门店"><el-select v-model="form.storeId" clearable placeholder="全部门店"><el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="适用职务"><el-select v-model="form.positionId" clearable placeholder="全部职务"><el-option v-for="item in positions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="生效日期"><el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="失效日期"><el-date-picker v-model="form.effectiveTo" type="date" value-format="YYYY-MM-DD" clearable /></el-form-item>
          <el-form-item v-if="form.id" label="状态"><el-switch v-model="form.status" active-value="ACTIVE" inactive-value="INACTIVE" active-text="启用" inactive-text="停用" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px; }
.form-grid :deep(.el-select), .form-grid :deep(.el-date-editor), .form-grid :deep(.el-input-number) { width: 100%; }
.unit { margin-left: 8px; color: var(--muted); }
.el-alert { margin-bottom: 16px; }
@media (max-width: 720px) { .form-grid { grid-template-columns: 1fr; } }
</style>
