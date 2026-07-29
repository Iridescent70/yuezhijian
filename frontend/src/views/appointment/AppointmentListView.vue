<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAppointment,
  getAppointmentCancelReasons,
  getAppointments,
  transitionAppointment,
} from '@/api/appointment'
import { useAuthStore } from '@/stores/auth'
import { createBillFromAppointment } from '@/api/trade'
import type {
  AppointmentDetail,
  AppointmentStatus,
  AppointmentSummary,
  CancelReasonOption,
} from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const detailLoading = ref(false)
const actionLoading = ref(false)
const drawerVisible = ref(false)
const reasonDialogVisible = ref(false)
const appointments = ref<AppointmentSummary[]>([])
const detail = ref<AppointmentDetail | null>(null)
const reasons = ref<CancelReasonOption[]>([])
const today = localDate(new Date())
const weekEnd = localDate(new Date(Date.now() + 7 * 24 * 60 * 60 * 1000))
const defaultStore = auth.user?.currentStoreId ?? auth.user?.stores[0]?.id
const filters = reactive({ storeId: defaultStore as number | undefined, dates: [today, weekEnd], status: '' })
const cancelForm = reactive({ action: 'cancel' as 'cancel' | 'no-show', reasonCode: '', note: '' })

const statusMap: Record<AppointmentStatus, { label: string; type: 'success' | 'warning' | 'info' | 'danger' | 'primary' }> = {
  PENDING_CONFIRM: { label: '待确认', type: 'warning' },
  CONFIRMED: { label: '已确认', type: 'primary' },
  ARRIVED: { label: '已到店', type: 'success' },
  SERVING: { label: '服务中', type: 'success' },
  COMPLETED: { label: '已完成', type: 'info' },
  CANCELLED: { label: '已取消', type: 'info' },
  NO_SHOW: { label: '已爽约', type: 'danger' },
}

const nextActions = computed(() => {
  const status = detail.value?.appointment.status
  if (status === 'PENDING_CONFIRM') return [{ action: 'confirm', label: '确认预约', type: 'primary' as const }, { action: 'cancel', label: '取消', type: 'default' as const }]
  if (status === 'CONFIRMED') return [{ action: 'arrive', label: '确认到店', type: 'primary' as const }, { action: 'no-show', label: '记为爽约', type: 'danger' as const }, { action: 'cancel', label: '取消', type: 'default' as const }]
  if (status === 'ARRIVED') return [{ action: 'create-bill', label: '转为账单', type: 'success' as const }, { action: 'start', label: '开始服务', type: 'primary' as const }, { action: 'cancel', label: '取消', type: 'default' as const }]
  if (status === 'SERVING') return [{ action: 'create-bill', label: '转为账单', type: 'success' as const }, { action: 'complete', label: '完成服务', type: 'primary' as const }]
  if (status === 'COMPLETED') return [{ action: 'create-bill', label: '查看账单', type: 'success' as const }]
  return []
})

async function load() {
  if (!filters.storeId) return
  loading.value = true
  try {
    appointments.value = await getAppointments({
      storeId: filters.storeId,
      startDate: filters.dates[0],
      endDate: filters.dates[1],
      status: filters.status || undefined,
    })
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '预约加载失败') }
  finally { loading.value = false }
}

async function openDetail(id: number) {
  drawerVisible.value = true
  detailLoading.value = true
  try { detail.value = await getAppointment(id) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '预约详情加载失败') }
  finally { detailLoading.value = false }
}

async function runAction(action: string) {
  if (!detail.value) return
  if (action === 'create-bill') {
    actionLoading.value = true
    try {
      const bill = await createBillFromAppointment(detail.value.appointment.id)
      await router.push(`/app/bills/${bill.id}`)
    } catch (error) { ElMessage.error(error instanceof Error ? error.message : '预约转账单失败') }
    finally { actionLoading.value = false }
    return
  }
  if (action === 'cancel' || action === 'no-show') {
    cancelForm.action = action
    cancelForm.reasonCode = action === 'no-show' ? 'CUSTOMER_NO_SHOW' : ''
    cancelForm.note = ''
    reasonDialogVisible.value = true
    return
  }
  const labels: Record<string, string> = { confirm: '确认预约', arrive: '确认客户到店', start: '开始服务', complete: '完成服务' }
  await ElMessageBox.confirm(`确定要${labels[action]}吗？`, '预约状态确认', { type: 'warning' })
  await submitTransition(action as 'confirm' | 'arrive' | 'start' | 'complete', {})
}

async function submitCancel() {
  const reason = reasons.value.find((item) => item.code === cancelForm.reasonCode)
  if (!reason) { ElMessage.warning('请选择原因'); return }
  if (reason.requiresNote && !cancelForm.note.trim()) { ElMessage.warning('该原因必须填写说明'); return }
  await submitTransition(cancelForm.action, { reasonCode: cancelForm.reasonCode, note: cancelForm.note || undefined })
  reasonDialogVisible.value = false
}

async function submitTransition(
  action: 'confirm' | 'arrive' | 'start' | 'complete' | 'cancel' | 'no-show',
  extra: { reasonCode?: string; note?: string },
) {
  if (!detail.value) return
  actionLoading.value = true
  try {
    detail.value = await transitionAppointment(detail.value.appointment.id, action, {
      version: detail.value.appointment.version,
      personCount: action === 'arrive' ? detail.value.appointment.personCount : undefined,
      ...extra,
    })
    ElMessage.success('预约状态已更新')
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '预约操作失败') }
  finally { actionLoading.value = false }
}

function formatDateTime(value: string) { return value.replace('T', ' ').slice(0, 16) }
function localDate(value: Date) {
  const offset = value.getTimezoneOffset() * 60_000
  return new Date(value.getTime() - offset).toISOString().slice(0, 10)
}

onMounted(async () => {
  try { reasons.value = await getAppointmentCancelReasons() } catch { reasons.value = [] }
  await load()
})
</script>

<template>
  <section class="page-content appointment-list-page">
    <div class="section-title-row">
      <div><h1>预约管理</h1><p>查看门店排期，处理确认、到店、服务、取消和爽约。</p></div>
      <el-button v-if="auth.hasPermission('appointment:appointment:create')" type="primary" @click="router.push('/app/appointments/new')">新建预约</el-button>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline>
        <el-form-item label="门店"><el-select v-model="filters.storeId" class="master-filter-select"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="预约日期"><el-date-picker v-model="filters.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部状态" class="master-filter-select"><el-option v-for="(item, code) in statusMap" :key="code" :label="item.label" :value="code" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="appointments" stripe row-key="id">
        <el-table-column label="预约时间" width="170"><template #default="scope"><strong>{{ formatDateTime(scope.row.startAt) }}</strong><br><small class="muted-text">至 {{ formatDateTime(scope.row.endAt).slice(11) }}</small></template></el-table-column>
        <el-table-column label="客户" min-width="160"><template #default="scope"><strong>{{ scope.row.customerName }}</strong><br><small class="muted-text">{{ scope.row.maskedMobile ?? '—' }}</small></template></el-table-column>
        <el-table-column prop="serviceNames" label="预约项目" min-width="220" show-overflow-tooltip />
        <el-table-column prop="employeeName" label="技师" width="120" />
        <el-table-column prop="workstationName" label="工位" width="130" />
        <el-table-column prop="personCount" label="人数" width="75" align="center" />
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="statusMap[scope.row.status as AppointmentStatus].type">{{ statusMap[scope.row.status as AppointmentStatus].label }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="100" fixed="right"><template #default="scope"><el-button link type="primary" @click="openDetail(scope.row.id)">查看处理</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" title="预约详情" size="580px">
      <div v-loading="detailLoading" class="appointment-detail-drawer">
        <template v-if="detail">
          <div class="appointment-detail-head"><div><strong>{{ detail.appointment.customerName }}</strong><span>{{ detail.appointment.appointmentNo }}</span></div><el-tag :type="statusMap[detail.appointment.status].type">{{ statusMap[detail.appointment.status].label }}</el-tag></div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="预约时间">{{ formatDateTime(detail.appointment.startAt) }}</el-descriptions-item><el-descriptions-item label="结束时间">{{ formatDateTime(detail.appointment.endAt) }}</el-descriptions-item><el-descriptions-item label="技师">{{ detail.appointment.employeeName }}</el-descriptions-item><el-descriptions-item label="工位">{{ detail.appointment.workstationName }}</el-descriptions-item><el-descriptions-item label="门店">{{ detail.appointment.storeName }}</el-descriptions-item><el-descriptions-item label="人数">{{ detail.appointment.personCount }}</el-descriptions-item><el-descriptions-item label="备注" :span="2">{{ detail.appointment.note ?? '—' }}</el-descriptions-item>
          </el-descriptions>
          <h3>预约项目</h3>
          <el-table :data="detail.services" size="small"><el-table-column prop="serviceName" label="项目" /><el-table-column prop="durationMinutes" label="时长" width="80" /><el-table-column label="价格" width="110" align="right"><template #default="scope">{{ formatMoney(scope.row.price) }}</template></el-table-column></el-table>
          <h3>状态记录</h3>
          <el-timeline><el-timeline-item v-for="item in [...detail.history].reverse()" :key="item.id" :timestamp="formatDateTime(item.occurredAt)" placement="top"><strong>{{ statusMap[item.toStatus].label }}</strong><p v-if="item.reasonCode || item.note" class="muted-text">{{ item.reasonCode }} {{ item.note }}</p></el-timeline-item></el-timeline>
          <div v-if="nextActions.length" class="drawer-actions"><el-button v-for="item in nextActions" v-show="item.action === 'create-bill' ? auth.hasPermission('trade:bill:create') : auth.hasPermission('appointment:appointment:manage')" :key="item.action" :type="item.type" :loading="actionLoading" @click="runAction(item.action)">{{ item.label }}</el-button></div>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="reasonDialogVisible" :title="cancelForm.action === 'no-show' ? '记录爽约' : '取消预约'" width="500px">
      <el-form label-width="90px"><el-form-item label="原因" required><el-select v-model="cancelForm.reasonCode" class="dialog-full-control"><el-option v-for="item in reasons" :key="item.code" :label="item.name" :value="item.code" /></el-select></el-form-item><el-form-item label="说明"><el-input v-model="cancelForm.note" type="textarea" :rows="3" maxlength="500" /></el-form-item></el-form>
      <template #footer><el-button @click="reasonDialogVisible = false">返回</el-button><el-button type="danger" :loading="actionLoading" @click="submitCancel">确认提交</el-button></template>
    </el-dialog>
  </section>
</template>
