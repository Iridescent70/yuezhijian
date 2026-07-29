<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBills } from '@/api/trade'
import { useAuthStore } from '@/stores/auth'
import type { BillStatus, BillSummary } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const bills = ref<BillSummary[]>([])
const today = localDate(new Date())
const monthStart = `${today.slice(0, 8)}01`
const defaultStore = auth.user?.stores.find((item) => item.id === 2)?.id ?? auth.user?.currentStoreId
const filters = reactive({ storeId: defaultStore as number | undefined, dates: [monthStart, today], status: '', keyword: '' })
const statusMap: Record<BillStatus, { label: string; type: 'success' | 'warning' | 'info' | 'danger' | 'primary' }> = {
  DRAFT: { label: '草稿', type: 'info' }, PENDING_PAYMENT: { label: '待结算', type: 'warning' },
  SETTLED: { label: '已结算', type: 'success' }, VOIDED: { label: '已作废', type: 'info' },
  ADJUSTED: { label: '已调账', type: 'primary' }, REVERSED: { label: '已冲销', type: 'danger' },
}

async function load() {
  if (!filters.storeId) return
  loading.value = true
  try {
    bills.value = await getBills({ storeId: filters.storeId, startDate: filters.dates[0], endDate: filters.dates[1], status: filters.status || undefined, keyword: filters.keyword || undefined })
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '账单加载失败') }
  finally { loading.value = false }
}

function localDate(value: Date) { const offset = value.getTimezoneOffset() * 60_000; return new Date(value.getTime() - offset).toISOString().slice(0, 10) }
function dateTime(value: string) { return value.replace('T', ' ').slice(0, 16) }
onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row"><div><h1>账单管理</h1><p>查询开单、待结算与已结算记录，已结算账单不得直接修改。</p></div><el-button v-if="auth.hasPermission('trade:bill:create')" type="primary" @click="router.push('/app/bills/new')">新建账单</el-button></div>
    <el-card class="filter-card" shadow="never"><el-form inline><el-form-item label="账单查询"><el-input v-model="filters.keyword" clearable placeholder="账单号或客户" /></el-form-item><el-form-item label="门店"><el-select v-model="filters.storeId" class="master-filter-select"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item><el-form-item label="日期"><el-date-picker v-model="filters.dates" type="daterange" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item><el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部" class="master-filter-select"><el-option v-for="(item, code) in statusMap" :key="code" :label="item.label" :value="code" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item></el-form></el-card>
    <el-card class="data-card" shadow="never"><el-table v-loading="loading" :data="bills" stripe row-key="id"><el-table-column label="账单" width="200"><template #default="scope"><button class="member-link" type="button" @click="router.push(`/app/bills/${scope.row.id}`)"><strong>{{ scope.row.billNo }}</strong><small>{{ dateTime(scope.row.createdAt) }}</small></button></template></el-table-column><el-table-column label="客户" min-width="160"><template #default="scope"><strong>{{ scope.row.customerName }}</strong><br><small class="muted-text">{{ scope.row.maskedMobile }}</small></template></el-table-column><el-table-column prop="storeName" label="门店" min-width="160" /><el-table-column prop="sourceType" label="来源" width="120" /><el-table-column label="应收" width="130" align="right"><template #default="scope">{{ formatMoney(scope.row.receivableAmount) }}</template></el-table-column><el-table-column label="实收" width="130" align="right"><template #default="scope">{{ formatMoney(scope.row.receivedAmount) }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="statusMap[scope.row.status as BillStatus].type">{{ statusMap[scope.row.status as BillStatus].label }}</el-tag></template></el-table-column><el-table-column label="操作" width="110" fixed="right"><template #default="scope"><el-button v-if="scope.row.status === 'PENDING_PAYMENT'" link type="primary" @click="router.push(`/app/bills/${scope.row.id}/settle`)">去结算</el-button><el-button v-else link @click="router.push(`/app/bills/${scope.row.id}`)">查看</el-button></template></el-table-column></el-table></el-card>
  </section>
</template>
