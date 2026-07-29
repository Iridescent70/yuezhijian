<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getNotification,
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notification'
import type { NotificationItem, NotificationMessageType } from '@/types/api'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const rows = ref<NotificationItem[]>([])
const total = ref(0)
const detail = ref<NotificationItem>()
const detailVisible = ref(false)
const dateRange = ref<string[]>([])
const query = reactive<{
  messageType?: NotificationMessageType
  readStatus?: 'READ' | 'UNREAD'
  page: number
  size: number
}>({ page: 1, size: 20 })
const typeLabels: Record<NotificationMessageType, string> = {
  ANNOUNCEMENT: '通知公告', APPOINTMENT: '预约提醒', CARD_EXPIRY: '次卡到期', BIRTHDAY: '生日提醒',
  BALANCE_LOW: '余额不足', CONSUMPTION: '消费通知', SYSTEM: '系统通知', DAILY_REPORT: '日报推送',
  BILL_ALERT: '异常账单', RECONCILIATION: '对账提醒', BILL_REVERSAL: '账单冲销',
  BALANCE_REVERSAL: '储值冲销', CARD_REVERSAL: '次卡冲销',
}

async function load() {
  loading.value = true
  try {
    const result = await getNotifications({
      ...query,
      publishedFrom: dateRange.value[0],
      publishedTo: dateRange.value[1],
    })
    rows.value = result.items
    total.value = result.total
    query.page = result.page
    query.size = result.size
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '消息加载失败')
  } finally {
    loading.value = false
  }
}

function search() { query.page = 1; void load() }
function reset() {
  Object.assign(query, { messageType: undefined, readStatus: undefined, page: 1 })
  dateRange.value = []
  void load()
}

async function open(value: unknown) {
  const row = value as NotificationItem
  try {
    detail.value = row.read ? await getNotification(row.id) : await markNotificationRead(row.id)
    const index = rows.value.findIndex(item => item.id === row.id)
    if (index >= 0 && detail.value) rows.value[index] = detail.value
    detailVisible.value = true
    window.dispatchEvent(new Event('notification-read'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '消息详情加载失败')
  }
}

async function readAll() {
  try {
    const count = await markAllNotificationsRead(query.messageType)
    ElMessage.success(count ? `已标记${count}条消息` : '当前没有未读消息')
    window.dispatchEvent(new Event('notification-read'))
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '全部已读失败')
  }
}

async function openBusiness() {
  const route = detail.value?.route
  if (!route) return
  detailVisible.value = false
  await router.push(route)
}

function dateTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '—' }
function changePage(page: number) { query.page = page; void load() }
function changeSize(size: number) { query.size = size; query.page = 1; void load() }
onMounted(async () => {
  await load()
  const id = Number(route.query.notificationId)
  if (Number.isSafeInteger(id) && id > 0) await open({ id, read: false } as NotificationItem)
})
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>消息中心</h1><p>展示当前门店范围内的公告和业务通知；切换门店后消息范围同步变化。</p></div>
      <el-button type="primary" plain @click="readAll">全部已读</el-button>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="search">
        <el-form-item label="消息类型"><el-select v-model="query.messageType" clearable placeholder="全部" style="width: 145px"><el-option v-for="(label, value) in typeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="已读状态"><el-select v-model="query.readStatus" clearable placeholder="全部" style="width: 120px"><el-option label="未读" value="UNREAD" /><el-option label="已读" value="READ" /></el-select></el-form-item>
        <el-form-item label="发布时间"><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 250px" /></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id" @row-click="open">
        <el-table-column label="状态" width="65"><template #default="scope"><span class="read-dot" :class="{ unread: !scope.row.read }" :title="scope.row.read ? '已读' : '未读'" /></template></el-table-column>
        <el-table-column label="类型" width="105"><template #default="scope"><el-tag :type="scope.row.messageType.includes('REVERSAL') ? 'warning' : scope.row.messageType === 'ANNOUNCEMENT' ? 'primary' : 'info'">{{ typeLabels[scope.row.messageType as NotificationMessageType] }}</el-tag></template></el-table-column>
        <el-table-column label="标题" min-width="260"><template #default="scope"><strong :class="{ 'unread-title': !scope.row.read }">{{ scope.row.title }}</strong><el-tag v-if="scope.row.pinned" size="small" type="danger" class="pin-tag">置顶</el-tag></template></el-table-column>
        <el-table-column prop="body" label="内容" min-width="320" show-overflow-tooltip />
        <el-table-column label="发布时间" width="175"><template #default="scope">{{ dateTime(scope.row.publishedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="75" fixed="right"><template #default="scope"><el-button link type="primary" @click.stop="open(scope.row)">查看</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && rows.length === 0" description="当前没有消息" />
      <el-pagination :current-page="query.page" :page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @update:current-page="changePage" @update:page-size="changeSize" />
    </el-card>

    <el-drawer v-model="detailVisible" title="消息详情" size="600px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="类型">{{ typeLabels[detail.messageType] }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ dateTime(detail.publishedAt) }}</el-descriptions-item>
          <el-descriptions-item label="消息编号">{{ detail.notificationNo }}</el-descriptions-item>
        </el-descriptions>
        <h2 class="detail-title">{{ detail.title }}</h2>
        <div class="message-body">{{ detail.body }}</div>
        <div v-if="detail.route" class="drawer-actions"><el-button type="primary" @click="openBusiness">查看相关业务</el-button></div>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.read-dot { display: inline-block; width: 9px; height: 9px; border-radius: 50%; background: #c7c7c7; }
.read-dot.unread { background: #e65f78; box-shadow: 0 0 0 4px rgb(230 95 120 / 12%); }
.unread-title { color: #713f51; }
.pin-tag { margin-left: 8px; }
.detail-title { margin: 24px 0 14px; font-size: 22px; }
.message-body { min-height: 160px; line-height: 1.85; white-space: pre-wrap; }
.drawer-actions { margin-top: 24px; }
</style>
