<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCommissionLedgers } from '@/api/commission'
import { getEmployees } from '@/api/masterData'
import { getStores } from '@/api/platform'
import type { CommissionLedgerItem, EmployeeSummary, StoreSummary } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const router = useRouter()
const loading = ref(false)
const rows = ref<CommissionLedgerItem[]>([])
const stores = ref<StoreSummary[]>([])
const employees = ref<EmployeeSummary[]>([])
const detail = ref<CommissionLedgerItem>()
const drawerVisible = ref(false)
const filters = reactive({
  storeId: undefined as number | undefined,
  employeeId: undefined as number | undefined,
  dates: [] as string[], direction: '', calculationStatus: '',
})

async function loadEmployees() {
  employees.value = await getEmployees({ storeId: filters.storeId })
  if (filters.employeeId && !employees.value.some(item => item.id === filters.employeeId)) {
    filters.employeeId = undefined
  }
}

async function load() {
  loading.value = true
  try {
    rows.value = await getCommissionLedgers({
      storeId: filters.storeId, employeeId: filters.employeeId,
      startDate: filters.dates[0], endDate: filters.dates[1],
      direction: filters.direction || undefined, calculationStatus: filters.calculationStatus || undefined,
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提成流水加载失败')
  } finally {
    loading.value = false
  }
}

function open(row: CommissionLedgerItem) { detail.value = row; drawerVisible.value = true }
function openRow(row: unknown) { open(row as CommissionLedgerItem) }
function dateTime(value?: string) { return value?.replace('T', ' ').slice(0, 19) ?? '—' }
function typeName(value: string) {
  return ({ SERVICE: '服务提成', CARD_SALE: '售卡提成', CARD_CONSUME: '次卡实耗' } as Record<string, string>)[value] ?? value
}
function sourceName(value: string) {
  return ({ BILL: '结算账单', BILL_REVERSAL: '账单冲销', CARD_SALE: '次卡销售', CARD_REFUND: '退卡', CARD_EXCHANGE: '换卡' } as Record<string, string>)[value] ?? value
}
function openSource(rowValue: unknown) {
  const row = rowValue as CommissionLedgerItem
  if (row.sourceType === 'BILL') void router.push(`/app/bills/${row.sourceId}`)
  else if (row.sourceType === 'BILL_REVERSAL') void router.push('/app/settlement/reversals')
}

onMounted(async () => {
  try {
    [stores.value, employees.value] = await Promise.all([getStores(), getEmployees()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '基础资料加载失败')
  }
  await load()
})
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>提成流水</h1><p>每笔提成都可以追溯员工、门店、来源单、方案版本和计算过程；负数表示冲回。</p></div>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="门店"><el-select v-model="filters.storeId" clearable placeholder="全部门店" style="width: 170px" @change="loadEmployees"><el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="员工"><el-select v-model="filters.employeeId" clearable filterable placeholder="全部员工" style="width: 150px"><el-option v-for="item in employees" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="业务日期"><el-date-picker v-model="filters.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" /></el-form-item>
        <el-form-item label="方向"><el-select v-model="filters.direction" clearable placeholder="全部" style="width: 120px"><el-option label="正向计提" value="POSITIVE" /><el-option label="负向冲回" value="NEGATIVE" /></el-select></el-form-item>
        <el-form-item label="计算状态"><el-select v-model="filters.calculationStatus" clearable placeholder="全部" style="width: 130px"><el-option label="已计算" value="CALCULATED" /><el-option label="待补规则" value="PENDING_RULE" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id" @row-click="openRow">
        <el-table-column label="流水号" min-width="190"><template #default="scope"><button class="member-link" type="button" @click.stop="openRow(scope.row)"><strong>{{ scope.row.ledgerNo }}</strong><small>{{ dateTime(scope.row.occurredAt) }}</small></button></template></el-table-column>
        <el-table-column prop="employeeName" label="员工" width="110" />
        <el-table-column prop="storeName" label="门店" min-width="150" />
        <el-table-column label="类型" width="110"><template #default="scope">{{ typeName(scope.row.commissionType) }}</template></el-table-column>
        <el-table-column label="来源单" min-width="170"><template #default="scope"><el-button link type="primary" @click.stop="openSource(scope.row)">{{ scope.row.sourceNo }}</el-button><div class="line-name">{{ scope.row.sourceLineName }}</div></template></el-table-column>
        <el-table-column label="计提基数" width="120" align="right"><template #default="scope">{{ formatMoney(scope.row.baseAmount) }}</template></el-table-column>
        <el-table-column label="提成金额" width="130" align="right"><template #default="scope"><strong :class="{ negative: scope.row.commissionAmount < 0 }">{{ formatMoney(scope.row.commissionAmount) }}</strong></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.calculationStatus === 'CALCULATED' ? 'success' : 'warning'">{{ scope.row.calculationStatus === 'CALCULATED' ? '已计算' : '待补规则' }}</el-tag></template></el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="提成计算详情" size="560px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="流水号">{{ detail.ledgerNo }}</el-descriptions-item>
          <el-descriptions-item label="员工 / 门店">{{ detail.employeeName }} / {{ detail.storeName }}</el-descriptions-item>
          <el-descriptions-item label="业务类型">{{ typeName(detail.commissionType) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ sourceName(detail.sourceType) }} {{ detail.sourceNo }}</el-descriptions-item>
          <el-descriptions-item label="服务项目">{{ detail.sourceLineName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="计提基数">{{ formatMoney(detail.baseAmount) }}</el-descriptions-item>
          <el-descriptions-item label="提成比例">{{ detail.rate == null ? '—' : `${Number((detail.rate * 100).toFixed(4))}%` }}</el-descriptions-item>
          <el-descriptions-item label="提成金额"><strong :class="{ negative: detail.commissionAmount < 0 }">{{ formatMoney(detail.commissionAmount) }}</strong></el-descriptions-item>
          <el-descriptions-item label="方案版本">{{ detail.planName ? `${detail.planName} v${detail.planRuleVersion}` : '未匹配生效方案' }}</el-descriptions-item>
          <el-descriptions-item label="计算过程">{{ detail.formulaSnapshot }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ dateTime(detail.occurredAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.reversedLedgerId" label="冲回原流水">#{{ detail.reversedLedgerId }}</el-descriptions-item>
          <el-descriptions-item label="幂等关联号">{{ detail.correlationId }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.negative { color: var(--el-color-danger); }
.line-name { color: var(--muted); font-size: 12px; margin-top: 2px; }
</style>
