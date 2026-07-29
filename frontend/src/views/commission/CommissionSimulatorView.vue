<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCommissionPlans, simulateCommissionPlan } from '@/api/commission'
import { getEmployees } from '@/api/masterData'
import { getStores } from '@/api/platform'
import type {
  CommissionPlan, CommissionSimulationResult, EmployeeSummary, StoreSummary,
} from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const loading = ref(false)
const plans = ref<CommissionPlan[]>([])
const stores = ref<StoreSummary[]>([])
const employees = ref<EmployeeSummary[]>([])
const result = ref<CommissionSimulationResult>()
const form = reactive({
  planId: undefined as number | undefined,
  storeId: undefined as number | undefined,
  employeeId: undefined as number | undefined,
  businessDate: new Date().toISOString().slice(0, 10),
  performanceAmount: 168,
  itemCount: 1,
})

const modeLabels: Record<string, string> = {
  RATE: '按比例', FIXED: '固定金额', NONE: '不计提成',
}
const sceneLabels: Record<string, string> = {
  SERVICE: '服务项目', CARD_SALE: '次卡销售', CARD_CONSUME: '次卡实耗',
}

async function loadEmployees() {
  employees.value = form.storeId ? await getEmployees({ storeId: form.storeId }) : []
  if (!employees.value.some(item => item.id === form.employeeId)) {
    form.employeeId = employees.value[0]?.id
  }
  result.value = undefined
}

async function simulate() {
  if (!form.planId || !form.storeId || !form.employeeId || !form.businessDate) {
    ElMessage.warning('请选择方案、门店、员工和业务日期')
    return
  }
  loading.value = true
  try {
    result.value = await simulateCommissionPlan(form.planId, {
      employeeId: form.employeeId,
      storeId: form.storeId,
      businessDate: form.businessDate,
      performanceAmount: form.performanceAmount,
      itemCount: form.itemCount,
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '测算失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    [plans.value, stores.value] = await Promise.all([
      getCommissionPlans(), getStores(),
    ])
    form.planId = plans.value.find(item => item.status === 'ACTIVE')?.id
    form.storeId = stores.value.find(item => item.status === 'ACTIVE' && item.code !== 'HQ')?.id
      ?? stores.value.find(item => item.status === 'ACTIVE')?.id
    await loadEmployees()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '测算基础资料加载失败')
  }
})
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>薪资测算</h1><p>用指定方案和样例业绩计算提成，只做测算，不写入提成流水。</p></div>
    </div>
    <el-alert type="info" :closable="false" show-icon>
      当前测算支持已确认的比例、固定金额和不计提成规则；阶梯公式待甲方确认口径后接入同一页面。
    </el-alert>
    <div class="simulator-grid">
      <el-card shadow="never">
        <template #header><strong>测算条件</strong></template>
        <el-form label-width="96px" @submit.prevent="simulate">
          <el-form-item label="提成方案">
            <el-select v-model="form.planId" filterable placeholder="请选择">
              <el-option v-for="item in plans" :key="item.id" :value="item.id"
                :label="`${item.name} v${item.ruleVersion}（${sceneLabels[item.scene]}）`" />
            </el-select>
          </el-form-item>
          <el-form-item label="门店">
            <el-select v-model="form.storeId" placeholder="请选择" @change="loadEmployees">
              <el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="员工">
            <el-select v-model="form.employeeId" filterable placeholder="请选择">
              <el-option v-for="item in employees" :key="item.id" :value="item.id"
                :label="`${item.name}（${item.positionName ?? '未设职务'}）`" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务日期"><el-date-picker v-model="form.businessDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="样例业绩"><el-input-number v-model="form.performanceAmount" :min="0" :precision="2" /></el-form-item>
          <el-form-item label="业务数量"><el-input-number v-model="form.itemCount" :min="1" :max="10000" :precision="0" /></el-form-item>
          <el-form-item><el-button type="primary" native-type="submit" :loading="loading">开始测算</el-button></el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header><strong>测算结果</strong></template>
        <el-empty v-if="!result" description="请先输入条件并开始测算" />
        <template v-else>
          <el-alert v-if="result.warnings.length" type="warning" :closable="false" show-icon>
            {{ result.warnings.join('；') }}
          </el-alert>
          <el-tag v-else type="success">适用性检查通过</el-tag>
          <div class="result-amount"><span>预计提成</span><strong>{{ formatMoney(result.commissionAmount) }}</strong></div>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="方案">{{ result.planName }} v{{ result.planRuleVersion }}</el-descriptions-item>
            <el-descriptions-item label="规则">{{ modeLabels[result.calculationMode] }}</el-descriptions-item>
            <el-descriptions-item label="员工 / 门店">{{ result.employeeName }} / {{ result.storeName }}</el-descriptions-item>
            <el-descriptions-item label="业绩 / 数量">{{ formatMoney(result.performanceAmount) }} / {{ result.itemCount }}</el-descriptions-item>
          </el-descriptions>
          <ol class="steps"><li v-for="step in result.calculationSteps" :key="step">{{ step }}</li></ol>
        </template>
      </el-card>
    </div>
  </section>
</template>

<style scoped>
.el-alert { margin-bottom: 16px; }
.simulator-grid { display: grid; grid-template-columns: minmax(360px, .8fr) minmax(440px, 1.2fr); gap: 16px; }
.simulator-grid :deep(.el-select), .simulator-grid :deep(.el-date-editor), .simulator-grid :deep(.el-input-number) { width: 100%; }
.result-amount { margin: 22px 0; padding: 20px; border-radius: 12px; background: var(--el-color-primary-light-9); display: flex; justify-content: space-between; align-items: center; }
.result-amount span { color: var(--muted); }
.result-amount strong { color: var(--el-color-primary); font-size: 30px; }
.steps { margin: 18px 0 0; padding-left: 22px; line-height: 2; color: var(--text); }
@media (max-width: 900px) { .simulator-grid { grid-template-columns: 1fr; } }
</style>
