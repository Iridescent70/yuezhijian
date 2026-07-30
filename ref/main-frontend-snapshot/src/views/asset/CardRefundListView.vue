<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  executeCardRefund,
  getCardRefundRequest,
  getCardRefundRequests,
  reviewCardRefund,
} from '@/api/card'
import { useAuthStore } from '@/stores/auth'
import type { CardRefundRequestDetail, CardRefundRequestSummary, CardRefundStatus } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const status = ref<CardRefundStatus | ''>('')
const rows = ref<CardRefundRequestSummary[]>([])
const detail = ref<CardRefundRequestDetail>()
const visible = ref(false)

const statusMap: Record<CardRefundStatus, { label: string; type: 'warning' | 'success' | 'danger' | 'info' }> = {
  SUBMITTED: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'info' },
  EXECUTED: { label: '已退卡', type: 'danger' },
}

async function load() {
  loading.value = true
  try {
    rows.value = await getCardRefundRequests(status.value || undefined)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退卡申请加载失败')
  } finally {
    loading.value = false
  }
}

async function open(row: unknown) {
  const item = row as CardRefundRequestSummary
  loading.value = true
  try {
    detail.value = await getCardRefundRequest(item.id)
    visible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退卡详情加载失败')
  } finally {
    loading.value = false
  }
}

async function review(approved: boolean) {
  if (!detail.value) return
  let comment: string | undefined
  if (approved) {
    await ElMessageBox.confirm('确认已核对办卡金额、消费原价重计、手续费和退款方式吗？', '审批退卡', { type: 'warning' })
  } else {
    const answer = await ElMessageBox.prompt('请输入驳回原因，驳回后次卡会恢复正常使用。', '驳回退卡', {
      inputType: 'textarea', inputValidator: (value) => Boolean(value.trim()) || '驳回原因不能为空',
    })
    comment = answer.value.trim()
  }
  saving.value = true
  try {
    detail.value = await reviewCardRefund(
      detail.value.request.id, approved, comment, detail.value.request.version,
    )
    ElMessage.success(approved ? '退卡审批已通过' : '申请已驳回，次卡已恢复')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退卡审批失败')
  } finally {
    saving.value = false
  }
}

async function execute() {
  if (!detail.value) return
  let externalReference: string | undefined
  if (detail.value.request.refundAmount > 0 && detail.value.request.refundMethodRequiresReference) {
    const answer = await ElMessageBox.prompt(
      `请输入${detail.value.request.refundMethodName ?? ''}的外部退款凭证号`,
      '执行退卡退款',
      { inputValidator: (value) => Boolean(value.trim()) || '外部退款凭证号不能为空' },
    )
    externalReference = answer.value.trim()
  } else {
    await ElMessageBox.confirm(
      `确认清零次卡并退款 ${formatMoney(detail.value.request.refundAmount)} 吗？`,
      '执行退卡',
      { type: 'error', confirmButtonText: '确认执行' },
    )
  }
  saving.value = true
  try {
    detail.value = await executeCardRefund(
      detail.value.request.id,
      detail.value.request.version,
      externalReference,
      crypto.randomUUID(),
    )
    ElMessage.success('退卡已执行，次卡已清零')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退卡执行失败')
  } finally {
    saving.value = false
  }
}

function dateTime(value?: string) { return value?.replace('T', ' ').slice(0, 19) ?? '—' }
function commissionStatus(value: string) {
  return ({
    PENDING_MODULE: { label: '待补规则', type: 'warning' },
    COMPLETED: { label: '已冲回', type: 'success' },
    NOT_APPLICABLE: { label: '无需冲回', type: 'info' },
  } as Record<string, { label: string; type: 'warning' | 'success' | 'info' }>)[value]
    ?? { label: value, type: 'info' }
}

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>退卡管理</h1><p>退卡按项目原价重计，执行申请、审批、退款三步；提成冲回状态单独显示。</p></div>
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
      <el-table v-loading="loading" :data="rows" stripe row-key="id" @row-click="open">
        <el-table-column label="退卡申请" min-width="200"><template #default="scope"><button class="member-link" type="button" @click.stop="open(scope.row)"><strong>{{ scope.row.requestNo }}</strong><small>{{ dateTime(scope.row.requestedAt) }}</small></button></template></el-table-column>
        <el-table-column label="会员" min-width="150"><template #default="scope"><el-button link type="primary" @click.stop="router.push(`/app/members/${scope.row.memberId}`)">{{ scope.row.memberName }}</el-button></template></el-table-column>
        <el-table-column prop="cardTypeName" label="次卡" min-width="180" />
        <el-table-column prop="storeName" label="经办门店" min-width="150" />
        <el-table-column label="退款金额" width="130" align="right"><template #default="scope"><strong>{{ formatMoney(scope.row.refundAmount) }}</strong></template></el-table-column>
        <el-table-column label="提成冲回" width="110"><template #default="scope"><el-tag :type="commissionStatus(scope.row.commissionAdjustmentStatus).type">{{ commissionStatus(scope.row.commissionAdjustmentStatus).label }}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="statusMap[scope.row.status as CardRefundStatus].type">{{ statusMap[scope.row.status as CardRefundStatus].label }}</el-tag></template></el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="visible" title="退卡详情" size="700px">
      <template v-if="detail">
        <div class="refund-head">
          <div><strong>{{ detail.request.requestNo }}</strong><span>{{ detail.request.cardTypeName }} · {{ detail.request.cardNo }}</span></div>
          <el-tag :type="statusMap[detail.request.status].type" size="large">{{ statusMap[detail.request.status].label }}</el-tag>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="会员">{{ detail.request.memberName }}</el-descriptions-item>
          <el-descriptions-item label="经办门店">{{ detail.request.storeName }}</el-descriptions-item>
          <el-descriptions-item label="办卡金额">{{ formatMoney(detail.request.originalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="消费原价重计">{{ formatMoney(detail.request.consumedRepriceAmount) }}</el-descriptions-item>
          <el-descriptions-item label="手续费">{{ formatMoney(detail.request.feeAmount) }}</el-descriptions-item>
          <el-descriptions-item label="退款金额"><strong>{{ formatMoney(detail.request.refundAmount) }}</strong></el-descriptions-item>
          <el-descriptions-item label="退款方式">{{ detail.request.refundMethodName || '无需退款' }}</el-descriptions-item>
          <el-descriptions-item label="提成冲回"><el-tag :type="commissionStatus(detail.request.commissionAdjustmentStatus).type">{{ commissionStatus(detail.request.commissionAdjustmentStatus).label }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="申请原因" :span="2">{{ detail.request.reason }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.request.reviewedAt" label="审批时间">{{ dateTime(detail.request.reviewedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.request.reviewedAt" label="审批意见">{{ detail.request.reviewComment || '同意' }}</el-descriptions-item>
        </el-descriptions>

        <h3>已消费项目原价重计</h3>
        <el-table :data="detail.consumedItems" empty-text="无已消费项目">
          <el-table-column prop="billNo" label="账单号" min-width="170" />
          <el-table-column prop="serviceName" label="项目" min-width="180" />
          <el-table-column label="消费时间" width="170"><template #default="scope">{{ dateTime(scope.row.consumedAt) }}</template></el-table-column>
          <el-table-column label="项目原价" width="120" align="right"><template #default="scope">{{ formatMoney(scope.row.originalAmount) }}</template></el-table-column>
        </el-table>

        <template v-if="detail.payment">
          <h3>退款记录</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="退款方式">{{ detail.payment.paymentMethodName }}</el-descriptions-item>
            <el-descriptions-item label="退款金额">{{ formatMoney(detail.payment.amount) }}</el-descriptions-item>
            <el-descriptions-item label="退款状态">{{ detail.payment.status }}</el-descriptions-item>
            <el-descriptions-item label="外部凭证">{{ detail.payment.externalRefundReference || '—' }}</el-descriptions-item>
          </el-descriptions>
        </template>

        <el-alert v-if="detail.request.commissionAdjustmentStatus === 'PENDING_MODULE'" title="已保留退卡事实，但没有可结算的售卡提成规则或历史提成流水；补齐规则后需重新计算。" type="warning" :closable="false" style="margin-top: 20px" />
        <div class="drawer-actions">
          <template v-if="detail.request.status === 'SUBMITTED' && auth.hasPermission('member:card:refund:approve')">
            <el-button :loading="saving" @click="review(false)">驳回并恢复次卡</el-button>
            <el-button type="primary" :loading="saving" @click="review(true)">审批通过</el-button>
          </template>
          <el-button v-if="detail.request.status === 'APPROVED' && auth.hasPermission('member:card:refund:manage')" type="danger" :loading="saving" @click="execute">执行退卡</el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.refund-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
.refund-head div { display: flex; flex-direction: column; gap: 6px; }
.refund-head strong { font-size: 22px; }
.refund-head span { color: var(--muted); }
h3 { margin: 26px 0 12px; }
</style>
