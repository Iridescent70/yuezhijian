<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  confirmTransfer,
  createTransfer,
  getAllGifts,
  getTransfer,
  getTransfers,
  reverseTransfer,
  voidTransfer,
} from '@/api/inventory'
import { useAuthStore } from '@/stores/auth'
import type { Gift, InventoryTransferStatus, TransferDetail, TransferSummary } from '@/types/api'

interface DraftLine { key: string; giftId?: number; quantity: number; note: string }

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<TransferSummary[]>([])
const total = ref(0)
const gifts = ref<Gift[]>([])
const query = reactive<{ storeId?: number; keyword: string; status?: InventoryTransferStatus; page: number; size: number }>({
  storeId: undefined, keyword: '', status: undefined, page: 1, size: 20,
})
const createVisible = ref(false)
const form = reactive({
  sourceStoreId: auth.user?.currentStoreId,
  targetStoreId: undefined as number | undefined,
  transferDate: localDate(), remarks: '', lines: [] as DraftLine[],
})
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<TransferDetail>()

async function load() {
  loading.value = true
  try {
    const [page, giftRows] = await Promise.all([
      getTransfers({ ...query, keyword: query.keyword.trim() || undefined }),
      getAllGifts('ACTIVE'),
    ])
    rows.value = page.items
    total.value = page.total
    gifts.value = giftRows
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '调拨单加载失败') }
  finally { loading.value = false }
}
function resetQuery() { Object.assign(query, { storeId: undefined, keyword: '', status: undefined, page: 1 }); void load() }
function emptyLine(): DraftLine { return { key: crypto.randomUUID(), giftId: undefined, quantity: 1, note: '' } }
function openCreate() {
  Object.assign(form, {
    sourceStoreId: auth.user?.currentStoreId, targetStoreId: undefined,
    transferDate: localDate(), remarks: '', lines: [emptyLine()],
  })
  createVisible.value = true
}
function addLine() { form.lines.push(emptyLine()) }
function removeLine(index: number) { if (form.lines.length > 1) form.lines.splice(index, 1) }
function giftFor(id?: number) { return gifts.value.find(item => item.id === id) }
async function save() {
  if (!form.sourceStoreId || !form.targetStoreId || !form.transferDate || !form.lines.length) {
    ElMessage.warning('请选择调出、调入门店并填写调拨明细'); return
  }
  if (form.sourceStoreId === form.targetStoreId) { ElMessage.warning('调出和调入门店不能相同'); return }
  if (form.lines.some(line => !line.giftId || line.quantity <= 0)) { ElMessage.warning('每行必须选择礼品并填写正数数量'); return }
  const giftIds = form.lines.map(line => line.giftId)
  if (new Set(giftIds).size !== giftIds.length) { ElMessage.warning('同一礼品只能填写一行'); return }
  saving.value = true
  try {
    const created = await createTransfer({
      sourceStoreId: form.sourceStoreId, targetStoreId: form.targetStoreId,
      transferDate: form.transferDate, remarks: form.remarks.trim() || undefined,
      idempotencyKey: `web-transfer-${crypto.randomUUID()}`,
      lines: form.lines.map(line => ({ giftId: line.giftId!, quantity: line.quantity, note: line.note.trim() || undefined })),
    })
    ElMessage.success('调拨草稿已创建，复核后请确认出入库')
    createVisible.value = false
    await load()
    await openDetail(created.id)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '调拨单创建失败') }
  finally { saving.value = false }
}
async function openDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  try { detail.value = await getTransfer(id) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '调拨详情加载失败'); detailVisible.value = false }
  finally { detailLoading.value = false }
}
async function doConfirm() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('确认后将同时扣减调出门店并增加调入门店库存，是否继续？', '确认调拨', { type: 'warning' })
    await runAction(() => confirmTransfer(detail.value!.id, detail.value!.version, '人工复核确认'))
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '调拨确认失败')
  }
}
async function doReasonAction(action: 'void' | 'reverse') {
  if (!detail.value) return
  try {
    const result = await ElMessageBox.prompt(
      action === 'void' ? '请输入作废原因' : '冲销要求调入门店仍有足够库存，请输入冲销原因',
      action === 'void' ? '作废调拨单' : '冲销调拨单',
      { inputValidator: value => value.trim().length > 0 || '原因不能为空', type: 'warning' },
    )
    await runAction(() => action === 'void'
      ? voidTransfer(detail.value!.id, detail.value!.version, result.value.trim())
      : reverseTransfer(detail.value!.id, detail.value!.version, result.value.trim()))
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '调拨处理失败')
  }
}
async function runAction(action: () => Promise<TransferDetail>) {
  saving.value = true
  try { detail.value = await action(); ElMessage.success('调拨单已处理'); await load() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '调拨处理失败') }
  finally { saving.value = false }
}
function statusLabel(status: InventoryTransferStatus) { return { DRAFT: '草稿', CONFIRMED: '已确认', VOIDED: '已作废', REVERSED: '已冲销' }[status] }
function statusType(status: InventoryTransferStatus): 'success' | 'warning' | 'info' | 'danger' {
  return status === 'CONFIRMED' ? 'success' : status === 'DRAFT' ? 'info' : status === 'REVERSED' ? 'warning' : 'danger'
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
      <div><h1>礼品调拨</h1><p>草稿不占用库存；确认时一次完成调出、调入和双边流水，冲销通过反向流水恢复。</p></div>
      <el-button v-if="auth.hasPermission('inventory:transfer:manage')" type="primary" @click="openCreate">新建调拨</el-button>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="涉及门店"><el-select v-model="query.storeId" clearable placeholder="全部" style="width: 190px"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="调拨单号"><el-input v-model="query.keyword" clearable maxlength="200" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部" style="width: 125px"><el-option label="草稿" value="DRAFT" /><el-option label="已确认" value="CONFIRMED" /><el-option label="已作废" value="VOIDED" /><el-option label="已冲销" value="REVERSED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column prop="transferNo" label="调拨单号" min-width="205" />
        <el-table-column prop="sourceStoreName" label="调出门店" min-width="150" />
        <el-table-column prop="targetStoreName" label="调入门店" min-width="150" />
        <el-table-column prop="transferDate" label="调拨日期" width="110" />
        <el-table-column prop="lineCount" label="礼品种类" width="90" align="right" />
        <el-table-column prop="totalQuantity" label="总数量" width="90" align="right" />
        <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="createdByName" label="制单人" width="110" />
        <el-table-column label="操作" width="75" fixed="right"><template #default="scope"><el-button link type="primary" @click="openDetail(scope.row.id)">详情</el-button></template></el-table-column>
      </el-table>
      <el-pagination :current-page="query.page" :page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @update:current-page="changePage" @update:page-size="changeSize" />
    </el-card>

    <el-dialog v-model="createVisible" title="新建礼品调拨" width="820px" destroy-on-close>
      <el-form label-width="90px">
        <div class="form-grid">
          <el-form-item label="调出门店" required><el-select v-model="form.sourceStoreId"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
          <el-form-item label="调入门店" required><el-select v-model="form.targetStoreId"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
          <el-form-item label="调拨日期" required><el-date-picker v-model="form.transferDate" type="date" value-format="YYYY-MM-DD" :disabled-date="disableFuture" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="form.remarks" maxlength="500" /></el-form-item>
        </div>
        <div class="line-heading"><strong>调拨明细</strong><el-button link type="primary" @click="addLine">添加礼品</el-button></div>
        <div v-for="(line, index) in form.lines" :key="line.key" class="line-row">
          <el-select v-model="line.giftId" filterable placeholder="选择礼品" class="gift-select"><el-option v-for="gift in gifts" :key="gift.id" :label="`${gift.code} · ${gift.name}`" :value="gift.id" /></el-select>
          <el-input-number v-model="line.quantity" :min="0" :precision="giftFor(line.giftId)?.unitDecimalPlaces ?? 0" />
          <span class="unit">{{ giftFor(line.giftId)?.unitName ?? '-' }}</span>
          <el-input v-model="line.note" maxlength="200" placeholder="明细备注" />
          <el-button link type="danger" :disabled="form.lines.length === 1" @click="removeLine(index)">删除</el-button>
        </div>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存草稿</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="调拨单详情" size="68%">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="2" border>
          <el-descriptions-item label="调拨单号">{{ detail.transferNo }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusType(detail.status)">{{ statusLabel(detail.status) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="调出门店">{{ detail.sourceStoreName }}</el-descriptions-item>
          <el-descriptions-item label="调入门店">{{ detail.targetStoreName }}</el-descriptions-item>
          <el-descriptions-item label="调拨日期">{{ detail.transferDate }}</el-descriptions-item>
          <el-descriptions-item label="制单信息">{{ detail.createdByName }} · {{ dateTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remarks || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.actionReason" label="处理说明" :span="2">{{ detail.actionReason }}</el-descriptions-item>
        </el-descriptions>
        <el-table v-if="detail" :data="detail.lines" stripe class="detail-table">
          <el-table-column prop="giftCode" label="礼品编号" width="140" />
          <el-table-column prop="giftName" label="礼品名称" min-width="180" />
          <el-table-column prop="quantity" label="数量" width="100" align="right" />
          <el-table-column prop="unitName" label="单位" width="70" />
          <el-table-column prop="note" label="备注" min-width="160" />
        </el-table>
        <div v-if="detail && auth.hasPermission('inventory:transfer:manage')" class="drawer-actions">
          <el-button v-if="detail.status === 'DRAFT'" type="primary" :loading="saving" @click="doConfirm">确认出入库</el-button>
          <el-button v-if="detail.status === 'DRAFT'" type="danger" plain :loading="saving" @click="doReasonAction('void')">作废</el-button>
          <el-button v-if="detail.status === 'CONFIRMED'" type="warning" :loading="saving" @click="doReasonAction('reverse')">冲销</el-button>
        </div>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.line-heading { display: flex; justify-content: space-between; align-items: center; margin: 8px 0 12px; }
.line-row { display: grid; grid-template-columns: minmax(220px, 1.4fr) 140px 40px minmax(160px, 1fr) 50px; gap: 10px; align-items: center; margin-bottom: 10px; }
.gift-select { width: 100%; }
.unit { color: var(--text-muted); }
.detail-table { margin-top: 18px; }
.drawer-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
</style>
