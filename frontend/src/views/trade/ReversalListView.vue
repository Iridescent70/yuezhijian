<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { executeReversal, getReversal, getReversals, reviewReversal } from '@/api/trade'
import { useAuthStore } from '@/stores/auth'
import type { ReversalDetail, ReversalStatus, ReversalSummary } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const status = ref<ReversalStatus | ''>('')
const rows = ref<ReversalSummary[]>([])
const visible = ref(false)
const detail = ref<ReversalDetail | null>(null)
const statusMap: Record<ReversalStatus, { label: string; type: 'warning' | 'success' | 'danger' | 'info' }> = {
  SUBMITTED: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'info' },
  EXECUTED: { label: '已执行', type: 'danger' },
}

async function load() {
  loading.value = true
  try {
    rows.value = await getReversals(status.value || undefined)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '冲销记录加载失败')
  } finally {
    loading.value = false
  }
}

async function open(row: ReversalSummary) {
  loading.value = true
  try {
    detail.value = await getReversal(row.id)
    visible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '冲销详情加载失败')
  } finally {
    loading.value = false
  }
}

function openRow(row: unknown) { void open(row as ReversalSummary) }

async function review(approved: boolean) {
  if (!detail.value) return
  let comment: string | undefined
  if (approved) {
    await ElMessageBox.confirm('确认该冲销申请的金额及退回内容无误吗？', '审批通过', { type: 'warning' })
  } else {
    const answer = await ElMessageBox.prompt('请输入驳回原因', '驳回冲销', {
      inputType: 'textarea', inputValidator: (value) => Boolean(value.trim()) || '驳回原因不能为空',
    })
    comment = answer.value.trim()
  }
  saving.value = true
  try {
    detail.value = await reviewReversal(
      detail.value.reversal.id, approved, comment, detail.value.reversal.version,
    )
    ElMessage.success(approved ? '审批已通过' : '申请已驳回')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审批失败')
  } finally {
    saving.value = false
  }
}

async function execute() {
  if (!detail.value) return
  await ElMessageBox.confirm(
    '执行后将账单置为已冲销并退回支付及全部会员资产，确认继续吗？',
    '执行整单冲销',
    { type: 'error', confirmButtonText: '确认执行' },
  )
  saving.value = true
  try {
    detail.value = await executeReversal(
      detail.value.reversal.id, detail.value.reversal.version, crypto.randomUUID(),
    )
    ElMessage.success('整单冲销已执行')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '冲销执行失败')
  } finally {
    saving.value = false
  }
}

function dateTime(value?: string) { return value?.replace('T', ' ').slice(0, 19) ?? '—' }
function assetName(value: string) { return ({ BALANCE: '储值', POINT: '积分', CARD: '次卡' } as Record<string, string>)[value] ?? value }

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>冲销管理</h1><p>整单冲销实行申请、审批、执行三步，执行后自动生成退款和会员资产反向流水。</p></div>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline>
        <el-form-item label="处理状态">
          <el-select v-model="status" clearable placeholder="全部状态" class="master-filter-select">
            <el-option v-for="(item, code) in statusMap" :key="code" :label="item.label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id" @row-click="openRow">
        <el-table-column label="冲销单" min-width="190"><template #default="scope"><button class="member-link" type="button" @click.stop="openRow(scope.row)"><strong>{{ scope.row.reversalNo }}</strong><small>{{ dateTime(scope.row.requestedAt) }}</small></button></template></el-table-column>
        <el-table-column label="原账单" min-width="190"><template #default="scope"><el-button link type="primary" @click.stop="router.push(`/app/bills/${scope.row.billId}`)">{{ scope.row.billNo }}</el-button></template></el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="140" />
        <el-table-column prop="storeName" label="门店" min-width="150" />
        <el-table-column label="冲销金额" width="130" align="right"><template #default="scope"><strong>{{ formatMoney(scope.row.refundAmount) }}</strong></template></el-table-column>
        <el-table-column prop="reason" label="申请原因" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="statusMap[scope.row.status as ReversalStatus].type">{{ statusMap[scope.row.status as ReversalStatus].label }}</el-tag></template></el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="visible" title="冲销详情" size="650px">
      <template v-if="detail">
        <div class="reversal-head">
          <div><strong>{{ detail.reversal.reversalNo }}</strong><span>原账单 {{ detail.reversal.billNo }}</span></div>
          <el-tag :type="statusMap[detail.reversal.status].type" size="large">{{ statusMap[detail.reversal.status].label }}</el-tag>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="客户">{{ detail.reversal.customerName }}</el-descriptions-item>
          <el-descriptions-item label="门店">{{ detail.reversal.storeName }}</el-descriptions-item>
          <el-descriptions-item label="冲销金额">{{ formatMoney(detail.reversal.refundAmount) }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ dateTime(detail.reversal.requestedAt) }}</el-descriptions-item>
          <el-descriptions-item label="申请原因" :span="2">{{ detail.reversal.reason }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.reversal.reviewedAt" label="审批时间">{{ dateTime(detail.reversal.reviewedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.reversal.reviewedAt" label="审批意见">{{ detail.reversal.reviewComment || '同意' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.reversal.executedAt" label="执行时间" :span="2">{{ dateTime(detail.reversal.executedAt) }}</el-descriptions-item>
        </el-descriptions>

        <h3>外部支付退回</h3>
        <el-table :data="detail.payments" empty-text="无外部支付">
          <el-table-column prop="paymentMethodName" label="支付方式" />
          <el-table-column label="金额" align="right"><template #default="scope">{{ formatMoney(scope.row.amount) }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="110" />
        </el-table>

        <h3>会员资产退回</h3>
        <el-table :data="detail.assets" empty-text="无会员资产">
          <el-table-column label="类型" width="80"><template #default="scope">{{ assetName(scope.row.assetType) }}</template></el-table-column>
          <el-table-column prop="displayName" label="内容" min-width="180" />
          <el-table-column prop="quantity" label="返还数量" width="100" align="right" />
          <el-table-column label="折算金额" width="120" align="right"><template #default="scope">{{ formatMoney(scope.row.amount) }}</template></el-table-column>
        </el-table>

        <div class="drawer-actions">
          <template v-if="detail.reversal.status === 'SUBMITTED' && auth.hasPermission('trade:reversal:approve')">
            <el-button :loading="saving" @click="review(false)">驳回</el-button>
            <el-button type="primary" :loading="saving" @click="review(true)">审批通过</el-button>
          </template>
          <el-button v-if="detail.reversal.status === 'APPROVED' && auth.hasPermission('trade:reversal:manage')" type="danger" :loading="saving" @click="execute">执行冲销</el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.reversal-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
.reversal-head div { display: flex; flex-direction: column; gap: 6px; }
.reversal-head strong { font-size: 22px; }
.reversal-head span { color: var(--muted); }
h3 { margin: 26px 0 12px; }
</style>
