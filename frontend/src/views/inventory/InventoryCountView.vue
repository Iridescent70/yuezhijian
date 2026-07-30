<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  confirmCount,
  createCount,
  getAllGifts,
  getCount,
  getCounts,
  saveCountLines,
  voidCount,
} from '@/api/inventory'
import { useAuthStore } from '@/stores/auth'
import type { CountDetail, CountSummary, Gift, InventoryCountStatus } from '@/types/api'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<CountSummary[]>([])
const total = ref(0)
const gifts = ref<Gift[]>([])
const query = reactive<{ storeId?: number; keyword: string; status?: InventoryCountStatus; page: number; size: number }>({
  storeId: auth.user?.currentStoreId, keyword: '', status: undefined, page: 1, size: 20,
})
const createVisible = ref(false)
const form = reactive({
  storeId: auth.user?.currentStoreId, name: '', countDate: localDate(), giftIds: [] as number[], remarks: '',
})
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<CountDetail>()

async function load() {
  loading.value = true
  try {
    const [page, giftRows] = await Promise.all([
      getCounts({ ...query, keyword: query.keyword.trim() || undefined }),
      getAllGifts(),
    ])
    rows.value = page.items
    total.value = page.total
    gifts.value = giftRows
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '盘点单加载失败') }
  finally { loading.value = false }
}
function resetQuery() {
  Object.assign(query, { storeId: auth.user?.currentStoreId, keyword: '', status: undefined, page: 1 })
  void load()
}
function openCreate() {
  Object.assign(form, {
    storeId: auth.user?.currentStoreId,
    name: `${localDate()} 礼品盘点`, countDate: localDate(), giftIds: [], remarks: '',
  })
  createVisible.value = true
}
function selectAllGifts() { form.giftIds = gifts.value.map(item => item.id) }
async function saveNewCount() {
  if (!form.storeId || !form.name.trim() || !form.countDate || form.giftIds.length === 0) {
    ElMessage.warning('请填写盘点名称、门店、日期并至少选择一个礼品'); return
  }
  saving.value = true
  try {
    const created = await createCount({
      storeId: form.storeId, name: form.name.trim(), countDate: form.countDate,
      giftIds: form.giftIds, remarks: form.remarks.trim() || undefined,
      idempotencyKey: `web-count-${crypto.randomUUID()}`,
    })
    ElMessage.success('盘点草稿已创建，账面数已锁定为本次盘点快照')
    createVisible.value = false
    await load()
    await openDetail(created.id)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '盘点单创建失败') }
  finally { saving.value = false }
}
async function openDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  try { detail.value = await getCount(id) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '盘点详情加载失败'); detailVisible.value = false }
  finally { detailLoading.value = false }
}
async function saveActualQuantities() {
  if (!detail.value) return
  if (detail.value.lines.some(line => line.actualQuantity == null || !Number.isFinite(line.actualQuantity) || line.actualQuantity < 0)) {
    ElMessage.warning('每个礼品都必须填写不小于0的实盘数量'); return
  }
  saving.value = true
  try {
    detail.value = await saveCountLines(
      detail.value.id,
      detail.value.version,
      detail.value.lines.map(line => ({ lineId: line.id, actualQuantity: line.actualQuantity! })),
    )
    ElMessage.success('实盘数量已保存，请核对差异后确认')
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '实盘数量保存失败') }
  finally { saving.value = false }
}
async function doConfirm() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(
      '确认后将按盘盈盘亏调整库存。若账面库存已被其他单据改变，本次确认会被拒绝，是否继续？',
      '确认盘点', { type: 'warning' },
    )
    await runAction(() => confirmCount(detail.value!.id, detail.value!.version, '盘点差异已人工复核'))
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '盘点确认失败')
  }
}
async function doVoid() {
  if (!detail.value) return
  try {
    const result = await ElMessageBox.prompt('请输入作废原因', '作废盘点单', {
      type: 'warning', inputValidator: value => value.trim().length > 0 || '原因不能为空',
    })
    await runAction(() => voidCount(detail.value!.id, detail.value!.version, result.value.trim()))
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '盘点作废失败')
  }
}
async function runAction(action: () => Promise<CountDetail>) {
  saving.value = true
  try { detail.value = await action(); ElMessage.success('盘点单已处理'); await load() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '盘点处理失败') }
  finally { saving.value = false }
}
function statusLabel(status: InventoryCountStatus) {
  return { DRAFT: '待录入', READY_CONFIRM: '待确认', CONFIRMED: '已确认', VOIDED: '已作废' }[status]
}
function statusType(status: InventoryCountStatus): 'success' | 'warning' | 'info' | 'danger' {
  return status === 'CONFIRMED' ? 'success' : status === 'READY_CONFIRM' ? 'warning' : status === 'DRAFT' ? 'info' : 'danger'
}
function quantity(value: number | undefined, decimals: number) {
  return value == null ? '-' : Number(value).toFixed(decimals)
}
function dateTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }
function localDate() { const now = new Date(); now.setMinutes(now.getMinutes() - now.getTimezoneOffset()); return now.toISOString().slice(0, 10) }
function disableFuture(date: Date) { return date.getTime() > Date.now() }
function changePage(page: number) { query.page = page; void load() }
function changeSize(size: number) { query.size = size; query.page = 1; void load() }
onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>礼品盘点</h1><p>创建时保存账面库存快照，录入实盘数后确认差异；确认前若库存已变化，系统会拒绝过期盘点。</p></div>
      <el-button v-if="auth.hasPermission('inventory:count:manage')" type="primary" @click="openCreate">新建盘点</el-button>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="门店"><el-select v-model="query.storeId" clearable placeholder="全部" style="width: 190px"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="盘点单"><el-input v-model="query.keyword" clearable maxlength="200" placeholder="单号或名称" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部" style="width: 125px"><el-option label="待录入" value="DRAFT" /><el-option label="待确认" value="READY_CONFIRM" /><el-option label="已确认" value="CONFIRMED" /><el-option label="已作废" value="VOIDED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column prop="countNo" label="盘点单号" min-width="205" />
        <el-table-column prop="name" label="盘点名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="storeName" label="门店" min-width="150" />
        <el-table-column prop="countDate" label="盘点日期" width="110" />
        <el-table-column prop="lineCount" label="礼品数" width="80" align="right" />
        <el-table-column prop="differenceLineCount" label="差异项" width="80" align="right" />
        <el-table-column prop="differenceQuantity" label="净差异" width="90" align="right" />
        <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="createdByName" label="制单人" width="110" />
        <el-table-column label="操作" width="75" fixed="right"><template #default="scope"><el-button link type="primary" @click="openDetail(scope.row.id)">详情</el-button></template></el-table-column>
      </el-table>
      <el-pagination :current-page="query.page" :page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @update:current-page="changePage" @update:page-size="changeSize" />
    </el-card>

    <el-dialog v-model="createVisible" title="新建礼品盘点" width="680px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="盘点名称" required><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="盘点门店" required><el-select v-model="form.storeId" class="full-control"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="盘点日期" required><el-date-picker v-model="form.countDate" type="date" value-format="YYYY-MM-DD" :disabled-date="disableFuture" /></el-form-item>
        <el-form-item label="盘点礼品" required>
          <div class="gift-picker">
            <el-select v-model="form.giftIds" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择本次盘点礼品" class="full-control">
              <el-option v-for="gift in gifts" :key="gift.id" :label="`${gift.code} · ${gift.name}${gift.status === 'DISABLED' ? '（停用）' : ''}`" :value="gift.id" />
            </el-select>
            <el-button @click="selectAllGifts">全部选择</el-button>
            <el-button @click="form.giftIds = []">清空</el-button>
          </div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remarks" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveNewCount">创建并录入</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="盘点单详情" size="72%">
      <div v-loading="detailLoading">
        <el-alert v-if="detail && ['DRAFT', 'READY_CONFIRM'].includes(detail.status)" type="warning" :closable="false" show-icon title="盘点期间请避免对相同礼品办理调拨或兑换；若账面库存变化，本单需作废后重建。" />
        <el-descriptions v-if="detail" :column="2" border class="detail-header">
          <el-descriptions-item label="盘点单号">{{ detail.countNo }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusType(detail.status)">{{ statusLabel(detail.status) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="盘点名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="盘点门店">{{ detail.storeName }}</el-descriptions-item>
          <el-descriptions-item label="盘点日期">{{ detail.countDate }}</el-descriptions-item>
          <el-descriptions-item label="制单信息">{{ detail.createdByName }} · {{ dateTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remarks || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.actionReason" label="处理说明" :span="2">{{ detail.actionReason }}</el-descriptions-item>
        </el-descriptions>
        <el-table v-if="detail" :data="detail.lines" stripe class="detail-table" row-key="id">
          <el-table-column prop="giftCode" label="礼品编号" width="140" />
          <el-table-column prop="giftName" label="礼品名称" min-width="190" />
          <el-table-column label="账面数量" width="115" align="right"><template #default="scope">{{ quantity(scope.row.bookQuantity, scope.row.unitDecimalPlaces) }}</template></el-table-column>
          <el-table-column label="实盘数量" width="170" align="right">
            <template #default="scope">
              <el-input-number v-if="['DRAFT', 'READY_CONFIRM'].includes(detail.status) && auth.hasPermission('inventory:count:manage')" v-model="scope.row.actualQuantity" :min="0" :precision="scope.row.unitDecimalPlaces" controls-position="right" />
              <span v-else>{{ quantity(scope.row.actualQuantity, scope.row.unitDecimalPlaces) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="差异" width="105" align="right"><template #default="scope"><span :class="{ gain: (scope.row.differenceQuantity ?? 0) > 0, loss: (scope.row.differenceQuantity ?? 0) < 0 }">{{ quantity(scope.row.differenceQuantity, scope.row.unitDecimalPlaces) }}</span></template></el-table-column>
          <el-table-column prop="unitName" label="单位" width="70" />
        </el-table>
        <div v-if="detail && auth.hasPermission('inventory:count:manage')" class="drawer-actions">
          <el-button v-if="['DRAFT', 'READY_CONFIRM'].includes(detail.status)" type="primary" plain :loading="saving" @click="saveActualQuantities">保存实盘数</el-button>
          <el-button v-if="detail.status === 'READY_CONFIRM'" type="primary" :loading="saving" @click="doConfirm">确认并调整库存</el-button>
          <el-button v-if="['DRAFT', 'READY_CONFIRM'].includes(detail.status)" type="danger" plain :loading="saving" @click="doVoid">作废</el-button>
        </div>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.full-control { width: 100%; }
.gift-picker { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; gap: 8px; width: 100%; }
.detail-header { margin-top: 16px; }
.detail-table { margin-top: 18px; }
.drawer-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.gain { color: var(--el-color-success); }
.loss { color: var(--el-color-danger); }
</style>
