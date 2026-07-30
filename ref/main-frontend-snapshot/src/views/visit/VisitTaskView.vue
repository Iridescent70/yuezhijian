<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addVisitRecord, completeVisitTask, getVisitTask, getVisitTasks } from '@/api/visit'
import { getEmployees } from '@/api/masterData'
import { getStores } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'
import type {
  EmployeeSummary,
  StoreSummary,
  VisitParticipantItem,
  VisitRecordPayload,
  VisitResultCode,
  VisitTaskDetail,
  VisitTaskStatus,
  VisitTaskSummary,
} from '@/types/api'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const rows = ref<VisitTaskSummary[]>([])
const stores = ref<StoreSummary[]>([])
const employees = ref<EmployeeSummary[]>([])
const detail = ref<VisitTaskDetail>()
const drawerVisible = ref(false)
const recordVisible = ref(false)
const selectedParticipant = ref<VisitParticipantItem>()
const conclusion = ref('')
const filters = reactive({
  storeId: undefined as number | undefined,
  employeeId: undefined as number | undefined,
  status: '' as '' | VisitTaskStatus,
  dueDate: '',
  keyword: '',
})
const recordForm = reactive<VisitRecordPayload>({
  employeeId: 0,
  resultCode: 'CONTACTED',
  satisfactionScore: 5,
  complaintFlag: false,
  content: '',
  nextFollowAt: undefined,
})

const resultOptions: Array<{ value: VisitResultCode; label: string }> = [
  { value: 'CONTACTED', label: '已联系' },
  { value: 'NO_ANSWER', label: '未接通' },
  { value: 'FOLLOW_UP', label: '继续跟进' },
  { value: 'DECLINED', label: '拒绝回访' },
]
const statusMap: Record<VisitTaskStatus, { label: string; type: 'warning' | 'danger' | 'success' | 'info' }> = {
  PENDING: { label: '待回访', type: 'warning' },
  OVERDUE: { label: '已逾期', type: 'danger' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' },
}
const allParticipantsCompleted = computed(() => detail.value?.participants.every(item => item.status === 'COMPLETED') === true)

async function loadEmployees() {
  employees.value = await getEmployees({ storeId: filters.storeId })
  if (filters.employeeId && !employees.value.some(item => item.id === filters.employeeId)) filters.employeeId = undefined
}

async function load() {
  loading.value = true
  try {
    rows.value = await getVisitTasks({
      storeId: filters.storeId,
      employeeId: filters.employeeId,
      status: filters.status || undefined,
      dueDate: filters.dueDate || undefined,
      keyword: filters.keyword || undefined,
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '回访任务加载失败')
  } finally {
    loading.value = false
  }
}

async function openTask(row: VisitTaskSummary) {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getVisitTask(row.id)
    conclusion.value = detail.value.task.conclusion ?? ''
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '回访详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function openTaskRow(row: unknown) { void openTask(row as VisitTaskSummary) }
function openParticipantRow(row: unknown) { openRecord(row as VisitParticipantItem) }

function openRecord(participant: VisitParticipantItem) {
  selectedParticipant.value = participant
  recordForm.employeeId = participant.employeeId ?? 0
  recordForm.resultCode = 'CONTACTED'
  recordForm.satisfactionScore = 5
  recordForm.complaintFlag = false
  recordForm.content = ''
  recordForm.nextFollowAt = undefined
  recordVisible.value = true
}

function changeResult() {
  if (recordForm.resultCode === 'CONTACTED') {
    recordForm.satisfactionScore = recordForm.satisfactionScore ?? 5
    recordForm.nextFollowAt = undefined
  } else {
    recordForm.satisfactionScore = undefined
    if (recordForm.resultCode === 'DECLINED') recordForm.nextFollowAt = undefined
  }
}

async function saveRecord() {
  if (!detail.value || !recordForm.employeeId) return ElMessage.warning('请选择回访员工')
  if (recordForm.resultCode === 'CONTACTED' && !recordForm.satisfactionScore) return ElMessage.warning('请填写满意度')
  if (['NO_ANSWER', 'FOLLOW_UP'].includes(recordForm.resultCode) && !recordForm.nextFollowAt) return ElMessage.warning('请选择下次跟进时间')
  if (recordForm.complaintFlag && !recordForm.content?.trim()) return ElMessage.warning('标记客诉时请填写情况说明')
  saving.value = true
  try {
    detail.value = await addVisitRecord(detail.value.task.id, {
      ...recordForm,
      content: recordForm.content?.trim() || undefined,
    })
    recordVisible.value = false
    ElMessage.success('回访记录已保存')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '回访记录保存失败')
  } finally {
    saving.value = false
  }
}

async function saveConclusion() {
  if (!detail.value || !conclusion.value.trim()) return ElMessage.warning('请填写回访总结')
  saving.value = true
  try {
    detail.value = await completeVisitTask(detail.value.task.id, conclusion.value.trim())
    ElMessage.success('回访总结已保存')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '回访任务完成失败')
  } finally {
    saving.value = false
  }
}

function dateTime(value?: string) { return value?.replace('T', ' ').slice(0, 16) ?? '—' }
function resultName(code: VisitResultCode) { return resultOptions.find(item => item.value === code)?.label ?? code }
function openMember() { if (detail.value) void router.push(`/app/members/${detail.value.task.memberId}`) }
function openBill() { if (detail.value) void router.push(`/app/bills/${detail.value.task.billId}`) }

onMounted(async () => {
  try {
    stores.value = await getStores()
    filters.storeId = auth.user?.currentStoreId ?? auth.user?.stores[0]?.id
    await loadEmployees()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '基础资料加载失败')
  }
  await load()
})
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>回访管理</h1><p>会员账单结算后自动进入待办；多位服务技师分别登记，未接通可安排下次跟进。</p></div>
    </div>

    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="任务查询"><el-input v-model="filters.keyword" clearable placeholder="任务号、账单号或会员" style="width: 220px" /></el-form-item>
        <el-form-item label="门店"><el-select v-model="filters.storeId" clearable placeholder="全部门店" style="width: 170px" @change="loadEmployees"><el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="技师"><el-select v-model="filters.employeeId" clearable filterable placeholder="全部技师" style="width: 150px"><el-option v-for="item in employees" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px"><el-option v-for="(item, code) in statusMap" :key="code" :label="item.label" :value="code" /></el-select></el-form-item>
        <el-form-item label="应回访日期"><el-date-picker v-model="filters.dueDate" type="date" value-format="YYYY-MM-DD" clearable /></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id" @row-click="openTaskRow">
        <el-table-column label="回访任务" min-width="200"><template #default="scope"><button class="member-link" type="button" @click.stop="openTaskRow(scope.row)"><strong>{{ scope.row.taskNo }}</strong><small>应回访 {{ dateTime(scope.row.dueAt) }}</small></button></template></el-table-column>
        <el-table-column label="会员" min-width="150"><template #default="scope"><strong>{{ scope.row.customerName }}</strong><br><small class="muted-text">{{ scope.row.maskedMobile }}</small></template></el-table-column>
        <el-table-column prop="storeName" label="门店" min-width="150" />
        <el-table-column label="账单" min-width="170"><template #default="scope"><span>{{ scope.row.billNo }}</span><br><small class="muted-text">结算 {{ dateTime(scope.row.settledAt) }}</small></template></el-table-column>
        <el-table-column label="技师进度" width="110"><template #default="scope">{{ scope.row.completedCount }}/{{ scope.row.participantCount }}</template></el-table-column>
        <el-table-column label="客诉" width="80"><template #default="scope"><el-tag v-if="scope.row.complaintFlag" type="danger">客诉</el-tag><span v-else>—</span></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="statusMap[scope.row.status as VisitTaskStatus].type">{{ statusMap[scope.row.status as VisitTaskStatus].label }}</el-tag></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && rows.length === 0" description="当前筛选条件下没有回访任务" />
    </el-card>

    <el-drawer v-model="drawerVisible" title="回访任务详情" size="760px">
      <div v-loading="detailLoading" class="visit-detail">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="任务号">{{ detail.task.taskNo }}</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag :type="statusMap[detail.task.status].type">{{ statusMap[detail.task.status].label }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="会员"><el-button link type="primary" @click="openMember">{{ detail.task.customerName }} {{ detail.task.maskedMobile }}</el-button></el-descriptions-item>
            <el-descriptions-item label="账单"><el-button link type="primary" @click="openBill">{{ detail.task.billNo }}</el-button></el-descriptions-item>
            <el-descriptions-item label="门店">{{ detail.task.storeName }}</el-descriptions-item>
            <el-descriptions-item label="应回访时间">{{ dateTime(detail.task.dueAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.task.cancelReason" label="取消原因" :span="2">{{ detail.task.cancelReason }}</el-descriptions-item>
          </el-descriptions>

          <h3>技师回访进度</h3>
          <el-table :data="detail.participants" border row-key="id">
            <el-table-column prop="employeeName" label="服务技师" width="130" />
            <el-table-column prop="serviceSummary" label="服务项目" min-width="220" />
            <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 'COMPLETED' ? 'success' : 'warning'">{{ scope.row.status === 'COMPLETED' ? '已完成' : '待处理' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="110"><template #default="scope"><el-button v-if="scope.row.status === 'PENDING' && !['COMPLETED', 'CANCELLED'].includes(detail.task.status) && auth.hasPermission('visit:task:manage')" link type="primary" @click="openParticipantRow(scope.row)">登记回访</el-button></template></el-table-column>
          </el-table>

          <h3>回访记录</h3>
          <el-timeline v-if="detail.records.length">
            <el-timeline-item v-for="item in [...detail.records].reverse()" :key="item.id" :timestamp="dateTime(item.createdAt)" placement="top" :type="item.complaintFlag ? 'danger' : item.resultCode === 'CONTACTED' ? 'success' : 'warning'">
              <div class="record-title"><strong>{{ item.employeeName }} · {{ resultName(item.resultCode) }}</strong><el-rate v-if="item.satisfactionScore" :model-value="item.satisfactionScore" disabled /></div>
              <p v-if="item.content">{{ item.content }}</p>
              <p v-if="item.nextFollowAt" class="muted-text">下次跟进：{{ dateTime(item.nextFollowAt) }}</p>
              <el-tag v-if="item.complaintFlag" type="danger" size="small">客诉</el-tag>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="尚未登记回访记录" :image-size="70" />

          <template v-if="allParticipantsCompleted && auth.hasPermission('visit:task:manage')">
            <h3>回访总结</h3>
            <el-input v-model="conclusion" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="填写本次回访结论和后续处理说明" />
            <div class="drawer-actions"><el-button type="primary" :loading="saving" @click="saveConclusion">保存总结</el-button></div>
          </template>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="recordVisible" title="登记回访" width="560px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="回访员工" required>
          <el-select v-if="!selectedParticipant?.employeeId" v-model="recordForm.employeeId" filterable placeholder="选择本店员工" style="width: 100%"><el-option v-for="item in employees" :key="item.id" :label="item.name" :value="item.id" /></el-select>
          <el-input v-else :model-value="selectedParticipant.employeeName" disabled />
        </el-form-item>
        <el-form-item label="服务项目"><el-input :model-value="selectedParticipant?.serviceSummary" disabled /></el-form-item>
        <el-form-item label="回访结果" required><el-radio-group v-model="recordForm.resultCode" @change="changeResult"><el-radio-button v-for="item in resultOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button></el-radio-group></el-form-item>
        <el-form-item v-if="recordForm.resultCode === 'CONTACTED'" label="满意度" required><el-rate v-model="recordForm.satisfactionScore" show-score /></el-form-item>
        <el-form-item v-if="['NO_ANSWER', 'FOLLOW_UP'].includes(recordForm.resultCode)" label="下次跟进时间" required><el-date-picker v-model="recordForm.nextFollowAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择下次跟进时间" style="width: 100%" /></el-form-item>
        <el-form-item><el-checkbox v-model="recordForm.complaintFlag">标记为客诉，需要后续跟踪</el-checkbox></el-form-item>
        <el-form-item :label="recordForm.complaintFlag ? '情况说明（必填）' : '回访内容'"><el-input v-model="recordForm.content" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="recordVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRecord">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.visit-detail h3 { margin: 24px 0 12px; }
.record-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.record-title :deep(.el-rate) { height: 20px; }
.visit-detail p { margin: 8px 0; line-height: 1.6; }
</style>
