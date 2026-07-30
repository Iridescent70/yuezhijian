<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createGift, getGift, getStockLedgers, getStocks, updateGift } from '@/api/inventory'
import { getCatalogUnits, getItemCategories } from '@/api/masterData'
import { useAuthStore } from '@/stores/auth'
import type { CategoryOption, GiftStatus, StockItem, StockLedgerItem, UnitOption } from '@/types/api'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<StockItem[]>([])
const total = ref(0)
const categories = ref<CategoryOption[]>([])
const units = ref<UnitOption[]>([])
const query = reactive({ storeId: auth.user?.currentStoreId, keyword: '', lowStock: undefined as boolean | undefined, page: 1, size: 20 })
const giftVisible = ref(false)
const editingId = ref<number>()
const giftForm = reactive({
  code: '', name: '', categoryId: undefined as number | undefined, unitId: undefined as number | undefined,
  pointPrice: 1, costPrice: 0, lowStockThreshold: 0, description: '', status: 'ACTIVE' as GiftStatus, version: '',
})
const ledgerVisible = ref(false)
const ledgerLoading = ref(false)
const ledgerTitle = ref('')
const ledgers = ref<StockLedgerItem[]>([])
const ledgerTotal = ref(0)
const ledgerQuery = reactive({ storeId: 0, giftId: 0, page: 1, size: 20 })
const selectedUnit = computed(() => units.value.find(item => item.id === giftForm.unitId))

async function load() {
  loading.value = true
  try {
    const [stockPage, categoryRows, unitRows] = await Promise.all([
      getStocks({ ...query, keyword: query.keyword.trim() || undefined }),
      getItemCategories('GIFT'),
      getCatalogUnits(),
    ])
    rows.value = stockPage.items
    total.value = stockPage.total
    categories.value = categoryRows
    units.value = unitRows
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '礼品库存加载失败')
  } finally { loading.value = false }
}

function resetQuery() {
  Object.assign(query, { storeId: auth.user?.currentStoreId, keyword: '', lowStock: undefined, page: 1 })
  void load()
}
function resetGiftForm() {
  Object.assign(giftForm, {
    code: '', name: '', categoryId: categories.value[0]?.id, unitId: units.value.find(item => item.code === 'PIECE')?.id ?? units.value[0]?.id,
    pointPrice: 1, costPrice: 0, lowStockThreshold: 0, description: '', status: 'ACTIVE', version: '',
  })
}
function openCreate() { editingId.value = undefined; resetGiftForm(); giftVisible.value = true }
async function openEdit(giftId: number) {
  try {
    const detail = await getGift(giftId)
    editingId.value = detail.id
    Object.assign(giftForm, {
      code: detail.code, name: detail.name, categoryId: detail.categoryId, unitId: detail.unitId,
      pointPrice: detail.pointPrice, costPrice: detail.costPrice, lowStockThreshold: detail.lowStockThreshold,
      description: detail.description ?? '', status: detail.status, version: detail.version,
    })
    giftVisible.value = true
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '礼品详情加载失败') }
}
async function saveGift() {
  if (!giftForm.code.trim() || !giftForm.name.trim() || !giftForm.categoryId || !giftForm.unitId) {
    ElMessage.warning('请填写礼品编号、名称、分类和单位'); return
  }
  saving.value = true
  try {
    const payload = {
      name: giftForm.name.trim(), categoryId: giftForm.categoryId, unitId: giftForm.unitId,
      pointPrice: giftForm.pointPrice, costPrice: giftForm.costPrice,
      lowStockThreshold: giftForm.lowStockThreshold, description: giftForm.description.trim() || undefined,
    }
    if (editingId.value) await updateGift(editingId.value, { ...payload, status: giftForm.status, version: giftForm.version })
    else await createGift({ code: giftForm.code.trim(), ...payload })
    ElMessage.success(editingId.value ? '礼品资料已更新' : '礼品资料已创建')
    giftVisible.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '礼品保存失败') }
  finally { saving.value = false }
}
async function loadLedgers() {
  ledgerLoading.value = true
  try {
    const result = await getStockLedgers(ledgerQuery.storeId, ledgerQuery.giftId, { page: ledgerQuery.page, size: ledgerQuery.size })
    ledgers.value = result.items
    ledgerTotal.value = result.total
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '库存流水加载失败') }
  finally { ledgerLoading.value = false }
}
function openLedgers(giftId: number) {
  const row = rows.value.find(item => item.giftId === giftId)
  if (!row) return
  ledgerTitle.value = `${row.storeName} · ${row.giftCode} ${row.giftName}`
  Object.assign(ledgerQuery, { storeId: row.storeId, giftId: row.giftId, page: 1, size: 20 })
  ledgerVisible.value = true
  void loadLedgers()
}
function quantity(value: number, decimals: number) { return Number(value).toFixed(decimals) }
function dateTime(value: string) { return value.replace('T', ' ').slice(0, 19) }
function transactionLabel(value: string) {
  return ({ TRANSFER_OUT: '调拨出库', TRANSFER_IN: '调拨入库', TRANSFER_REVERSAL_OUT: '调拨冲销出库',
    TRANSFER_REVERSAL_IN: '调拨冲销入库', COUNT_GAIN: '盘盈', COUNT_LOSS: '盘亏',
    POINT_REDEMPTION_OUT: '积分兑换', POINT_REDEMPTION_REVERSAL_IN: '兑换冲销' } as Record<string, string>)[value] ?? value
}
function changePage(page: number) { query.page = page; void load() }
function changeSize(size: number) { query.size = size; query.page = 1; void load() }
function changeLedgerPage(page: number) { ledgerQuery.page = page; void loadLedgers() }
onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>礼品库存</h1><p>按门店查看实时库存和不可变流水；库存增减只能由已确认调拨、盘点或兑换单产生。</p></div>
      <el-button v-if="auth.hasPermission('inventory:gift:manage')" type="primary" @click="openCreate">新建礼品</el-button>
    </div>
    <el-alert type="info" :closable="false" show-icon title="新礼品的期初库存请通过盘点单录入，禁止直接修改库存余额。" />
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="门店"><el-select v-model="query.storeId" style="width: 190px"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="礼品"><el-input v-model="query.keyword" clearable maxlength="200" placeholder="编号或名称" /></el-form-item>
        <el-form-item label="库存状态"><el-select v-model="query.lowStock" clearable placeholder="全部" style="width: 130px"><el-option label="低库存" :value="true" /><el-option label="库存正常" :value="false" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="giftId">
        <el-table-column prop="giftCode" label="礼品编号" width="140" />
        <el-table-column prop="giftName" label="礼品名称" min-width="200" />
        <el-table-column prop="storeName" label="门店" min-width="170" />
        <el-table-column label="现存量" width="120" align="right"><template #default="scope"><strong :class="{ danger: scope.row.lowStock }">{{ quantity(scope.row.onHandQuantity, scope.row.unitDecimalPlaces) }}</strong> {{ scope.row.unitName }}</template></el-table-column>
        <el-table-column label="预警线" width="110" align="right"><template #default="scope">{{ quantity(scope.row.lowStockThreshold, scope.row.unitDecimalPlaces) }}</template></el-table-column>
        <el-table-column label="库存状态" width="90"><template #default="scope"><el-tag :type="scope.row.lowStock ? 'danger' : 'success'">{{ scope.row.lowStock ? '低库存' : '正常' }}</el-tag></template></el-table-column>
        <el-table-column label="礼品状态" width="90"><template #default="scope"><el-tag :type="scope.row.giftStatus === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.giftStatus === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="145" fixed="right"><template #default="scope"><el-button link type="primary" @click="openLedgers(scope.row.giftId)">流水</el-button><el-button v-if="auth.hasPermission('inventory:gift:manage')" link type="primary" @click="openEdit(scope.row.giftId)">礼品资料</el-button></template></el-table-column>
      </el-table>
      <el-pagination :current-page="query.page" :page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @update:current-page="changePage" @update:page-size="changeSize" />
    </el-card>

    <el-dialog v-model="giftVisible" :title="editingId ? '编辑礼品资料' : '新建礼品资料'" width="620px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="礼品编号" required><el-input v-model="giftForm.code" :disabled="Boolean(editingId)" maxlength="64" /></el-form-item>
        <el-form-item label="礼品名称" required><el-input v-model="giftForm.name" maxlength="200" /></el-form-item>
        <el-form-item label="礼品分类" required><el-select v-model="giftForm.categoryId" class="dialog-full-control"><el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="库存单位" required><el-select v-model="giftForm.unitId" class="dialog-full-control"><el-option v-for="item in units" :key="item.id" :label="`${item.name}（${item.decimalPlaces}位小数）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="兑换积分" required><el-input-number v-model="giftForm.pointPrice" :min="1" :max="100000000" :precision="0" /></el-form-item>
        <el-form-item label="礼品成本"><el-input-number v-model="giftForm.costPrice" :min="0" :precision="4" /></el-form-item>
        <el-form-item label="低库存阈值"><el-input-number v-model="giftForm.lowStockThreshold" :min="0" :precision="selectedUnit?.decimalPlaces ?? 0" /></el-form-item>
        <el-form-item v-if="editingId" label="资料状态"><el-radio-group v-model="giftForm.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="说明"><el-input v-model="giftForm.description" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="giftVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveGift">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="ledgerVisible" :title="`库存流水 · ${ledgerTitle}`" size="72%">
      <el-table v-loading="ledgerLoading" :data="ledgers" stripe>
        <el-table-column prop="ledgerNo" label="流水号" min-width="190" />
        <el-table-column label="类型" width="125"><template #default="scope">{{ transactionLabel(scope.row.transactionType) }}</template></el-table-column>
        <el-table-column label="变动前" width="90" align="right"><template #default="scope">{{ scope.row.beforeQuantity }}</template></el-table-column>
        <el-table-column label="变动" width="90" align="right"><template #default="scope"><span :class="scope.row.changeQuantity < 0 ? 'danger' : 'success'">{{ scope.row.changeQuantity > 0 ? '+' : '' }}{{ scope.row.changeQuantity }}</span></template></el-table-column>
        <el-table-column prop="afterQuantity" label="变动后" width="90" align="right" />
        <el-table-column label="来源" width="150"><template #default="scope">{{ scope.row.sourceType }} #{{ scope.row.sourceId }}</template></el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column label="发生时间" width="165"><template #default="scope">{{ dateTime(scope.row.occurredAt) }}</template></el-table-column>
        <el-table-column prop="note" label="备注" min-width="180" show-overflow-tooltip />
      </el-table>
      <el-pagination :current-page="ledgerQuery.page" :page-size="ledgerQuery.size" :total="ledgerTotal" layout="total, prev, pager, next" @update:current-page="changeLedgerPage" />
    </el-drawer>
  </section>
</template>

<style scoped>
.danger { color: var(--el-color-danger); }
.success { color: var(--el-color-success); }
</style>
