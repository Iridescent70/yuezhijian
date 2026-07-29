<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adjustPoints,
  confirmRecharge,
  createRecharge,
  getBalanceAccount,
  getBalanceLedgers,
  getPointAccount,
  getPointLedgers,
  quoteRecharge,
} from '@/api/asset'
import { getPaymentMethods } from '@/api/trade'
import {
  executeCardExchange,
  getCardTypes,
  getMemberCards,
  purchaseMemberCard,
  quoteCardExchange,
  quoteCardRefund,
  submitCardRefund,
  transferMemberCard,
} from '@/api/card'
import { searchMembers } from '@/api/member'
import { getEmployees } from '@/api/masterData'
import { useAuthStore } from '@/stores/auth'
import type {
  BalanceAccount,
  BalanceLedgerItem,
  CardExchangeQuote,
  CardRefundQuote,
  CardTypeDetail,
  EmployeeSummary,
  MemberCardSummary,
  MemberSummary,
  PaymentMethodOption,
  PointAccount,
  PointLedgerItem,
} from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const props = defineProps<{ memberId: number; storeId: number }>()
const emit = defineEmits<{
  changed: [payload: { balance: BalanceAccount; points: PointAccount; cardCount: number }]
}>()
const auth = useAuthStore()
const loading = ref(true)
const submitting = ref(false)
const balance = ref<BalanceAccount>()
const points = ref<PointAccount>()
const balanceLedgers = ref<BalanceLedgerItem[]>([])
const pointLedgers = ref<PointLedgerItem[]>([])
const paymentMethods = ref<PaymentMethodOption[]>([])
const memberCards = ref<MemberCardSummary[]>([])
const cardTypes = ref<CardTypeDetail[]>([])
const salesEmployees = ref<EmployeeSummary[]>([])
const rechargeVisible = ref(false)
const pointVisible = ref(false)
const cardVisible = ref(false)
const exchangeVisible = ref(false)
const exchangeQuoteLoading = ref(false)
const exchangeQuote = ref<CardExchangeQuote>()
const exchangeSourceCard = ref<MemberCardSummary>()
const transferVisible = ref(false)
const transferSourceCard = ref<MemberCardSummary>()
const recipientLoading = ref(false)
const recipientMembers = ref<MemberSummary[]>([])
const refundVisible = ref(false)
const refundQuoteLoading = ref(false)
const refundSourceCard = ref<MemberCardSummary>()
const refundQuote = ref<CardRefundQuote>()
const rechargeForm = reactive({ rechargeAmount: 0, giftAmount: 0, paymentMethodId: 0, externalReference: '' })
const pointForm = reactive({ changePoints: 0, reason: '' })
const cardForm = reactive({ cardTypeId: 0, quantity: 1, salesEmployeeId: undefined as number | undefined, paymentMethodId: 0, externalReference: '' })
const exchangeForm = reactive({ targetCardTypeId: 0, employeeId: undefined as number | undefined, paymentMethodId: 0, externalReference: '' })
const transferForm = reactive({ recipientMemberId: undefined as number | undefined, expiresAt: '', employeeId: undefined as number | undefined, reason: '' })
const refundForm = reactive({ feeAmount: 0, refundMethodId: 0, employeeId: undefined as number | undefined, reason: '' })
const selectedMethod = computed(() => paymentMethods.value.find((item) => item.id === rechargeForm.paymentMethodId))
const selectedCardMethod = computed(() => paymentMethods.value.find((item) => item.id === cardForm.paymentMethodId))
const selectedCardType = computed(() => cardTypes.value.find((item) => item.id === cardForm.cardTypeId))
const selectedExchangeMethod = computed(() => paymentMethods.value.find((item) => item.id === exchangeForm.paymentMethodId))
const exchangeCardTypes = computed(() => cardTypes.value.filter((item) => item.id !== exchangeSourceCard.value?.cardTypeId))
const selectedRecipient = computed(() => recipientMembers.value.find((item) => item.id === transferForm.recipientMemberId))
const selectedRefundMethod = computed(() => paymentMethods.value.find((item) => item.id === refundForm.refundMethodId))
const canManage = computed(() => auth.hasPermission('member:asset:manage'))

const balanceTypeLabels: Record<string, string> = {
  RECHARGE: '充值本金', RECHARGE_GIFT: '充值赠送', CONSUME: '消费', REFUND: '退款',
  ADJUST_IN: '调增', ADJUST_OUT: '调减', REVERSAL: '冲销', MIGRATION: '数据迁移',
}
const pointTypeLabels: Record<string, string> = {
  EARN: '获得', REDEEM: '兑换', EXPIRE: '过期', REFUND: '退回',
  ADJUST_IN: '调增', ADJUST_OUT: '调减', REVERSAL: '冲销', MIGRATION: '数据迁移',
}
const cardStatusLabels: Record<string, string> = {
  ACTIVE: '有效', EXHAUSTED: '已用完', EXPIRED: '已过期', FROZEN: '已冻结',
  TRANSFERRED: '已转赠', EXCHANGED: '已换卡', REFUNDED: '已退卡',
}

async function load() {
  loading.value = true
  try {
    const [nextBalance, nextPoints, nextBalanceLedgers, nextPointLedgers, nextCards] = await Promise.all([
      getBalanceAccount(props.memberId),
      getPointAccount(props.memberId),
      getBalanceLedgers(props.memberId),
      getPointLedgers(props.memberId),
      getMemberCards(props.memberId),
    ])
    balance.value = nextBalance
    points.value = nextPoints
    balanceLedgers.value = nextBalanceLedgers
    pointLedgers.value = nextPointLedgers
    memberCards.value = nextCards
    emit('changed', { balance: nextBalance, points: nextPoints, cardCount: nextCards.filter((card) => card.status === 'ACTIVE').length })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会员资产加载失败')
  } finally {
    loading.value = false
  }
}

async function openRecharge() {
  try {
    if (!paymentMethods.value.length) paymentMethods.value = await getPaymentMethods(props.storeId)
    const method = paymentMethods.value.find((item) => item.type !== 'STORED_VALUE')
    Object.assign(rechargeForm, {
      rechargeAmount: 0, giftAmount: 0, paymentMethodId: method?.id ?? 0, externalReference: '',
    })
    rechargeVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '支付方式加载失败')
  }
}

async function submitRecharge() {
  if (rechargeForm.rechargeAmount <= 0 || !rechargeForm.paymentMethodId) {
    ElMessage.warning('请填写充值金额和支付方式')
    return
  }
  if (selectedMethod.value?.needsExternalReference && !rechargeForm.externalReference.trim()) {
    ElMessage.warning('当前支付方式必须填写外部凭证号')
    return
  }
  const creditAmount = rechargeForm.rechargeAmount + rechargeForm.giftAmount
  await ElMessageBox.confirm(
    `确认收款 ${formatMoney(rechargeForm.rechargeAmount)}，实际入账 ${formatMoney(creditAmount)} 吗？`,
    '确认充值入账',
    { type: 'warning' },
  )
  submitting.value = true
  try {
    const quote = await quoteRecharge(props.memberId, {
      rechargeAmount: rechargeForm.rechargeAmount,
      giftAmount: rechargeForm.giftAmount,
      paymentMethodId: rechargeForm.paymentMethodId,
    })
    const order = await createRecharge(props.memberId, {
      quoteNo: quote.quoteNo,
      storeId: props.storeId,
      externalReference: rechargeForm.externalReference.trim() || undefined,
      idempotencyKey: crypto.randomUUID(),
    })
    await confirmRecharge(order.id, order.version)
    rechargeVisible.value = false
    ElMessage.success('充值已确认入账')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '充值失败')
  } finally {
    submitting.value = false
  }
}

function openPointAdjustment() {
  Object.assign(pointForm, { changePoints: 0, reason: '' })
  pointVisible.value = true
}

async function submitPointAdjustment() {
  if (pointForm.changePoints === 0 || !pointForm.reason.trim()) {
    ElMessage.warning('请填写积分变动值和原因')
    return
  }
  submitting.value = true
  try {
    await adjustPoints(props.memberId, {
      changePoints: pointForm.changePoints,
      reason: pointForm.reason.trim(),
      idempotencyKey: crypto.randomUUID(),
    })
    pointVisible.value = false
    ElMessage.success('积分调整完成')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '积分调整失败')
  } finally {
    submitting.value = false
  }
}

async function openCardPurchase() {
  try {
    const [nextTypes, nextMethods, nextEmployees] = await Promise.all([
      getCardTypes({ storeId: props.storeId, status: 'ACTIVE' }),
      paymentMethods.value.length ? Promise.resolve(paymentMethods.value) : getPaymentMethods(props.storeId),
      getEmployees({ storeId: props.storeId }),
    ])
    cardTypes.value = nextTypes
    paymentMethods.value = nextMethods
    salesEmployees.value = nextEmployees.filter((item) => item.canSell && item.status === 'ACTIVE')
    const method = nextMethods.find((item) => item.type !== 'STORED_VALUE')
    Object.assign(cardForm, {
      cardTypeId: nextTypes[0]?.id ?? 0, quantity: 1,
      salesEmployeeId: salesEmployees.value[0]?.id, paymentMethodId: method?.id ?? 0, externalReference: '',
    })
    if (!nextTypes.length) { ElMessage.warning('当前门店没有在售次卡类型'); return }
    cardVisible.value = true
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '售卡资料加载失败') }
}

async function submitCardPurchase() {
  if (!cardForm.cardTypeId || !cardForm.paymentMethodId) { ElMessage.warning('请选择次卡类型和支付方式'); return }
  if (selectedCardMethod.value?.needsExternalReference && !cardForm.externalReference.trim()) {
    ElMessage.warning('当前支付方式必须填写外部凭证号'); return
  }
  const total = Number(selectedCardType.value?.salePrice ?? 0) * cardForm.quantity
  await ElMessageBox.confirm(`确认收款 ${formatMoney(total)} 并发放 ${cardForm.quantity} 张次卡吗？`, '确认售卡', { type: 'warning' })
  submitting.value = true
  try {
    await purchaseMemberCard(props.memberId, {
      cardTypeId: cardForm.cardTypeId, quantity: cardForm.quantity, storeId: props.storeId,
      salesEmployeeId: cardForm.salesEmployeeId, paymentMethodId: cardForm.paymentMethodId,
      externalReference: cardForm.externalReference.trim() || undefined, idempotencyKey: crypto.randomUUID(),
    })
    cardVisible.value = false
    ElMessage.success('次卡已发放')
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '售卡失败') }
  finally { submitting.value = false }
}

async function refreshExchangeQuote() {
  const card = exchangeSourceCard.value
  const targetCardTypeId = exchangeForm.targetCardTypeId
  exchangeQuote.value = undefined
  if (!card || !targetCardTypeId) return
  exchangeQuoteLoading.value = true
  try {
    const quote = await quoteCardExchange(card.id, targetCardTypeId)
    if (exchangeForm.targetCardTypeId === targetCardTypeId) exchangeQuote.value = quote
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '换卡试算失败')
  } finally {
    exchangeQuoteLoading.value = false
  }
}

async function openCardExchange(row: unknown) {
  const card = row as MemberCardSummary
  try {
    const [nextTypes, nextMethods, nextEmployees] = await Promise.all([
      getCardTypes({ storeId: props.storeId, status: 'ACTIVE' }),
      paymentMethods.value.length ? Promise.resolve(paymentMethods.value) : getPaymentMethods(props.storeId),
      salesEmployees.value.length ? Promise.resolve(salesEmployees.value) : getEmployees({ storeId: props.storeId }),
    ])
    exchangeSourceCard.value = card
    cardTypes.value = nextTypes
    paymentMethods.value = nextMethods
    salesEmployees.value = nextEmployees.filter((item) => item.canSell && item.status === 'ACTIVE')
    const candidates = nextTypes.filter((item) => item.id !== card.cardTypeId)
    if (!candidates.length) { ElMessage.warning('当前门店没有其他可换购的次卡类型'); return }
    const preferred = candidates.find((item) => Number(item.salePrice) >= Number(card.purchasePrice)) ?? candidates[0]
    const method = nextMethods.find((item) => item.type !== 'STORED_VALUE')
    Object.assign(exchangeForm, {
      targetCardTypeId: preferred?.id ?? 0,
      employeeId: salesEmployees.value[0]?.id,
      paymentMethodId: method?.id ?? 0,
      externalReference: '',
    })
    exchangeQuote.value = undefined
    exchangeVisible.value = true
    await refreshExchangeQuote()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '换卡资料加载失败')
  }
}

async function submitCardExchange() {
  const card = exchangeSourceCard.value
  const quote = exchangeQuote.value
  if (!card || !quote) { ElMessage.warning('请先完成换卡试算'); return }
  if (quote.differenceAmount > 0 && !exchangeForm.paymentMethodId) {
    ElMessage.warning('请选择补差支付方式'); return
  }
  if (quote.differenceAmount > 0 && selectedExchangeMethod.value?.needsExternalReference && !exchangeForm.externalReference.trim()) {
    ElMessage.warning('当前支付方式必须填写外部凭证号'); return
  }
  await ElMessageBox.confirm(
    `确认将 ${card.cardTypeName} 换为 ${quote.targetCardTypeName}，收取补差 ${formatMoney(quote.differenceAmount)} 吗？`,
    '确认换卡',
    { type: 'warning' },
  )
  submitting.value = true
  try {
    const payments = quote.differenceAmount > 0 ? [{
      paymentMethodId: exchangeForm.paymentMethodId,
      amount: quote.differenceAmount,
      externalReference: exchangeForm.externalReference.trim() || undefined,
    }] : []
    const result = await executeCardExchange(card.id, {
      quoteNo: quote.quoteNo,
      storeId: props.storeId,
      employeeId: exchangeForm.employeeId,
      payments,
      idempotencyKey: crypto.randomUUID(),
    })
    exchangeVisible.value = false
    ElMessage.success(`换卡完成，新卡号：${result.newCard.cardNo}`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '换卡失败')
  } finally {
    submitting.value = false
  }
}

async function searchTransferRecipients(keyword: string) {
  const requestedKeyword = keyword.trim()
  recipientLoading.value = true
  try {
    const result = await searchMembers({
      keyword: requestedKeyword || undefined,
      status: 'ACTIVE',
      page: 1,
      size: 30,
    })
    recipientMembers.value = result.items.filter((item) => item.id !== props.memberId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '接收会员查询失败')
  } finally {
    recipientLoading.value = false
  }
}

async function openCardTransfer(row: unknown) {
  const card = row as MemberCardSummary
  try {
    if (!salesEmployees.value.length) {
      salesEmployees.value = (await getEmployees({ storeId: props.storeId }))
        .filter((item) => item.canSell && item.status === 'ACTIVE')
    }
    transferSourceCard.value = card
    Object.assign(transferForm, {
      recipientMemberId: undefined,
      expiresAt: card.expiresAt.slice(0, 19),
      employeeId: salesEmployees.value[0]?.id,
      reason: '',
    })
    recipientMembers.value = []
    transferVisible.value = true
    await searchTransferRecipients('')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '转赠资料加载失败')
  }
}

async function submitCardTransfer() {
  const card = transferSourceCard.value
  if (!card || !transferForm.recipientMemberId || !transferForm.expiresAt || !transferForm.reason.trim()) {
    ElMessage.warning('请选择接收会员、有效期并填写转赠原因'); return
  }
  await ElMessageBox.confirm(
    `确认将 ${card.cardTypeName} 的剩余 ${card.remainingTimes} 次转赠给 ${selectedRecipient.value?.fullName ?? '所选会员'} 吗？原卡将立即失效。`,
    '确认次卡转赠',
    { type: 'warning' },
  )
  submitting.value = true
  try {
    const result = await transferMemberCard(card.id, {
      recipientMemberId: transferForm.recipientMemberId,
      expiresAt: transferForm.expiresAt,
      storeId: props.storeId,
      employeeId: transferForm.employeeId,
      reason: transferForm.reason.trim(),
      sourceCardVersion: card.version,
      idempotencyKey: crypto.randomUUID(),
    })
    transferVisible.value = false
    ElMessage.success(`转赠完成，接收会员新卡号：${result.targetCard.cardNo}`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '次卡转赠失败')
  } finally {
    submitting.value = false
  }
}

async function refreshCardRefundQuote() {
  const card = refundSourceCard.value
  if (!card || refundForm.feeAmount < 0) return
  refundQuoteLoading.value = true
  refundQuote.value = undefined
  try {
    refundQuote.value = await quoteCardRefund(card.id, refundForm.feeAmount)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退卡试算失败')
  } finally {
    refundQuoteLoading.value = false
  }
}

async function openCardRefund(row: unknown) {
  const card = row as MemberCardSummary
  try {
    const [nextMethods, nextEmployees] = await Promise.all([
      paymentMethods.value.length ? Promise.resolve(paymentMethods.value) : getPaymentMethods(props.storeId),
      salesEmployees.value.length ? Promise.resolve(salesEmployees.value) : getEmployees({ storeId: props.storeId }),
    ])
    paymentMethods.value = nextMethods
    salesEmployees.value = nextEmployees.filter((item) => item.canSell && item.status === 'ACTIVE')
    const method = nextMethods.find((item) => item.type !== 'STORED_VALUE')
    refundSourceCard.value = card
    Object.assign(refundForm, {
      feeAmount: 0,
      refundMethodId: method?.id ?? 0,
      employeeId: salesEmployees.value[0]?.id,
      reason: '',
    })
    refundQuote.value = undefined
    refundVisible.value = true
    await refreshCardRefundQuote()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退卡资料加载失败')
  }
}

async function submitCardRefundRequest() {
  const card = refundSourceCard.value
  const quote = refundQuote.value
  if (!card || !quote || !refundForm.reason.trim()) { ElMessage.warning('请完成试算并填写退卡原因'); return }
  if (quote.refundAmount > 0 && !refundForm.refundMethodId) { ElMessage.warning('请选择退款方式'); return }
  await ElMessageBox.confirm(
    `确认提交退卡申请吗？预计退款 ${formatMoney(quote.refundAmount)}，提交后次卡将冻结等待审批。`,
    '提交退卡申请',
    { type: 'warning' },
  )
  submitting.value = true
  try {
    const result = await submitCardRefund(card.id, {
      quoteNo: quote.quoteNo,
      refundMethodId: quote.refundAmount > 0 ? refundForm.refundMethodId : undefined,
      storeId: props.storeId,
      employeeId: refundForm.employeeId,
      reason: refundForm.reason.trim(),
      idempotencyKey: crypto.randomUUID(),
    })
    refundVisible.value = false
    ElMessage.success(`退卡申请 ${result.request.requestNo} 已提交，次卡已冻结`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退卡申请失败')
  } finally {
    submitting.value = false
  }
}

function formatTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '—' }

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="member-assets-panel">
    <div class="asset-actions">
      <div>
        <strong>会员资产</strong>
        <p>余额和积分以流水为准，每次变动均可追溯。</p>
      </div>
      <div v-if="canManage">
        <el-button @click="openPointAdjustment">调整积分</el-button>
        <el-button v-if="auth.hasPermission('member:card:manage')" @click="openCardPurchase">办理次卡</el-button>
        <el-button type="primary" @click="openRecharge">会员充值</el-button>
      </div>
    </div>

    <div class="asset-account-grid">
      <el-card shadow="never">
        <span>可用储值</span>
        <strong>{{ formatMoney(balance?.availableBalance ?? 0) }}</strong>
        <small>冻结 {{ formatMoney(balance?.frozenBalance ?? 0) }} · 累计充值 {{ formatMoney(balance?.totalRecharged ?? 0) }}</small>
      </el-card>
      <el-card shadow="never">
        <span>有效次卡</span>
        <strong>{{ memberCards.filter((card) => card.status === 'ACTIVE').length }}</strong>
        <small>剩余总次数 {{ memberCards.filter((card) => card.status === 'ACTIVE').reduce((sum, card) => sum + Number(card.remainingTimes), 0) }}</small>
      </el-card>
      <el-card shadow="never">
        <span>可用积分</span>
        <strong>{{ points?.availablePoints ?? 0 }}</strong>
        <small>累计获得 {{ points?.lifetimePoints ?? 0 }} · 最近变动 {{ formatTime(points?.lastTransactionAt) }}</small>
      </el-card>
    </div>

    <el-tabs class="asset-ledger-tabs">
      <el-tab-pane label="储值流水">
        <el-table :data="balanceLedgers" empty-text="暂无储值流水">
          <el-table-column prop="ledgerNo" label="流水号" width="210" />
          <el-table-column label="类型" width="110">
            <template #default="scope">{{ balanceTypeLabels[scope.row.transactionType] ?? scope.row.transactionType }}</template>
          </el-table-column>
          <el-table-column label="变动" width="120" align="right">
            <template #default="scope"><span :class="scope.row.changeAmount >= 0 ? 'asset-in' : 'asset-out'">{{ scope.row.changeAmount >= 0 ? '+' : '' }}{{ formatMoney(scope.row.changeAmount) }}</span></template>
          </el-table-column>
          <el-table-column label="变动后" width="120" align="right">
            <template #default="scope">{{ formatMoney(scope.row.afterBalance) }}</template>
          </el-table-column>
          <el-table-column prop="storeName" label="门店" min-width="130" />
          <el-table-column prop="note" label="说明" min-width="140" show-overflow-tooltip />
          <el-table-column label="时间" width="170"><template #default="scope">{{ formatTime(scope.row.occurredAt) }}</template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="积分流水">
        <el-table :data="pointLedgers" empty-text="暂无积分流水">
          <el-table-column prop="ledgerNo" label="流水号" width="210" />
          <el-table-column label="类型" width="110"><template #default="scope">{{ pointTypeLabels[scope.row.transactionType] ?? scope.row.transactionType }}</template></el-table-column>
          <el-table-column label="变动" width="100" align="right"><template #default="scope"><span :class="scope.row.changePoints >= 0 ? 'asset-in' : 'asset-out'">{{ scope.row.changePoints >= 0 ? '+' : '' }}{{ scope.row.changePoints }}</span></template></el-table-column>
          <el-table-column prop="afterPoints" label="变动后" width="100" align="right" />
          <el-table-column prop="note" label="原因" min-width="180" show-overflow-tooltip />
          <el-table-column label="时间" width="170"><template #default="scope">{{ formatTime(scope.row.occurredAt) }}</template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="次卡资产">
        <el-table :data="memberCards" empty-text="暂无会员次卡">
          <el-table-column prop="cardNo" label="卡号" width="210" />
          <el-table-column prop="cardTypeName" label="次卡类型" min-width="180" />
          <el-table-column prop="remainingTimes" label="剩余次数" width="100" align="right" />
          <el-table-column label="购卡金额" width="120" align="right"><template #default="scope">{{ formatMoney(scope.row.purchasePrice) }}</template></el-table-column>
          <el-table-column prop="purchaseStoreName" label="购卡门店" min-width="140" />
          <el-table-column label="到期日期" width="120"><template #default="scope">{{ scope.row.expiresAt.slice(0, 10) }}</template></el-table-column>
          <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ cardStatusLabels[scope.row.status] ?? scope.row.status }}</el-tag></template></el-table-column>
          <el-table-column v-if="auth.hasPermission('member:card:manage') || auth.hasPermission('member:card:refund:manage')" label="操作" width="180" fixed="right">
            <template #default="scope"><template v-if="scope.row.status === 'ACTIVE'"><el-button v-if="auth.hasPermission('member:card:manage')" link type="primary" @click="openCardExchange(scope.row)">换卡</el-button><el-button v-if="auth.hasPermission('member:card:manage')" link type="primary" @click="openCardTransfer(scope.row)">转赠</el-button><el-button v-if="auth.hasPermission('member:card:refund:manage')" link type="danger" @click="openCardRefund(scope.row)">退卡</el-button></template></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="rechargeVisible" title="会员充值" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="充值金额" required><el-input-number v-model="rechargeForm.rechargeAmount" :min="0" :max="1000000" :precision="2" /></el-form-item>
        <el-form-item label="赠送金额"><el-input-number v-model="rechargeForm.giftAmount" :min="0" :max="1000000" :precision="2" /></el-form-item>
        <el-form-item label="实际入账"><strong>{{ formatMoney(rechargeForm.rechargeAmount + rechargeForm.giftAmount) }}</strong></el-form-item>
        <el-form-item label="支付方式" required><el-select v-model="rechargeForm.paymentMethodId" style="width: 100%"><el-option v-for="item in paymentMethods.filter((method) => method.type !== 'STORED_VALUE')" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item v-if="selectedMethod?.needsExternalReference" label="外部凭证" required><el-input v-model="rechargeForm.externalReference" maxlength="128" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="rechargeVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitRecharge">确认收款并入账</el-button></template>
    </el-dialog>

    <el-dialog v-model="pointVisible" title="调整会员积分" width="500px" destroy-on-close>
      <el-alert title="正数增加、负数扣减；调整后会生成不可修改的积分流水。" type="warning" :closable="false" />
      <el-form label-width="100px" style="margin-top: 20px">
        <el-form-item label="积分变动" required><el-input-number v-model="pointForm.changePoints" :min="-1000000" :max="1000000" /></el-form-item>
        <el-form-item label="调整原因" required><el-input v-model="pointForm.reason" type="textarea" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="pointVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitPointAdjustment">确认调整</el-button></template>
    </el-dialog>

    <el-dialog v-model="cardVisible" title="办理次卡" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="次卡类型" required><el-select v-model="cardForm.cardTypeId" style="width: 100%"><el-option v-for="item in cardTypes" :key="item.id" :label="`${item.name}（${formatMoney(item.salePrice)}）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="购买数量"><el-input-number v-model="cardForm.quantity" :min="1" :max="20" /></el-form-item>
        <el-form-item label="单卡次数"><span>{{ selectedCardType?.totalTimes ?? 0 }} 次</span></el-form-item>
        <el-form-item label="收款合计"><strong>{{ formatMoney(Number(selectedCardType?.salePrice ?? 0) * cardForm.quantity) }}</strong></el-form-item>
        <el-form-item label="销售员工"><el-select v-model="cardForm.salesEmployeeId" clearable style="width: 100%"><el-option v-for="item in salesEmployees" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="支付方式" required><el-select v-model="cardForm.paymentMethodId" style="width: 100%"><el-option v-for="item in paymentMethods.filter((method) => method.type !== 'STORED_VALUE')" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item v-if="selectedCardMethod?.needsExternalReference" label="外部凭证" required><el-input v-model="cardForm.externalReference" maxlength="128" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="cardVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitCardPurchase">确认收款并发卡</el-button></template>
    </el-dialog>

    <el-dialog v-model="exchangeVisible" title="次卡换购" width="620px" destroy-on-close>
      <el-alert title="原卡按办卡金额和剩余次数折算；换卡完成后原卡立即关闭，不能撤回。" type="warning" :closable="false" />
      <el-form v-loading="exchangeQuoteLoading" label-width="110px" style="margin-top: 20px">
        <el-form-item label="原次卡"><span>{{ exchangeSourceCard?.cardTypeName }} · 剩余 {{ exchangeSourceCard?.remainingTimes }} 次</span></el-form-item>
        <el-form-item label="目标次卡" required>
          <el-select v-model="exchangeForm.targetCardTypeId" style="width: 100%" @change="refreshExchangeQuote">
            <el-option v-for="item in exchangeCardTypes" :key="item.id" :label="`${item.name}（${formatMoney(item.salePrice)}）`" :value="item.id" />
          </el-select>
        </el-form-item>
        <template v-if="exchangeQuote">
          <el-form-item label="原卡剩余价值"><strong>{{ formatMoney(exchangeQuote.oldRemainingValue) }}</strong></el-form-item>
          <el-form-item label="新卡售价"><span>{{ formatMoney(exchangeQuote.newCardValue) }}</span></el-form-item>
          <el-form-item label="应收补差"><strong class="asset-out">{{ formatMoney(exchangeQuote.differenceAmount) }}</strong></el-form-item>
          <el-form-item label="经办员工"><el-select v-model="exchangeForm.employeeId" clearable style="width: 100%"><el-option v-for="item in salesEmployees" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
          <template v-if="exchangeQuote.differenceAmount > 0">
            <el-form-item label="支付方式" required><el-select v-model="exchangeForm.paymentMethodId" style="width: 100%"><el-option v-for="item in paymentMethods.filter((method) => method.type !== 'STORED_VALUE')" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
            <el-form-item v-if="selectedExchangeMethod?.needsExternalReference" label="外部凭证" required><el-input v-model="exchangeForm.externalReference" maxlength="128" /></el-form-item>
          </template>
          <el-form-item label="报价有效期"><span>{{ formatTime(exchangeQuote.expiresAt) }}</span></el-form-item>
        </template>
      </el-form>
      <template #footer><el-button @click="exchangeVisible = false">取消</el-button><el-button type="primary" :disabled="!exchangeQuote" :loading="submitting" @click="submitCardExchange">确认收款并换卡</el-button></template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="次卡转赠" width="620px" destroy-on-close>
      <el-alert title="转赠会关闭原卡，并为接收会员建立仅包含当前剩余次数的新卡；操作完成后不能直接撤回。" type="warning" :closable="false" />
      <el-form label-width="110px" style="margin-top: 20px">
        <el-form-item label="转出次卡"><span>{{ transferSourceCard?.cardTypeName }} · 剩余 {{ transferSourceCard?.remainingTimes }} 次</span></el-form-item>
        <el-form-item label="接收会员" required>
          <el-select v-model="transferForm.recipientMemberId" filterable remote reserve-keyword :remote-method="searchTransferRecipients" :loading="recipientLoading" placeholder="输入手机号、会员号或会员卡号" style="width: 100%">
            <el-option v-for="item in recipientMembers" :key="item.id" :label="`${item.fullName} · ${item.memberNo} · ${item.maskedMobile}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="新卡有效期" required><el-date-picker v-model="transferForm.expiresAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择新卡到期时间" style="width: 100%" /></el-form-item>
        <el-form-item label="经办员工"><el-select v-model="transferForm.employeeId" clearable style="width: 100%"><el-option v-for="item in salesEmployees" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="转赠原因" required><el-input v-model="transferForm.reason" type="textarea" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="transferVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitCardTransfer">确认转赠</el-button></template>
    </el-dialog>

    <el-dialog v-model="refundVisible" title="申请退卡" width="680px" destroy-on-close>
      <el-alert title="提交后次卡会立即冻结；退款按已消费项目的原价重新计价，审批通过后才会清零并退款。" type="warning" :closable="false" />
      <el-form v-loading="refundQuoteLoading" label-width="110px" style="margin-top: 20px">
        <el-form-item label="退卡次卡"><span>{{ refundSourceCard?.cardTypeName }} · 剩余 {{ refundSourceCard?.remainingTimes }} 次</span></el-form-item>
        <el-form-item label="手续费"><el-input-number v-model="refundForm.feeAmount" :min="0" :max="1000000" :precision="2" /><el-button style="margin-left: 12px" @click="refreshCardRefundQuote">重新试算</el-button></el-form-item>
        <template v-if="refundQuote">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="办卡金额">{{ formatMoney(refundQuote.originalAmount) }}</el-descriptions-item>
            <el-descriptions-item label="消费原价重计">-{{ formatMoney(refundQuote.consumedRepriceAmount) }}</el-descriptions-item>
            <el-descriptions-item label="手续费">-{{ formatMoney(refundQuote.feeAmount) }}</el-descriptions-item>
            <el-descriptions-item label="预计退款"><strong class="asset-in">{{ formatMoney(refundQuote.refundAmount) }}</strong></el-descriptions-item>
          </el-descriptions>
          <el-table v-if="refundQuote.items.length" :data="refundQuote.items" size="small" style="margin: 16px 0">
            <el-table-column prop="billNo" label="消费账单" min-width="160" />
            <el-table-column prop="serviceName" label="消费项目" min-width="160" />
            <el-table-column label="项目原价" width="120" align="right"><template #default="scope">{{ formatMoney(scope.row.originalAmount) }}</template></el-table-column>
          </el-table>
          <el-form-item v-if="refundQuote.refundAmount > 0" label="退款方式" required><el-select v-model="refundForm.refundMethodId" style="width: 100%"><el-option v-for="item in paymentMethods.filter((method) => method.type !== 'STORED_VALUE')" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
          <el-form-item v-if="refundQuote.refundAmount > 0 && selectedRefundMethod?.needsExternalReference" label="执行凭证"><span class="muted">审批执行时填写外部退款凭证</span></el-form-item>
          <el-form-item label="经办员工"><el-select v-model="refundForm.employeeId" clearable style="width: 100%"><el-option v-for="item in salesEmployees" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="退卡原因" required><el-input v-model="refundForm.reason" type="textarea" maxlength="1000" show-word-limit /></el-form-item>
          <el-alert title="售卡技师和店长提成冲回将在提成模块中处理；当前申请会明确标记为待处理。" type="info" :closable="false" />
        </template>
      </el-form>
      <template #footer><el-button @click="refundVisible = false">取消</el-button><el-button type="danger" :disabled="!refundQuote" :loading="submitting" @click="submitCardRefundRequest">提交退卡申请</el-button></template>
    </el-dialog>
  </div>
</template>
