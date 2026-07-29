<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuditLog, getAuditLogs } from '@/api/audit'
import type { AuditLogDetail, AuditLogSummary } from '@/types/api'

const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const rows = ref<AuditLogSummary[]>([])
const total = ref(0)
const detail = ref<AuditLogDetail>()
const dateRange = ref<string[]>([])
const query = reactive({
  operator: '', module: '', action: '', objectType: '', objectId: '', result: '', page: 1, size: 20,
})

const fieldLabels: Record<string, string> = {
  code: '编号', name: '名称', categoryName: '分类', unitName: '单位', barcode: '条码',
  costPrice: '成本', costAmount: '成本', salePrice: '标准售价', listPrice: '标准售价',
  durationMinutes: '时长（分钟）', trackStock: '跟踪库存', description: '说明', status: '资料状态',
  storeName: '门店', storePrice: '门店售价', saleStatus: '销售状态',
  type: '类型', electronic: '电子支付', includedInRevenue: '计入营业额',
  needsExternalReference: '需外部凭证', applicable: '适用门店', enabled: '门店启用', sortNo: '显示顺序',
  city: '城市', district: '区域', address: '详细地址', longitude: '经度', latitude: '纬度',
  radiusKm: '服务半径（公里）', visitFee: '上门费',
  businessType: '适用业务', requiresNote: '必须填写说明',
  positionCode: '展示位置', imageName: '图片文件', linkType: '跳转类型', linkValue: '跳转地址',
  validFrom: '开始时间', validTo: '结束时间',
}

const detailChanges = computed(() => {
  if (!detail.value) return []
  const fields = new Set([
    ...Object.keys(detail.value.beforeValues), ...Object.keys(detail.value.afterValues),
  ])
  return [...fields].map(field => ({
    field,
    label: fieldLabels[field] ?? field,
    beforeValue: detail.value?.beforeValues[field],
    afterValue: detail.value?.afterValues[field],
  })).filter(item => item.beforeValue !== item.afterValue)
})

async function load() {
  loading.value = true
  try {
    const page = await getAuditLogs({
      operator: query.operator.trim() || undefined,
      module: query.module.trim() || undefined,
      action: query.action.trim() || undefined,
      objectType: query.objectType.trim() || undefined,
      objectId: query.objectId.trim() || undefined,
      result: query.result || undefined,
      occurredFrom: dateRange.value[0],
      occurredTo: dateRange.value[1],
      page: query.page,
      size: query.size,
    })
    rows.value = page.items
    total.value = page.total
    query.page = page.page
    query.size = page.size
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作日志加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  void load()
}

function reset() {
  Object.assign(query, { operator: '', module: '', action: '', objectType: '', objectId: '', result: '', page: 1 })
  dateRange.value = []
  void load()
}

async function showDetail(value: unknown) {
  const row = value as AuditLogSummary
  detailVisible.value = true
  detailLoading.value = true
  try { detail.value = await getAuditLog(row.id) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '日志详情加载失败') }
  finally { detailLoading.value = false }
}

function changePage(value: number) { query.page = value; void load() }
function changeSize(value: number) { query.size = value; query.page = 1; void load() }
function dateTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '—' }

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>操作日志</h1><p>查询系统已接入的业务变更证据；日志只读，不能用于直接恢复或修改业务数据。</p></div>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="search">
        <el-form-item label="操作人"><el-input v-model="query.operator" clearable placeholder="姓名或账号" style="width: 160px" /></el-form-item>
        <el-form-item label="模块"><el-input v-model="query.module" clearable placeholder="如 CATALOG" style="width: 150px" /></el-form-item>
        <el-form-item label="动作"><el-input v-model="query.action" clearable placeholder="如 UPDATE" style="width: 150px" /></el-form-item>
        <el-form-item label="对象"><el-input v-model="query.objectType" clearable placeholder="类型" style="width: 130px" /><el-input v-model="query.objectId" clearable placeholder="编号" style="width: 130px; margin-left: 8px" /></el-form-item>
        <el-form-item label="结果"><el-select v-model="query.result" clearable placeholder="全部" style="width: 120px"><el-option label="成功" value="SUCCESS" /><el-option label="失败" value="FAILURE" /></el-select></el-form-item>
        <el-form-item label="发生日期"><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 250px" /></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column prop="operatorName" label="操作人" width="140" />
        <el-table-column prop="module" label="模块" width="120" />
        <el-table-column prop="action" label="动作" width="170" />
        <el-table-column label="对象" min-width="190"><template #default="scope">{{ scope.row.objectType || '—' }} / {{ scope.row.objectId || '—' }}</template></el-table-column>
        <el-table-column label="结果" width="90"><template #default="scope"><el-tag :type="scope.row.result === 'SUCCESS' ? 'success' : 'danger'">{{ scope.row.result === 'SUCCESS' ? '成功' : '失败' }}</el-tag></template></el-table-column>
        <el-table-column label="发生时间" width="175"><template #default="scope">{{ dateTime(scope.row.occurredAt) }}</template></el-table-column>
        <el-table-column label="操作" width="85" fixed="right"><template #default="scope"><el-button link type="primary" @click="showDetail(scope.row)">详情</el-button></template></el-table-column>
      </el-table>
      <el-pagination :current-page="query.page" :page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @update:current-page="changePage" @update:page-size="changeSize" />
    </el-card>

    <el-drawer v-model="detailVisible" title="操作日志详情" size="720px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="2" border>
          <el-descriptions-item label="操作人">{{ detail.operatorName }}{{ detail.userId == null ? '' : `（ID ${detail.userId}）` }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ dateTime(detail.occurredAt) }}</el-descriptions-item>
          <el-descriptions-item label="模块/动作">{{ detail.module }} / {{ detail.action }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ detail.result === 'SUCCESS' ? '成功' : '失败' }}</el-descriptions-item>
          <el-descriptions-item label="业务对象">{{ detail.objectType || '—' }} / {{ detail.objectId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="门店ID">{{ detail.storeId ?? '全局' }}</el-descriptions-item>
          <el-descriptions-item label="错误码">{{ detail.errorCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="IP">{{ detail.ip || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Trace ID" :span="2">{{ detail.traceId }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="audit-detail-title">字段变化</h3>
        <el-table v-if="detail" :data="detailChanges" border>
          <el-table-column prop="label" label="字段" width="150" />
          <el-table-column label="修改前" min-width="220"><template #default="scope">{{ scope.row.beforeValue ?? '—' }}</template></el-table-column>
          <el-table-column label="修改后" min-width="220"><template #default="scope">{{ scope.row.afterValue ?? '—' }}</template></el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.audit-detail-title { margin: 20px 0 12px; }
</style>
