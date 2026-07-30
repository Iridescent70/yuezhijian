<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelJob, createExport, downloadJobResult, getJob, getJobs } from '@/api/jobs'
import { useAuthStore } from '@/stores/auth'
import type { AsyncJobItem, AsyncJobStatus } from '@/types/api'

const auth = useAuthStore()
const loading = ref(false)
const creating = ref(false)
const rows = ref<AsyncJobItem[]>([])
const total = ref(0)
const detail = ref<AsyncJobItem>()
const detailVisible = ref(false)
const query = reactive({ status: '' as '' | AsyncJobStatus, page: 1, size: 20 })
const exportForm = reactive({
  exportType: (auth.hasPermission('member:member:export') ? 'MEMBER'
    : auth.hasPermission('catalog:service:export') ? 'SERVICE_CATALOG'
      : auth.hasPermission('catalog:product:export') ? 'PRODUCT_CATALOG'
        : 'SERVICE_FEEDBACK') as 'MEMBER' | 'SERVICE_CATALOG' | 'PRODUCT_CATALOG' | 'SERVICE_FEEDBACK',
  keyword: '',
  status: '',
  overdue: 'ALL' as 'ALL' | 'YES' | 'NO',
})
let poller: number | undefined

const processing = computed(() => rows.value.some(item => ['PENDING', 'RUNNING'].includes(item.status)))
const statusLabels: Record<AsyncJobStatus, string> = {
  PENDING: '等待中', RUNNING: '执行中', SUCCEEDED: '已完成', PARTIAL: '部分成功',
  FAILED: '失败', CANCELLED: '已取消',
}
const statusTypes: Record<AsyncJobStatus, 'info' | 'primary' | 'success' | 'warning' | 'danger'> = {
  PENDING: 'info', RUNNING: 'primary', SUCCEEDED: 'success', PARTIAL: 'warning',
  FAILED: 'danger', CANCELLED: 'info',
}
const canCreateExport = computed(() => auth.hasPermission('system:job:create') && (
  auth.hasPermission('member:member:export') || auth.hasPermission('catalog:service:export')
  || auth.hasPermission('catalog:product:export') || auth.hasPermission('visit:feedback:view')
))

async function load(silent = false) {
  if (loading.value) return
  loading.value = true
  try {
    const result = await getJobs({
      status: query.status || undefined,
      page: query.page,
      size: query.size,
    })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    if (!silent) ElMessage.error(error instanceof Error ? error.message : '任务列表加载失败')
  } finally {
    loading.value = false
  }
}

async function submitExport() {
  creating.value = true
  try {
    await createExport({
      exportType: exportForm.exportType,
      keyword: ['MEMBER', 'SERVICE_CATALOG', 'PRODUCT_CATALOG'].includes(exportForm.exportType)
        ? exportForm.keyword.trim() || undefined : undefined,
      status: exportForm.status || undefined,
      overdue: exportForm.exportType === 'SERVICE_FEEDBACK'
        ? exportForm.overdue === 'ALL' ? undefined : exportForm.overdue === 'YES'
        : undefined,
    })
    ElMessage.success('导出任务已创建，可在本页查看进度')
    query.page = 1
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出任务创建失败')
  } finally {
    creating.value = false
  }
}

async function showDetail(rowValue: unknown) {
  const row = rowValue as AsyncJobItem
  try {
    detail.value = await getJob(row.id)
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '任务详情加载失败')
  }
}

async function cancel(rowValue: unknown) {
  const row = rowValue as AsyncJobItem
  await ElMessageBox.confirm(`确认取消任务“${row.jobName}”吗？`, '取消任务', { type: 'warning' })
  try {
    await cancelJob(row.id)
    ElMessage.success('任务已取消')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '任务取消失败')
  }
}

async function download(rowValue: unknown) {
  const row = rowValue as AsyncJobItem
  try {
    const blob = await downloadJobResult(row.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = row.resultFileName || `${row.jobNo}.csv`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 0)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '结果文件下载失败')
  }
}

function reset() {
  query.status = ''
  query.page = 1
  void load()
}

function changePage(value: number) {
  query.page = value
  void load()
}

function changeSize(value: number) {
  query.size = value
  query.page = 1
  void load()
}

function dateTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function canDownload(rowValue: unknown) {
  const row = rowValue as AsyncJobItem
  return ['SUCCEEDED', 'PARTIAL'].includes(row.status) && !!row.resultFileId
    && new Date(row.expiresAt).getTime() > Date.now()
}

onMounted(() => {
  void load()
  poller = window.setInterval(() => { if (processing.value) void load(true) }, 3000)
})
onBeforeUnmount(() => { if (poller) window.clearInterval(poller) })
watch(() => exportForm.exportType, () => {
  exportForm.keyword = ''
  exportForm.status = ''
  exportForm.overdue = 'ALL'
})
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div>
        <h1>下载中心</h1>
        <p>任务按创建人隔离，结果文件默认保留7天；会员、服务项目、产品资料和服务反馈均固定导出当前门店。</p>
      </div>
    </div>

    <el-card v-if="canCreateExport" class="filter-card export-card" shadow="never">
      <template #header><strong>新建导出任务</strong></template>
      <el-form inline @submit.prevent="submitExport">
        <el-form-item label="导出内容">
          <el-select v-model="exportForm.exportType" style="width: 170px">
            <el-option v-if="auth.hasPermission('member:member:export')" label="会员名单" value="MEMBER" />
            <el-option v-if="auth.hasPermission('catalog:service:export')" label="服务项目" value="SERVICE_CATALOG" />
            <el-option v-if="auth.hasPermission('catalog:product:export')" label="产品资料" value="PRODUCT_CATALOG" />
            <el-option v-if="auth.hasPermission('visit:feedback:view')" label="服务反馈" value="SERVICE_FEEDBACK" />
          </el-select>
        </el-form-item>
        <el-form-item label="当前门店"><el-input :model-value="auth.user?.currentStoreName" disabled style="width: 180px" /></el-form-item>
        <el-form-item v-if="['MEMBER', 'SERVICE_CATALOG', 'PRODUCT_CATALOG'].includes(exportForm.exportType)" :label="exportForm.exportType === 'MEMBER' ? '会员查询' : exportForm.exportType === 'PRODUCT_CATALOG' ? '产品查询' : '项目查询'">
          <el-input v-model="exportForm.keyword" clearable maxlength="100" :placeholder="exportForm.exportType === 'MEMBER' ? '姓名、手机号、会员号或卡号' : exportForm.exportType === 'PRODUCT_CATALOG' ? '产品编号、名称或条码' : '项目编号或名称'" style="width: 220px" />
        </el-form-item>
        <el-form-item v-if="!['SERVICE_CATALOG', 'PRODUCT_CATALOG'].includes(exportForm.exportType)" :label="exportForm.exportType === 'MEMBER' ? '会员状态' : '反馈状态'">
          <el-select v-model="exportForm.status" clearable placeholder="全部" style="width: 150px">
            <template v-if="exportForm.exportType === 'SERVICE_FEEDBACK'">
              <el-option label="待处理" value="OPEN" /><el-option label="处理中" value="PROCESSING" />
              <el-option label="已解决" value="RESOLVED" /><el-option label="已关闭" value="CLOSED" />
            </template>
            <template v-else>
              <el-option label="正常" value="ACTIVE" /><el-option label="已冻结" value="FROZEN" />
              <el-option label="已停用" value="INACTIVE" />
            </template>
          </el-select>
        </el-form-item>
        <el-form-item v-if="exportForm.exportType === 'SERVICE_FEEDBACK'" label="是否超时">
          <el-select v-model="exportForm.overdue" style="width: 130px">
            <el-option label="全部" value="ALL" /><el-option label="仅超时" value="YES" /><el-option label="未超时" value="NO" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :loading="creating">生成CSV</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="query.page = 1; load()">
        <el-form-item label="任务状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px">
            <el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column prop="jobNo" label="任务编号" min-width="205" />
        <el-table-column prop="jobName" label="任务名称" min-width="150" />
        <el-table-column label="状态" width="110">
          <template #default="scope"><el-tag :type="statusTypes[scope.row.status as AsyncJobStatus]">{{ statusLabels[scope.row.status as AsyncJobStatus] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="进度" width="150"><template #default="scope"><el-progress :percentage="scope.row.progress" :stroke-width="8" /></template></el-table-column>
        <el-table-column label="结果" width="110"><template #default="scope">{{ scope.row.successCount }} 成功 / {{ scope.row.failureCount }} 失败</template></el-table-column>
        <el-table-column label="创建时间" width="170"><template #default="scope">{{ dateTime(scope.row.createdAt) }}</template></el-table-column>
        <el-table-column label="过期时间" width="170"><template #default="scope">{{ dateTime(scope.row.expiresAt) }}</template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="showDetail(scope.row)">详情</el-button>
            <el-button v-if="canDownload(scope.row)" link type="primary" @click="download(scope.row)">下载</el-button>
            <el-button v-if="scope.row.status === 'PENDING' && auth.hasPermission('system:job:cancel')" link type="danger" @click="cancel(scope.row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        :current-page="query.page" :page-size="query.size" :total="total"
        layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]"
        @update:current-page="changePage" @update:page-size="changeSize"
      />
    </el-card>

    <el-drawer v-model="detailVisible" title="任务详情" size="520px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="任务编号">{{ detail.jobNo }}</el-descriptions-item>
        <el-descriptions-item label="任务名称">{{ detail.jobName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabels[detail.status] }}</el-descriptions-item>
        <el-descriptions-item label="进度">{{ detail.progress }}%</el-descriptions-item>
        <el-descriptions-item label="结果">{{ detail.successCount }} 成功 / {{ detail.failureCount }} 失败</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ dateTime(detail.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ dateTime(detail.finishedAt) }}</el-descriptions-item>
        <el-descriptions-item label="文件">{{ detail.resultFileName || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMessage" label="失败原因"><span class="error-text">{{ detail.errorMessage }}</span></el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<style scoped>
.export-card { margin-bottom: 16px; }
.el-pagination { justify-content: flex-end; margin-top: 16px; }
.error-text { color: var(--el-color-danger); white-space: pre-wrap; }
</style>
