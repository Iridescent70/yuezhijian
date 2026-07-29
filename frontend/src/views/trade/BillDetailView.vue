<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addBillLine,
  applyBillDiscount,
  createReversal,
  getBill,
  getBillCancelReasons,
  removeBillLine,
  updateBillLine,
  voidBill,
} from '@/api/trade'
import { getEmployees, getServices } from '@/api/masterData'
import { useAuthStore } from '@/stores/auth'
import type { BillDetail, BillLine, CancelReasonOption, EmployeeSummary, ServiceItemSummary } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const billId = Number(route.params.billId)
const loading = ref(true)
const saving = ref(false)
const lineVisible = ref(false)
const voidVisible = ref(false)
const discountVisible = ref(false)
const reversalVisible = ref(false)
const editingLineId = ref<number | null>(null)
const detail = ref<BillDetail | null>(null)
const services = ref<ServiceItemSummary[]>([])
const employees = ref<EmployeeSummary[]>([])
const cancelReasons = ref<CancelReasonOption[]>([])
const lineForm = reactive({
  serviceId: undefined as number | undefined,
  quantity: 1,
  employeeId: undefined as number | undefined,
  note: '',
})
const voidForm = reactive({ reasonCode: '', note: '' })
const discountForm = reactive({
  discountType: 'RATE' as 'AMOUNT' | 'RATE',
  value: 90,
  reason: '',
})
const reversalForm = reactive({ reason: '' })
const mutable = computed(() => Boolean(
  detail.value && ['DRAFT', 'PENDING_PAYMENT'].includes(detail.value.bill.status),
))

async function load() {
  loading.value = true
  try {
    detail.value = await getBill(billId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '账单加载失败')
  } finally {
    loading.value = false
  }
}

async function loadLineOptions() {
  if (!detail.value || (services.value.length && employees.value.length)) return
  ;[services.value, employees.value] = await Promise.all([
    getServices({ storeId: detail.value.bill.storeId }),
    getEmployees({ storeId: detail.value.bill.storeId }),
  ])
}

async function openAdd() {
  await loadLineOptions()
  editingLineId.value = null
  Object.assign(lineForm, { serviceId: undefined, quantity: 1, employeeId: undefined, note: '' })
  lineVisible.value = true
}

async function openEdit(line: BillLine) {
  await loadLineOptions()
  editingLineId.value = line.id
  Object.assign(lineForm, {
    serviceId: line.itemId,
    quantity: Number(line.quantity),
    employeeId: line.employeeId,
    note: line.note ?? '',
  })
  lineVisible.value = true
}

function openEditById(lineId: number) {
  const line = detail.value?.lines.find((item) => item.id === lineId)
  if (line) void openEdit(line)
}

async function submitLine() {
  if (!detail.value || !lineForm.serviceId) {
    ElMessage.warning('请选择服务项目')
    return
  }
  const hadDiscount = detail.value.bill.discountAmount > 0
  saving.value = true
  try {
    if (editingLineId.value) {
      detail.value = await updateBillLine(billId, editingLineId.value, {
        quantity: lineForm.quantity,
        employeeId: lineForm.employeeId,
        note: lineForm.note || undefined,
        version: detail.value.bill.version,
      })
      ElMessage.success(hadDiscount ? '项目已修改，原优惠已自动清除' : '消费项目已修改')
    } else {
      detail.value = await addBillLine(billId, {
        serviceId: lineForm.serviceId,
        quantity: lineForm.quantity,
        employeeId: lineForm.employeeId,
        note: lineForm.note || undefined,
        version: detail.value.bill.version,
      })
      ElMessage.success(hadDiscount ? '项目已添加，原优惠已自动清除' : '消费项目已添加')
    }
    lineVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeLine(line: BillLine) {
  if (!detail.value) return
  await ElMessageBox.confirm(`确认删除“${line.itemName}”吗？`, '删除消费项目', { type: 'warning' })
  const hadDiscount = detail.value.bill.discountAmount > 0
  saving.value = true
  try {
    detail.value = await removeBillLine(billId, line.id, detail.value.bill.version)
    ElMessage.success(hadDiscount ? '项目已删除，原优惠已自动清除' : '消费项目已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  } finally {
    saving.value = false
  }
}

function removeLineById(lineId: number) {
  const line = detail.value?.lines.find((item) => item.id === lineId)
  if (line) void removeLine(line)
}

function openDiscount() {
  if (!detail.value?.lines.length) return
  Object.assign(discountForm, {
    discountType: 'RATE',
    value: detail.value.bill.discountAmount > 0
      ? Number(((detail.value.bill.receivableAmount / detail.value.bill.originalAmount) * 100).toFixed(2))
      : 90,
    reason: '',
  })
  discountVisible.value = true
}

async function submitDiscount() {
  if (!detail.value || !discountForm.reason.trim()) {
    ElMessage.warning('请填写优惠原因')
    return
  }
  saving.value = true
  try {
    detail.value = await applyBillDiscount(billId, {
      discountType: discountForm.discountType,
      value: discountForm.discountType === 'RATE' ? discountForm.value / 100 : discountForm.value,
      reason: discountForm.reason,
      version: detail.value.bill.version,
    })
    discountVisible.value = false
    ElMessage.success(detail.value.bill.discountAmount > 0 ? '优惠已重新分摊' : '优惠已清除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '优惠保存失败')
  } finally {
    saving.value = false
  }
}

async function submitVoid() {
  if (!detail.value) return
  const reason = cancelReasons.value.find(item => item.code === voidForm.reasonCode)
  if (!reason) { ElMessage.warning('请选择作废原因'); return }
  if (reason.requiresNote && !voidForm.note.trim()) { ElMessage.warning('该原因必须填写说明'); return }
  saving.value = true
  try {
    detail.value = await voidBill(billId, {
      reasonCode: voidForm.reasonCode,
      note: voidForm.note || undefined,
      version: detail.value.bill.version,
    })
    voidVisible.value = false
    ElMessage.success('账单已作废')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '作废失败')
  } finally {
    saving.value = false
  }
}

async function openVoid() {
  try {
    cancelReasons.value = await getBillCancelReasons()
    if (!cancelReasons.value.length) {
      ElMessage.warning('没有已启用的账单作废原因，请先联系管理员配置')
      return
    }
    Object.assign(voidForm, { reasonCode: cancelReasons.value[0]?.code ?? '', note: '' })
    voidVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '作废原因加载失败')
  }
}

async function submitReversal() {
  if (!reversalForm.reason.trim()) {
    ElMessage.warning('请填写冲销原因')
    return
  }
  saving.value = true
  try {
    const reversal = await createReversal(billId, reversalForm.reason.trim(), crypto.randomUUID())
    reversalVisible.value = false
    ElMessage.success(`冲销申请 ${reversal.reversal.reversalNo} 已提交审批`)
    await router.push('/app/settlement/reversals')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '冲销申请提交失败')
  } finally {
    saving.value = false
  }
}

function dateTime(value?: string) {
  return value?.replace('T', ' ').slice(0, 19) ?? '—'
}

function assetTypeName(type: string) {
  return ({ BALANCE: '储值', POINT: '积分', CARD: '次卡' } as Record<string, string>)[type] ?? type
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="page-content">
    <template v-if="detail">
      <div class="section-title-row">
        <div class="member-title-block">
          <el-button link @click="router.push('/app/bills')">← 返回账单列表</el-button>
          <div class="member-title-line"><h1>{{ detail.bill.billNo }}</h1><el-tag>{{ detail.bill.status }}</el-tag></div>
          <p>{{ detail.bill.customerName }} · {{ detail.bill.storeName }} · {{ dateTime(detail.bill.createdAt) }}</p>
        </div>
        <div>
          <el-button
            v-if="detail.bill.status === 'SETTLED' && auth.hasPermission('trade:reversal:manage')"
            type="danger"
            plain
            @click="reversalForm.reason = ''; reversalVisible = true"
          >申请冲销</el-button>
          <el-button v-if="mutable && auth.hasPermission('trade:bill:manage')" @click="openVoid">作废</el-button>
          <el-button v-if="mutable && detail.lines.length && auth.hasPermission('trade:bill:manage')" @click="openDiscount">整单优惠</el-button>
          <el-button v-if="mutable && auth.hasPermission('trade:bill:manage')" @click="openAdd">添加项目</el-button>
          <el-button
            v-if="mutable && detail.lines.length && auth.hasPermission('trade:bill:settle')"
            type="primary"
            @click="router.push(`/app/bills/${billId}/settle`)"
          >收银结算</el-button>
        </div>
      </div>

      <div class="bill-amount-grid">
        <article><span>原价</span><strong>{{ formatMoney(detail.bill.originalAmount) }}</strong></article>
        <article><span>优惠</span><strong>{{ formatMoney(detail.bill.discountAmount) }}</strong></article>
        <article class="accent"><span>应收</span><strong>{{ formatMoney(detail.bill.receivableAmount) }}</strong></article>
        <article><span>实收</span><strong>{{ formatMoney(detail.bill.receivedAmount) }}</strong></article>
      </div>

      <el-card class="data-card" shadow="never">
        <template #header><strong>消费明细</strong></template>
        <el-table :data="detail.lines">
          <el-table-column prop="lineNo" label="#" width="55" />
          <el-table-column prop="itemName" label="项目" min-width="180" />
          <el-table-column prop="employeeName" label="服务技师" width="110" />
          <el-table-column prop="quantity" label="数量" width="75" />
          <el-table-column label="原价" width="110" align="right">
            <template #default="scope">{{ formatMoney(scope.row.originalAmount) }}</template>
          </el-table-column>
          <el-table-column label="优惠" width="105" align="right">
            <template #default="scope">{{ formatMoney(scope.row.discountAmount) }}</template>
          </el-table-column>
          <el-table-column label="应收" width="110" align="right">
            <template #default="scope">{{ formatMoney(scope.row.receivableAmount) }}</template>
          </el-table-column>
          <el-table-column v-if="mutable && auth.hasPermission('trade:bill:manage')" label="操作" width="110" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEditById(scope.row.id)">编辑</el-button>
              <el-button link type="danger" @click="removeLineById(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card v-if="detail.discounts.length" class="data-card bill-secondary-card" shadow="never">
        <template #header><strong>优惠分摊</strong></template>
        <el-table :data="detail.discounts" size="small">
          <el-table-column prop="batchNo" label="优惠批次" min-width="170" />
          <el-table-column prop="reason" label="原因" min-width="180" />
          <el-table-column label="原价" width="110" align="right">
            <template #default="scope">{{ formatMoney(scope.row.originalAmount) }}</template>
          </el-table-column>
          <el-table-column label="优惠" width="110" align="right">
            <template #default="scope">{{ formatMoney(scope.row.discountAmount) }}</template>
          </el-table-column>
          <el-table-column label="授权时间" width="170">
            <template #default="scope">{{ dateTime(scope.row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card v-if="detail.assetUsages.length" class="data-card bill-secondary-card" shadow="never">
        <template #header><strong>会员资产抵扣</strong></template>
        <el-table :data="detail.assetUsages" size="small">
          <el-table-column label="资产" width="90">
            <template #default="scope"><el-tag effect="plain">{{ assetTypeName(scope.row.assetType) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="displayName" label="抵扣说明" min-width="220" />
          <el-table-column prop="quantity" label="扣减数量" width="110" />
          <el-table-column label="抵扣金额" width="120" align="right">
            <template #default="scope">{{ formatMoney(scope.row.amount) }}</template>
          </el-table-column>
          <el-table-column label="发生时间" width="170">
            <template #default="scope">{{ dateTime(scope.row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card v-if="detail.payments.length" class="data-card bill-secondary-card" shadow="never">
        <template #header><strong>外部支付明细</strong></template>
        <el-table :data="detail.payments">
          <el-table-column prop="paymentMethodName" label="方式" />
          <el-table-column prop="externalReference" label="凭证号" />
          <el-table-column label="金额" align="right"><template #default="scope">{{ formatMoney(scope.row.amount) }}</template></el-table-column>
          <el-table-column label="支付时间"><template #default="scope">{{ dateTime(scope.row.paidAt) }}</template></el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-dialog v-model="lineVisible" :title="editingLineId ? '编辑消费项目' : '添加消费项目'" width="620px">
      <el-form label-width="90px">
        <el-form-item label="服务项目" required>
          <el-select v-model="lineForm.serviceId" :disabled="Boolean(editingLineId)" class="dialog-full-control">
            <el-option v-for="item in services" :key="item.id" :label="`${item.name} · ${formatMoney(item.storePrice)}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量"><el-input-number v-model="lineForm.quantity" :min="0.0001" :precision="2" /></el-form-item>
        <el-form-item label="服务技师">
          <el-select v-model="lineForm.employeeId" clearable class="dialog-full-control">
            <el-option v-for="item in employees.filter((employee) => employee.canService)" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="lineForm.note" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lineVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitLine">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="discountVisible" title="整单优惠" width="520px">
      <el-form label-width="100px">
        <el-form-item label="优惠方式">
          <el-radio-group v-model="discountForm.discountType">
            <el-radio-button value="RATE">折扣率</el-radio-button>
            <el-radio-button value="AMOUNT">优惠金额</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="discountForm.discountType === 'RATE' ? '实付比例' : '优惠金额'">
          <el-input-number
            v-model="discountForm.value"
            :min="discountForm.discountType === 'RATE' ? 0.01 : 0"
            :max="discountForm.discountType === 'RATE'
              ? 100
              : Math.max(Number(detail?.bill.originalAmount ?? 0) - 0.01, 0)"
            :precision="2"
          />
          <span class="discount-unit">{{ discountForm.discountType === 'RATE' ? '%' : '元' }}</span>
        </el-form-item>
        <el-form-item label="优惠原因" required><el-input v-model="discountForm.reason" type="textarea" maxlength="500" /></el-form-item>
        <el-alert title="优惠会按各项目原价比例分摊；之后增删改项目会自动清除本次优惠。" type="info" :closable="false" />
      </el-form>
      <template #footer>
        <el-button @click="discountVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDiscount">保存优惠</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="voidVisible" title="作废账单" width="500px">
      <el-form label-width="90px">
        <el-form-item label="原因">
          <el-select v-model="voidForm.reasonCode">
            <el-option v-for="item in cancelReasons" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="voidForm.note" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="voidVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="submitVoid">确认作废</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reversalVisible" title="申请整单冲销" width="540px">
      <el-alert
        title="执行后会退回整单支付、储值、积分和次卡次数；申请提交后仍需审批。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form label-width="90px" class="reversal-form">
        <el-form-item label="退款金额"><strong>{{ formatMoney(detail?.bill.receivableAmount ?? 0) }}</strong></el-form-item>
        <el-form-item label="冲销原因" required>
          <el-input v-model="reversalForm.reason" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reversalVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="submitReversal">提交审批</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.discount-unit { margin-left: 8px; color: var(--el-text-color-secondary); }
.reversal-form { margin-top: 20px; }
</style>
