<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  bindVoucherCode,
  createVoucherDefinition,
  getVoucherCodes,
  getVoucherDefinitions,
  issueVoucherCodes,
  updateVoucherDefinition,
} from '@/api/benefit'
import { searchMembers } from '@/api/member'
import { useAuthStore } from '@/stores/auth'
import type { MemberSummary, VoucherCodeStatus, VoucherCodeSummary, VoucherDefinition } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const auth = useAuthStore()
const activeTab = ref('definitions')
const loading = ref(false)
const saving = ref(false)
const definitions = ref<VoucherDefinition[]>([])
const codes = ref<VoucherCodeSummary[]>([])
const keyword = ref('')
const codeStatus = ref<VoucherCodeStatus | ''>('')
const definitionDialog = ref(false)
const issueDialog = ref(false)
const bindDialog = ref(false)
const members = ref<MemberSummary[]>([])
const memberLoading = ref(false)
const issuedCodes = ref<VoucherCodeSummary[]>([])
const bindingCode = ref<VoucherCodeSummary>()

const definitionForm = reactive({
  id: undefined as number | undefined,
  code: '',
  name: '',
  benefitType: 'FIXED_AMOUNT' as 'FIXED_AMOUNT' | 'DISCOUNT',
  faceAmount: 50,
  discountPercent: 80,
  minSpend: 0,
  validDays: 30,
  commissionRule: '',
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE',
  version: '',
})
const issueForm = reactive({ voucherId: 0, count: 1, memberId: undefined as number | undefined })
const bindForm = reactive({ memberId: undefined as number | undefined })

const statusLabels: Record<VoucherCodeStatus, string> = {
  UNBOUND: '未绑定', BOUND: '可使用', REDEEMED: '已核销', EXPIRED: '已过期', VOIDED: '已作废',
}

async function loadDefinitions() {
  loading.value = true
  try {
    definitions.value = await getVoucherDefinitions({ keyword: keyword.value || undefined })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '代金券定义加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCodes() {
  loading.value = true
  try {
    codes.value = await getVoucherCodes({ keyword: keyword.value || undefined, status: codeStatus.value || undefined })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '券码加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(definitionForm, {
    id: undefined, code: '', name: '', benefitType: 'FIXED_AMOUNT', faceAmount: 50,
    discountPercent: 80, minSpend: 0, validDays: 30, commissionRule: '', status: 'ACTIVE', version: '',
  })
  definitionDialog.value = true
}

function openEdit(row: VoucherDefinition) {
  Object.assign(definitionForm, {
    id: row.id, code: row.code, name: row.name, benefitType: row.benefitType,
    faceAmount: row.faceAmount, discountPercent: Math.round(row.discountRate * 100),
    minSpend: row.minSpend, validDays: row.validDays, commissionRule: row.commissionRule ?? '',
    status: row.status, version: row.version,
  })
  definitionDialog.value = true
}

async function saveDefinition() {
  if (!definitionForm.code.trim() || !definitionForm.name.trim()) {
    ElMessage.warning('请填写券编码和名称')
    return
  }
  saving.value = true
  const payload = {
    name: definitionForm.name.trim(), benefitType: definitionForm.benefitType,
    faceAmount: definitionForm.benefitType === 'FIXED_AMOUNT' ? definitionForm.faceAmount : 0,
    discountRate: definitionForm.benefitType === 'DISCOUNT' ? definitionForm.discountPercent / 100 : 1,
    minSpend: definitionForm.minSpend, validDays: definitionForm.validDays,
    commissionRule: definitionForm.commissionRule.trim() || undefined,
  }
  try {
    if (definitionForm.id) {
      await updateVoucherDefinition(definitionForm.id, {
        ...payload, status: definitionForm.status, version: definitionForm.version,
      })
    } else {
      await createVoucherDefinition({ ...payload, code: definitionForm.code.trim().toUpperCase() })
    }
    ElMessage.success(definitionForm.id ? '代金券定义已更新' : '代金券定义已创建')
    definitionDialog.value = false
    await loadDefinitions()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function openIssue(row: VoucherDefinition) {
  Object.assign(issueForm, { voucherId: row.id, count: 1, memberId: undefined })
  issuedCodes.value = []
  members.value = []
  issueDialog.value = true
}

async function issue() {
  saving.value = true
  try {
    issuedCodes.value = await issueVoucherCodes({
      voucherId: issueForm.voucherId, count: issueForm.count,
      memberId: issueForm.memberId, idempotencyKey: crypto.randomUUID(),
    })
    ElMessage.success(`已生成 ${issuedCodes.value.length} 张券`)
    await loadCodes()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发券失败')
  } finally {
    saving.value = false
  }
}

function openBind(row: VoucherCodeSummary) {
  bindingCode.value = row
  bindForm.memberId = undefined
  members.value = []
  bindDialog.value = true
}

async function bind() {
  if (!bindingCode.value || !bindForm.memberId) {
    ElMessage.warning('请选择会员')
    return
  }
  saving.value = true
  try {
    await bindVoucherCode(bindingCode.value.code, bindForm.memberId, crypto.randomUUID())
    ElMessage.success('券码已绑定会员')
    bindDialog.value = false
    await loadCodes()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '绑定失败')
  } finally {
    saving.value = false
  }
}

async function searchMember(keywordValue: string) {
  if (!keywordValue.trim()) { members.value = []; return }
  memberLoading.value = true
  try {
    members.value = (await searchMembers({ keyword: keywordValue.trim(), status: 'ACTIVE', page: 1, size: 20 })).items
  } finally {
    memberLoading.value = false
  }
}

function benefitText(rowValue: unknown) {
  const row = rowValue as VoucherDefinition | VoucherCodeSummary
  return row.benefitType === 'FIXED_AMOUNT' ? formatMoney(row.faceAmount) : `${Number((row.discountRate * 10).toFixed(2))}折`
}
function editRow(row: unknown) { openEdit(row as VoucherDefinition) }
function issueRow(row: unknown) { openIssue(row as VoucherDefinition) }
function bindRow(row: unknown) { openBind(row as VoucherCodeSummary) }
function dateTime(value?: string) { return value?.replace('T', ' ').slice(0, 19) ?? '—' }
function onTabChange() { keyword.value = ''; activeTab.value === 'definitions' ? loadDefinitions() : loadCodes() }

onMounted(loadDefinitions)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>代金券管理</h1><p>模板、发券和券码状态统一管理；已发券保留发放时规则快照。</p></div>
      <el-button v-if="auth.hasPermission('benefit:voucher:manage')" type="primary" @click="openCreate">新建代金券</el-button>
    </div>
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="代金券定义" name="definitions" />
      <el-tab-pane label="券码与绑定" name="codes" />
    </el-tabs>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="activeTab === 'definitions' ? loadDefinitions() : loadCodes()">
        <el-form-item label="关键词"><el-input v-model="keyword" clearable placeholder="名称、编码或券码" /></el-form-item>
        <el-form-item v-if="activeTab === 'codes'" label="券码状态">
          <el-select v-model="codeStatus" clearable placeholder="全部状态" style="width: 150px">
            <el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="activeTab === 'definitions'" class="data-card" shadow="never">
      <el-table v-loading="loading" :data="definitions" stripe row-key="id">
        <el-table-column prop="code" label="编码" width="130" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column label="权益" width="120"><template #default="scope"><strong>{{ benefitText(scope.row) }}</strong></template></el-table-column>
        <el-table-column label="使用门槛" width="130"><template #default="scope">{{ scope.row.minSpend > 0 ? `满${formatMoney(scope.row.minSpend)}` : '无门槛' }}</template></el-table-column>
        <el-table-column label="有效期" width="100"><template #default="scope">{{ scope.row.validDays }}天</template></el-table-column>
        <el-table-column prop="commissionRule" label="提成口径" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="170" fixed="right"><template #default="scope">
          <el-button v-if="auth.hasPermission('benefit:voucher:manage')" link type="primary" @click="editRow(scope.row)">编辑</el-button>
          <el-button v-if="scope.row.status === 'ACTIVE' && auth.hasPermission('benefit:voucher:issue')" link type="primary" @click="issueRow(scope.row)">发券</el-button>
        </template></el-table-column>
      </el-table>
    </el-card>

    <el-card v-else class="data-card" shadow="never">
      <el-table v-loading="loading" :data="codes" stripe row-key="id">
        <el-table-column prop="code" label="券码" min-width="210" />
        <el-table-column prop="voucherName" label="代金券" min-width="170" />
        <el-table-column label="权益" width="110"><template #default="scope">{{ benefitText(scope.row) }}</template></el-table-column>
        <el-table-column label="会员" min-width="150"><template #default="scope">{{ scope.row.memberName || '未绑定' }}</template></el-table-column>
        <el-table-column label="有效期至" width="170"><template #default="scope">{{ dateTime(scope.row.validUntil) }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag>{{ statusLabels[scope.row.status as VoucherCodeStatus] }}</el-tag></template></el-table-column>
        <el-table-column label="关联账单" width="120"><template #default="scope"><el-button v-if="scope.row.redeemedBillId" link type="primary" @click="$router.push(`/app/bills/${scope.row.redeemedBillId}`)">查看账单</el-button><span v-else>—</span></template></el-table-column>
        <el-table-column label="操作" width="100" fixed="right"><template #default="scope"><el-button v-if="scope.row.status === 'UNBOUND' && auth.hasPermission('benefit:voucher:issue')" link type="primary" @click="bindRow(scope.row)">绑定会员</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="definitionDialog" :title="definitionForm.id ? '编辑代金券' : '新建代金券'" width="620px">
      <el-form label-width="100px">
        <el-form-item label="券编码"><el-input v-model="definitionForm.code" :disabled="Boolean(definitionForm.id)" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="definitionForm.name" /></el-form-item>
        <el-form-item label="权益类型"><el-radio-group v-model="definitionForm.benefitType"><el-radio-button value="FIXED_AMOUNT">金额券</el-radio-button><el-radio-button value="DISCOUNT">折扣券</el-radio-button></el-radio-group></el-form-item>
        <el-form-item v-if="definitionForm.benefitType === 'FIXED_AMOUNT'" label="面额"><el-input-number v-model="definitionForm.faceAmount" :min="0.01" :precision="2" /></el-form-item>
        <el-form-item v-else label="折扣率"><el-input-number v-model="definitionForm.discountPercent" :min="1" :max="99" :precision="0" /><span class="suffix">%（例如80表示八折）</span></el-form-item>
        <el-form-item label="最低消费"><el-input-number v-model="definitionForm.minSpend" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="有效天数"><el-input-number v-model="definitionForm.validDays" :min="1" :max="3650" /></el-form-item>
        <el-form-item label="提成口径"><el-input v-model="definitionForm.commissionRule" type="textarea" placeholder="例如：按券后实收金额计算" /></el-form-item>
        <el-form-item v-if="definitionForm.id" label="状态"><el-switch v-model="definitionForm.status" active-value="ACTIVE" inactive-value="INACTIVE" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="definitionDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveDefinition">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="issueDialog" title="生成并发放券码" width="620px">
      <el-form label-width="100px">
        <el-form-item label="数量"><el-input-number v-model="issueForm.count" :min="1" :max="100" /></el-form-item>
        <el-form-item label="直接绑定">
          <el-select v-model="issueForm.memberId" filterable remote clearable :remote-method="searchMember" :loading="memberLoading" placeholder="可留空，稍后凭券码绑定" style="width: 100%">
            <el-option v-for="member in members" :key="member.id" :value="member.id" :label="`${member.fullName} · ${member.maskedMobile} · ${member.memberNo}`" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert v-if="issuedCodes.length" :title="`已生成 ${issuedCodes.length} 张券，请在券码页继续查询和绑定。`" type="success" :closable="false" />
      <template #footer><el-button @click="issueDialog = false">关闭</el-button><el-button type="primary" :loading="saving" @click="issue">确认发券</el-button></template>
    </el-dialog>

    <el-dialog v-model="bindDialog" title="券码绑定会员" width="560px">
      <p class="code-preview">{{ bindingCode?.code }} · {{ bindingCode?.voucherName }}</p>
      <el-select v-model="bindForm.memberId" filterable remote :remote-method="searchMember" :loading="memberLoading" placeholder="输入姓名、手机号或会员号" style="width: 100%">
        <el-option v-for="member in members" :key="member.id" :value="member.id" :label="`${member.fullName} · ${member.maskedMobile} · ${member.memberNo}`" />
      </el-select>
      <template #footer><el-button @click="bindDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="bind">确认绑定</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.suffix { margin-left: 10px; color: var(--el-text-color-secondary); }
.code-preview { margin: 0 0 16px; padding: 12px; border-radius: 6px; background: var(--el-fill-color-light); font-weight: 600; }
</style>
