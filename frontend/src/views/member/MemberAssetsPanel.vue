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
import { useAuthStore } from '@/stores/auth'
import type {
  BalanceAccount,
  BalanceLedgerItem,
  PaymentMethodOption,
  PointAccount,
  PointLedgerItem,
} from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const props = defineProps<{ memberId: number; storeId: number }>()
const emit = defineEmits<{
  changed: [payload: { balance: BalanceAccount; points: PointAccount }]
}>()
const auth = useAuthStore()
const loading = ref(true)
const submitting = ref(false)
const balance = ref<BalanceAccount>()
const points = ref<PointAccount>()
const balanceLedgers = ref<BalanceLedgerItem[]>([])
const pointLedgers = ref<PointLedgerItem[]>([])
const paymentMethods = ref<PaymentMethodOption[]>([])
const rechargeVisible = ref(false)
const pointVisible = ref(false)
const rechargeForm = reactive({ rechargeAmount: 0, giftAmount: 0, paymentMethodId: 0, externalReference: '' })
const pointForm = reactive({ changePoints: 0, reason: '' })
const selectedMethod = computed(() => paymentMethods.value.find((item) => item.id === rechargeForm.paymentMethodId))
const canManage = computed(() => auth.hasPermission('member:asset:manage'))

const balanceTypeLabels: Record<string, string> = {
  RECHARGE: '充值本金', RECHARGE_GIFT: '充值赠送', CONSUME: '消费', REFUND: '退款',
  ADJUST_IN: '调增', ADJUST_OUT: '调减', REVERSAL: '冲销', MIGRATION: '数据迁移',
}
const pointTypeLabels: Record<string, string> = {
  EARN: '获得', REDEEM: '兑换', EXPIRE: '过期', REFUND: '退回',
  ADJUST_IN: '调增', ADJUST_OUT: '调减', REVERSAL: '冲销', MIGRATION: '数据迁移',
}

async function load() {
  loading.value = true
  try {
    const [nextBalance, nextPoints, nextBalanceLedgers, nextPointLedgers] = await Promise.all([
      getBalanceAccount(props.memberId),
      getPointAccount(props.memberId),
      getBalanceLedgers(props.memberId),
      getPointLedgers(props.memberId),
    ])
    balance.value = nextBalance
    points.value = nextPoints
    balanceLedgers.value = nextBalanceLedgers
    pointLedgers.value = nextPointLedgers
    emit('changed', { balance: nextBalance, points: nextPoints })
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
  </div>
</template>
