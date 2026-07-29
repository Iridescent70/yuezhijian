<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getBill,
  getPaymentMethods,
  getSettlementAssetOptions,
  quoteSettlement,
  settleBill,
} from '@/api/trade'
import type {
  BillDetail,
  CardSettlementOption,
  PaymentMethodOption,
  SettlementAssetOptions,
  SettlementQuote,
} from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const route = useRoute()
const router = useRouter()
const billId = Number(route.params.billId)
const loading = ref(true)
const quoting = ref(false)
const settling = ref(false)
const detail = ref<BillDetail | null>(null)
const methods = ref<PaymentMethodOption[]>([])
const assetOptions = ref<SettlementAssetOptions | null>(null)
const quote = ref<SettlementQuote | null>(null)
const balanceAmount = ref(0)
const points = ref(0)
const selectedCards = reactive<Record<number, number | undefined>>({})
const payments = reactive<Array<{
  paymentMethodId?: number
  amount: number
  externalReference: string
}>>([])

const receivable = computed(() => Number(detail.value?.bill.receivableAmount ?? 0))
const selectedCardOptions = computed(() => Object.entries(selectedCards)
  .map(([lineId, cardId]) => cardOptionsFor(Number(lineId)).find((item) => item.memberCardId === cardId))
  .filter((item): item is CardSettlementOption => Boolean(item)))
const cardAmount = computed(() => selectedCardOptions.value.reduce((sum, option) => {
  const line = detail.value?.lines.find((item) => item.id === option.billLineId)
  return sum + Number(line?.receivableAmount ?? 0)
}, 0))
const pointAmount = computed(() => points.value / Number(assetOptions.value?.pointsPerYuan || 1))
const estimatedAssetAmount = computed(() => roundMoney(cardAmount.value + balanceAmount.value + pointAmount.value))
const externalPaymentAmount = computed(() => roundMoney(
  payments.reduce((sum, item) => sum + Number(item.amount || 0), 0),
))
const estimatedPaymentTotal = computed(() => roundMoney(
  estimatedAssetAmount.value + externalPaymentAmount.value,
))
const difference = computed(() => Math.max(roundMoney(receivable.value - estimatedPaymentTotal.value), 0))
const balanceLimit = computed(() => Math.max(Math.min(
  Number(assetOptions.value?.balanceAccount?.availableBalance ?? 0),
  receivable.value - cardAmount.value,
), 0))
const pointLimit = computed(() => Math.max(Math.min(
  Number(assetOptions.value?.pointAccount?.availablePoints ?? 0),
  Math.floor(Math.max(receivable.value - cardAmount.value - balanceAmount.value, 0)
    * Number(assetOptions.value?.pointsPerYuan || 1)),
), 0))

async function load() {
  loading.value = true
  try {
    detail.value = await getBill(billId)
    if (!['DRAFT', 'PENDING_PAYMENT'].includes(detail.value.bill.status)) {
      ElMessage.warning('当前账单不可结算')
      await router.replace(`/app/bills/${billId}`)
      return
    }
    const [paymentMethods, settlementAssets] = await Promise.all([
      getPaymentMethods(detail.value.bill.storeId),
      getSettlementAssetOptions(billId),
    ])
    methods.value = paymentMethods
    assetOptions.value = settlementAssets
    const defaultMethod = methods.value.find((item) => item.type === 'CASH') ?? methods.value[0]
    payments.splice(0, payments.length, {
      paymentMethodId: defaultMethod?.id,
      amount: receivable.value,
      externalReference: '',
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '结算数据加载失败')
  } finally {
    loading.value = false
  }
}

function cardOptionsFor(lineId: number) {
  return assetOptions.value?.cardOptions.filter((item) => item.billLineId === lineId) ?? []
}

function hasCardOption(lineId: number) {
  return cardOptionsFor(lineId).length > 0
}

function cardOptionDisabled(option: CardSettlementOption) {
  return selectedCardOptions.value.some((selected) => selected.billLineId !== option.billLineId
    && selected.memberCardBalanceId === option.memberCardBalanceId)
}

function addPayment() {
  payments.push({ paymentMethodId: undefined, amount: 0, externalReference: '' })
  quote.value = null
}

function removePayment(index: number) {
  payments.splice(index, 1)
  quote.value = null
}

function methodFor(id?: number) {
  return methods.value.find((item) => item.id === id)
}

function onAssetChange() {
  balanceAmount.value = Math.min(Math.max(Number(balanceAmount.value || 0), 0), balanceLimit.value)
  points.value = Math.min(Math.max(Math.trunc(Number(points.value || 0)), 0), pointLimit.value)
  quote.value = null
  fillRemainingWithCash()
}

function fillRemainingWithCash() {
  const cashIndex = payments.findIndex((item) => methodFor(item.paymentMethodId)?.type === 'CASH')
  if (cashIndex < 0) return
  const otherPaymentTotal = payments.reduce((sum, item, index) => (
    index === cashIndex ? sum : sum + Number(item.amount || 0)
  ), 0)
  payments[cashIndex]!.amount = Math.max(roundMoney(
    receivable.value - estimatedAssetAmount.value - otherPaymentTotal,
  ), 0)
  quote.value = null
}

async function createQuote() {
  const nonEmptyPayments = payments.filter((item) => Number(item.amount || 0) > 0)
  if (nonEmptyPayments.some((item) => !item.paymentMethodId)) {
    ElMessage.warning('请完整填写支付方式和金额')
    return
  }
  quoting.value = true
  try {
    quote.value = await quoteSettlement(billId, {
      balanceAmount: balanceAmount.value,
      points: points.value,
      cards: Object.entries(selectedCards)
        .filter((entry): entry is [string, number] => entry[1] !== undefined)
        .map(([lineId, cardId]) => ({ billLineId: Number(lineId), memberCardId: cardId })),
      payments: nonEmptyPayments.map((item) => ({
        paymentMethodId: item.paymentMethodId!,
        amount: item.amount,
        externalReference: item.externalReference || undefined,
      })),
    })
    ElMessage.success('试算完成，请核对金额')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '试算失败')
  } finally {
    quoting.value = false
  }
}

async function settle() {
  if (!quote.value || quote.value.differenceAmount > 0) {
    ElMessage.warning('请先完成有效试算')
    return
  }
  await ElMessageBox.confirm(
    `确认结算 ${formatMoney(quote.value.receivableAmount)} 吗？`,
    '确认结算',
    { type: 'warning' },
  )
  settling.value = true
  try {
    await settleBill(billId, quote.value.quoteNo, crypto.randomUUID())
    ElMessage.success('账单结算完成')
    await router.replace(`/app/bills/${billId}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '结算失败')
  } finally {
    settling.value = false
  }
}

function roundMoney(value: number) {
  return Math.round((value + Number.EPSILON) * 10_000) / 10_000
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="page-content settlement-page">
    <template v-if="detail">
      <div class="section-title-row">
        <div>
          <h1>收银结算</h1>
          <p>{{ detail.bill.billNo }} · {{ detail.bill.customerName }} · {{ detail.bill.storeName }}</p>
        </div>
        <el-button @click="router.push(`/app/bills/${billId}`)">返回账单</el-button>
      </div>

      <div class="settlement-layout">
        <div class="settlement-left">
          <el-card shadow="never">
            <template #header><strong>消费明细与次卡抵扣</strong></template>
            <el-table :data="detail.lines" size="small">
              <el-table-column prop="itemName" label="项目" min-width="150" />
              <el-table-column prop="employeeName" label="技师" width="100" />
              <el-table-column prop="quantity" label="数量" width="65" />
              <el-table-column label="金额" width="105" align="right">
                <template #default="scope">{{ formatMoney(scope.row.receivableAmount) }}</template>
              </el-table-column>
              <el-table-column v-if="detail.bill.memberId" label="次卡抵扣" min-width="250">
                <template #default="scope">
                  <el-select
                    v-if="hasCardOption(scope.row.id)"
                    v-model="selectedCards[scope.row.id]"
                    clearable
                    placeholder="不使用次卡"
                    @change="onAssetChange"
                  >
                    <el-option
                      v-for="option in cardOptionsFor(scope.row.id)"
                      :key="`${option.billLineId}-${option.memberCardId}`"
                      :disabled="cardOptionDisabled(option)"
                      :value="option.memberCardId"
                      :label="`${option.cardTypeName}（余${option.remainingTimes}，扣${option.requiredTimes}）`"
                    />
                  </el-select>
                  <span v-else class="muted-text">无可用次卡</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card v-if="detail.bill.memberId && assetOptions" shadow="never">
            <template #header><strong>会员资产抵扣</strong></template>
            <div class="asset-fields">
              <div class="asset-field">
                <label>储值余额</label>
                <el-input-number
                  v-model="balanceAmount"
                  :min="0"
                  :max="balanceLimit"
                  :precision="2"
                  :step="10"
                  @change="onAssetChange"
                />
                <small>可用 {{ formatMoney(assetOptions.balanceAccount?.availableBalance ?? 0) }}</small>
              </div>
              <div class="asset-field">
                <label>积分抵扣</label>
                <el-input-number
                  v-model="points"
                  :min="0"
                  :max="pointLimit"
                  :step="assetOptions.pointsPerYuan"
                  :precision="0"
                  @change="onAssetChange"
                />
                <small>
                  可用 {{ assetOptions.pointAccount?.availablePoints ?? 0 }}，
                  {{ assetOptions.pointsPerYuan }}积分抵1元
                </small>
              </div>
            </div>
          </el-card>
        </div>

        <el-card shadow="never" class="payment-panel">
          <template #header>
            <div class="payment-panel-title">
              <strong>外部支付</strong>
              <el-button link type="primary" @click="addPayment">+ 添加支付方式</el-button>
            </div>
          </template>
          <div v-for="(payment, index) in payments" :key="index" class="payment-row">
            <el-select v-model="payment.paymentMethodId" placeholder="支付方式" @change="quote = null">
              <el-option v-for="item in methods" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <el-input-number v-model="payment.amount" :min="0" :precision="2" @change="quote = null" />
            <el-input
              v-if="methodFor(payment.paymentMethodId)?.needsExternalReference"
              v-model="payment.externalReference"
              placeholder="外部凭证号"
              @input="quote = null"
            />
            <span v-else class="payment-reference-placeholder">无需凭证</span>
            <el-button link type="danger" @click="removePayment(index)">删除</el-button>
          </div>
          <el-button class="fill-button" plain @click="fillRemainingWithCash">现金自动补齐差额</el-button>
          <el-divider />
          <div class="settlement-summary">
            <div><span>账单应收</span><strong>{{ formatMoney(detail.bill.receivableAmount) }}</strong></div>
            <div><span>会员资产</span><strong>{{ formatMoney(quote?.assetAmount ?? estimatedAssetAmount) }}</strong></div>
            <div><span>外部支付</span><strong>{{ formatMoney(quote?.externalPaymentAmount ?? externalPaymentAmount) }}</strong></div>
            <div><span>支付合计</span><strong>{{ formatMoney(quote?.paymentTotal ?? estimatedPaymentTotal) }}</strong></div>
            <div>
              <span>未付差额</span>
              <strong :class="{ danger: (quote?.differenceAmount ?? difference) > 0 }">
                {{ formatMoney(quote?.differenceAmount ?? difference) }}
              </strong>
            </div>
            <div><span>现金找零</span><strong>{{ formatMoney(quote?.changeAmount ?? 0) }}</strong></div>
          </div>
          <div v-if="quote?.assets.length" class="asset-confirmation">
            <div v-for="item in quote.assets" :key="`${item.assetType}-${item.displayName}`">
              <span>{{ item.displayName }}</span><strong>-{{ formatMoney(item.amount) }}</strong>
            </div>
          </div>
          <div class="settlement-actions">
            <el-button :loading="quoting" @click="createQuote">重新试算</el-button>
            <el-button
              type="primary"
              :loading="settling"
              :disabled="!quote || quote.differenceAmount > 0"
              @click="settle"
            >确认结算</el-button>
          </div>
        </el-card>
      </div>
    </template>
  </section>
</template>

<style scoped>
.settlement-layout { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(360px, 1fr); gap: 16px; align-items: start; }
.settlement-left { display: grid; gap: 16px; }
.asset-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
.asset-field { display: grid; grid-template-columns: 90px minmax(140px, 1fr); align-items: center; gap: 8px 12px; }
.asset-field label { font-weight: 600; }
.asset-field small { grid-column: 2; color: var(--el-text-color-secondary); }
.payment-panel-title { display: flex; align-items: center; justify-content: space-between; }
.payment-row { display: grid; grid-template-columns: minmax(110px, 1fr) 130px minmax(120px, 1fr) 44px; gap: 8px; align-items: center; margin-bottom: 10px; }
.payment-reference-placeholder, .muted-text { color: var(--el-text-color-secondary); font-size: 13px; }
.fill-button { width: 100%; margin-top: 2px; }
.settlement-summary { display: grid; gap: 10px; }
.settlement-summary > div, .asset-confirmation > div { display: flex; justify-content: space-between; gap: 16px; }
.settlement-summary strong { font-variant-numeric: tabular-nums; }
.danger { color: var(--el-color-danger); }
.asset-confirmation { margin-top: 14px; padding: 12px; border-radius: 6px; background: var(--el-fill-color-light); color: var(--el-text-color-regular); display: grid; gap: 8px; font-size: 13px; }
.settlement-actions { display: flex; justify-content: flex-end; margin-top: 20px; }
@media (max-width: 1100px) { .settlement-layout { grid-template-columns: 1fr; } }
@media (max-width: 700px) { .asset-fields { grid-template-columns: 1fr; } .payment-row { grid-template-columns: 1fr; } }
</style>
