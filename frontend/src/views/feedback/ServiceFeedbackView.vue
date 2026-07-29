<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getServiceFeedback, getServiceFeedbackDetail, handleServiceFeedback } from '@/api/feedback'
import { getEmployees } from '@/api/masterData'
import { getStores } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'
import type {
  EmployeeSummary,
  FeedbackActionType,
  FeedbackDetail,
  FeedbackStatus,
  FeedbackSummary,
  HandleFeedbackPayload,
  StoreSummary,
} from '@/types/api'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const rows = ref<FeedbackSummary[]>([])
const stores = ref<StoreSummary[]>([])
const employees = ref<EmployeeSummary[]>([])
const detail = ref<FeedbackDetail>()
const drawerVisible = ref(false)
const actionVisible = ref(false)
const actionTitle = ref('处理服务反馈')
const filters = reactive({
  storeId: undefined as number | undefined,
  handlerId: undefined as number | undefined,
  score: undefined as number | undefined,
  status: '' as '' | FeedbackStatus,
  keyword: '',
})
const actionForm = reactive<HandleFeedbackPayload>({ action: 'ASSIGN', handlerId: undefined, content: '', result: '' })

const statusMap: Record<FeedbackStatus, { label: string; type: 'danger' | 'warning' | 'success' | 'info' }> = {
  OPEN: { label: '待处理', type: 'danger' },
  PROCESSING: { label: '处理中', type: 'warning' },
  RESOLVED: { label: '已解决', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info' },
}
const actionName: Record<FeedbackActionType, string> = {
  CREATED: '创建反馈', ASSIGNED: '分配负责人', NOTE: '处理备注',
  RESOLVED: '标记解决', CLOSED: '关闭反馈', REOPENED: '重新打开',
}

async function loadEmployees() {
  employees.value = await getEmployees({ storeId: filters.storeId })
  if (filters.handlerId && !employees.value.some(item => item.id === filters.handlerId)) filters.handlerId = undefined
}

async function load() {
  loading.value = true
  try {
    rows.value = await getServiceFeedback({
      storeId: filters.storeId,
      handlerId: filters.handlerId,
      score: filters.score,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '服务反馈加载失败')
  } finally {
    loading.value = false
  }
}

async function open(row: FeedbackSummary) {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getServiceFeedbackDetail(row.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '服务反馈详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function openRow(row: unknown) { void open(row as FeedbackSummary) }

function beginAction(action: HandleFeedbackPayload['action']) {
  if (!detail.value) return
  actionForm.action = action
  actionForm.handlerId = action === 'ASSIGN' || action === 'REOPEN'
    ? detail.value.feedback.handlerId
    : undefined
  actionForm.content = ''
  actionForm.result = ''
  actionTitle.value = ({
    ASSIGN: '分配负责人', NOTE: '添加处理备注', RESOLVE: '标记已解决',
    CLOSE: '关闭服务反馈', REOPEN: '重新打开反馈',
  } as Record<string, string>)[action]
  actionVisible.value = true
}

async function submitAction() {
  if (!detail.value) return
  if (['ASSIGN', 'REOPEN'].includes(actionForm.action) && !actionForm.handlerId) return ElMessage.warning('请选择负责人')
  if (['NOTE', 'REOPEN'].includes(actionForm.action) && !actionForm.content?.trim()) return ElMessage.warning('请填写处理说明')
  if (actionForm.action === 'RESOLVE' && !actionForm.result?.trim()) return ElMessage.warning('请填写处理结果')
  saving.value = true
  try {
    detail.value = await handleServiceFeedback(detail.value.feedback.id, {
      action: actionForm.action,
      handlerId: actionForm.handlerId,
      content: actionForm.content?.trim() || undefined,
      result: actionForm.result?.trim() || undefined,
    })
    actionVisible.value = false
    ElMessage.success('服务反馈已更新')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '服务反馈处理失败')
  } finally {
    saving.value = false
  }
}

function dateTime(value?: string) { return value?.replace('T', ' ').slice(0, 16) ?? '—' }
function openMember() { if (detail.value) void router.push(`/app/members/${detail.value.feedback.memberId}`) }
function openBill() { if (detail.value) void router.push(`/app/bills/${detail.value.feedback.billId}`) }
function openVisit() { void router.push('/app/service/visits') }

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
      <div><h1>服务反馈</h1><p>回访中标记的客诉自动进入这里；处理过程逐条保留，解决后复核关闭。</p></div>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="反馈查询"><el-input v-model="filters.keyword" clearable placeholder="反馈号、账单号或会员" style="width: 220px" /></el-form-item>
        <el-form-item label="门店"><el-select v-model="filters.storeId" clearable placeholder="全部门店" style="width: 170px" @change="loadEmployees"><el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="负责人"><el-select v-model="filters.handlerId" clearable filterable placeholder="全部" style="width: 140px"><el-option v-for="item in employees" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="评分"><el-select v-model="filters.score" clearable placeholder="全部" style="width: 100px"><el-option v-for="score in 5" :key="score" :label="`${score}分`" :value="score" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px"><el-option v-for="(item, code) in statusMap" :key="code" :label="item.label" :value="code" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id" @row-click="openRow">
        <el-table-column label="反馈单" min-width="200"><template #default="scope"><button class="member-link" type="button" @click.stop="openRow(scope.row)"><strong>{{ scope.row.feedbackNo }}</strong><small>{{ dateTime(scope.row.createdAt) }}</small></button></template></el-table-column>
        <el-table-column label="会员" min-width="150"><template #default="scope"><strong>{{ scope.row.memberName }}</strong><br><small class="muted-text">{{ scope.row.maskedMobile }}</small></template></el-table-column>
        <el-table-column prop="storeName" label="门店" min-width="150" />
        <el-table-column prop="billNo" label="关联账单" min-width="170" />
        <el-table-column label="满意度" width="110"><template #default="scope"><el-rate v-if="scope.row.score" :model-value="scope.row.score" disabled /><span v-else class="muted-text">未评分</span></template></el-table-column>
        <el-table-column prop="handlerName" label="负责人" width="110"><template #default="scope">{{ scope.row.handlerName || '待分配' }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="statusMap[scope.row.status as FeedbackStatus].type">{{ statusMap[scope.row.status as FeedbackStatus].label }}</el-tag></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && rows.length === 0" description="当前没有服务反馈" />
    </el-card>

    <el-drawer v-model="drawerVisible" title="服务反馈详情" size="720px">
      <div v-loading="detailLoading" class="feedback-detail">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="反馈单号">{{ detail.feedback.feedbackNo }}</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag :type="statusMap[detail.feedback.status].type">{{ statusMap[detail.feedback.status].label }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="会员"><el-button link type="primary" @click="openMember">{{ detail.feedback.memberName }} {{ detail.feedback.maskedMobile }}</el-button></el-descriptions-item>
            <el-descriptions-item label="账单"><el-button link type="primary" @click="openBill">{{ detail.feedback.billNo }}</el-button></el-descriptions-item>
            <el-descriptions-item label="满意度"><el-rate v-if="detail.feedback.score" :model-value="detail.feedback.score" disabled /><span v-else class="muted-text">未评分</span></el-descriptions-item>
            <el-descriptions-item label="负责人">{{ detail.feedback.handlerName || '待分配' }}</el-descriptions-item>
            <el-descriptions-item label="客诉内容" :span="2">{{ detail.feedback.content }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.feedback.handleResult" label="处理结果" :span="2">{{ detail.feedback.handleResult }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="auth.hasPermission('visit:feedback:manage')" class="drawer-actions">
            <el-button v-if="['OPEN', 'PROCESSING'].includes(detail.feedback.status)" @click="beginAction('ASSIGN')">{{ detail.feedback.handlerId ? '重新分配' : '分配负责人' }}</el-button>
            <el-button v-if="['PROCESSING', 'RESOLVED'].includes(detail.feedback.status)" @click="beginAction('NOTE')">添加备注</el-button>
            <el-button v-if="detail.feedback.status === 'PROCESSING'" type="primary" @click="beginAction('RESOLVE')">标记解决</el-button>
            <el-button v-if="detail.feedback.status === 'RESOLVED'" type="success" @click="beginAction('CLOSE')">关闭反馈</el-button>
            <el-button v-if="['RESOLVED', 'CLOSED'].includes(detail.feedback.status)" type="warning" @click="beginAction('REOPEN')">重新打开</el-button>
            <el-button @click="openVisit">查看回访任务</el-button>
          </div>

          <h3>处理记录</h3>
          <el-timeline>
            <el-timeline-item v-for="item in [...detail.actions].reverse()" :key="item.id" :timestamp="dateTime(item.createdAt)" placement="top" :type="item.actionType === 'RESOLVED' || item.actionType === 'CLOSED' ? 'success' : item.actionType === 'REOPENED' ? 'warning' : 'primary'">
              <strong>{{ actionName[item.actionType] }}</strong>
              <span v-if="item.handlerName"> · {{ item.handlerName }}</span>
              <p v-if="item.content">{{ item.content }}</p>
              <small class="muted-text">操作人：{{ item.createdByName }}</small>
            </el-timeline-item>
          </el-timeline>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="actionVisible" :title="actionTitle" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item v-if="['ASSIGN', 'REOPEN'].includes(actionForm.action)" label="负责人" required><el-select v-model="actionForm.handlerId" filterable placeholder="选择本店员工" style="width: 100%"><el-option v-for="item in employees" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item v-if="actionForm.action === 'RESOLVE'" label="处理结果" required><el-input v-model="actionForm.result" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item>
        <el-form-item v-else :label="['NOTE', 'REOPEN'].includes(actionForm.action) ? '处理说明' : '备注'"><el-input v-model="actionForm.content" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="actionVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitAction">确认</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.feedback-detail h3 { margin: 24px 0 12px; }
.feedback-detail p { margin: 8px 0; line-height: 1.6; }
.feedback-detail :deep(.el-rate) { height: 20px; }
</style>
