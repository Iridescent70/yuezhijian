<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createExport } from '@/api/jobs'
import { batchProductSaleStatus, createProduct, getProduct, getProductCategories, getProducts, getUnits, importProducts, updateProduct } from '@/api/product'
import { useAuthStore } from '@/stores/auth'
import type { CategoryOption, ProductBatchResult, ProductStoreConfig, ProductSummary, UnitOption } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const exporting = ref(false)
const importing = ref(false)
const importInput = ref<HTMLInputElement>()
const batchSubmitting = ref(false)
const selectedProducts = ref<ProductSummary[]>([])
const batchResult = ref<ProductBatchResult>()
const resultVisible = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const rows = ref<ProductSummary[]>([])
const categories = ref<CategoryOption[]>([])
const units = ref<UnitOption[]>([])
const editStores = ref<ProductStoreConfig[]>([])
const filters = reactive({ keyword: '', storeId: undefined as number | undefined, categoryId: undefined as number | undefined, saleStatus: '' })
const form = reactive({
  code: '', name: '', categoryId: undefined as number | undefined, unitId: undefined as number | undefined,
  barcode: '', costPrice: 0, salePrice: 0, storePrice: 0, trackStock: true,
  storeIds: [] as number[], storeId: undefined as number | undefined,
  saleStatus: 'ON_SALE', status: 'ACTIVE', description: '', version: '',
})

async function load() {
  loading.value = true
  try {
    ;[rows.value, categories.value, units.value] = await Promise.all([
      getProducts({
        keyword: filters.keyword.trim() || undefined,
        storeId: filters.storeId,
        categoryId: filters.categoryId,
        saleStatus: filters.saleStatus || undefined,
      }),
      getProductCategories(),
      getUnits(),
    ])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '产品资料加载失败')
  } finally {
    loading.value = false
  }
}

async function exportCurrentStore() {
  if (filters.storeId && filters.storeId !== auth.user?.currentStoreId) {
    ElMessage.warning('产品资料导出固定使用当前登录门店，请切换门店后再导出')
    return
  }
  exporting.value = true
  try {
    await createExport({
      exportType: 'PRODUCT_CATALOG',
      keyword: filters.keyword.trim() || undefined,
    })
    ElMessage.success('产品资料导出任务已创建')
    await router.push('/app/system/downloads')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '产品资料导出任务创建失败')
  } finally {
    exporting.value = false
  }
}

function downloadImportTemplate() {
  const categoryCode = categories.value[0]?.code ?? ''
  const unitCode = units.value.find(item => item.code === 'PIECE')?.code ?? units.value[0]?.code ?? ''
  const content = '\ufeff"产品编号","产品名称","分类编号","单位编号","条码","成本","标准售价","门店售价","跟踪库存","产品说明"\r\n'
    + `"PRD-DEMO","示例产品","${categoryCode}","${unitCode}","690000000000","20.00","68.00","58.00","是","请删除示例行后填写"\r\n`
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = '产品资料导入模板.csv'
  anchor.click()
  URL.revokeObjectURL(url)
}

async function uploadImport(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  importing.value = true
  try {
    await importProducts(file)
    ElMessage.success('产品资料导入任务已创建，请在下载中心查看结果')
    await router.push('/app/system/downloads')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '产品资料导入任务创建失败')
  } finally {
    importing.value = false
  }
}

function selectionChanged(value: unknown) {
  selectedProducts.value = value as ProductSummary[]
}

async function batchSaleStatus(saleStatus: 'ON_SALE' | 'OFF_SALE') {
  if (!selectedProducts.value.length) {
    ElMessage.warning('请先选择产品')
    return
  }
  if (filters.storeId !== auth.user?.currentStoreId) {
    ElMessage.warning('批量上下架固定使用当前登录门店，请先将门店筛选切换为当前门店')
    return
  }
  const action = saleStatus === 'ON_SALE' ? '上架' : '下架'
  await ElMessageBox.confirm(
    `确认在当前门店${action}已选择的 ${selectedProducts.value.length} 个产品吗？`,
    `批量${action}`,
    { type: saleStatus === 'ON_SALE' ? 'success' : 'warning' },
  )
  batchSubmitting.value = true
  try {
    batchResult.value = await batchProductSaleStatus({
      productIds: selectedProducts.value.map(item => item.id),
      saleStatus,
    })
    resultVisible.value = true
    selectedProducts.value = []
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `批量${action}失败`)
  } finally {
    batchSubmitting.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    code: '', name: '', categoryId: categories.value[0]?.id, unitId: units.value[0]?.id,
    barcode: '', costPrice: 0, salePrice: 0, storePrice: 0, trackStock: true,
    storeIds: auth.user?.currentStoreId ? [auth.user.currentStoreId] : [], storeId: undefined,
    saleStatus: 'ON_SALE', status: 'ACTIVE', description: '', version: '',
  })
}

function openCreate() {
  editingId.value = undefined
  editStores.value = []
  resetForm()
  dialogVisible.value = true
}

async function openEdit(value: unknown) {
  const row = value as ProductSummary
  saving.value = true
  try {
    const detail = await getProduct(row.id)
    const preferredStoreId = filters.storeId ?? auth.user?.currentStoreId
    const store = detail.stores.find(item => item.storeId === preferredStoreId) ?? detail.stores[0]
    if (!store) throw new Error('产品没有可维护的门店配置')
    editingId.value = detail.id
    editStores.value = detail.stores
    Object.assign(form, {
      code: detail.code, name: detail.name, categoryId: detail.categoryId, unitId: detail.unitId,
      barcode: detail.barcode ?? '', costPrice: detail.costPrice, salePrice: detail.salePrice,
      storePrice: store.storePrice, trackStock: detail.trackStock, storeIds: [], storeId: store.storeId,
      saleStatus: store.saleStatus, status: detail.status, description: detail.description ?? '', version: detail.version,
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '产品详情加载失败')
  } finally {
    saving.value = false
  }
}

function selectStore(storeId: number) {
  const store = editStores.value.find(item => item.storeId === storeId)
  if (!store) return
  form.storePrice = store.storePrice
  form.saleStatus = store.saleStatus
}

async function submit() {
  if (!form.code.trim() || !form.name.trim() || !form.categoryId || !form.unitId
    || (editingId.value ? !form.storeId : !form.storeIds.length)) {
    ElMessage.warning('请填写编号、名称、分类、单位和门店')
    return
  }
  saving.value = true
  try {
    if (editingId.value && form.storeId) {
      await updateProduct(editingId.value, {
        name: form.name, categoryId: form.categoryId, unitId: form.unitId,
        barcode: form.barcode || undefined, costPrice: form.costPrice, salePrice: form.salePrice,
        trackStock: form.trackStock, description: form.description || undefined, status: form.status,
        storeId: form.storeId, storePrice: form.storePrice, saleStatus: form.saleStatus, version: form.version,
      })
      ElMessage.success('产品已更新')
    } else {
      await createProduct({
        code: form.code, name: form.name, categoryId: form.categoryId, unitId: form.unitId,
        barcode: form.barcode || undefined, costPrice: form.costPrice, salePrice: form.salePrice,
        storePrice: form.storePrice, trackStock: form.trackStock, storeIds: form.storeIds,
        description: form.description || undefined,
      })
      ElMessage.success('产品已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '产品保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  filters.storeId = auth.user?.currentStoreId
  void load()
})
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>产品管理</h1><p>维护零售产品和服务耗用物料的基础资料、库存属性及门店售价。</p></div>
      <div>
        <template v-if="auth.hasPermission('catalog:product:manage') && auth.hasPermission('system:job:create') && auth.hasPermission('system:job:view')">
          <el-button @click="downloadImportTemplate">下载导入模板</el-button>
          <el-button :loading="importing" @click="importInput?.click()">批量导入</el-button>
          <input ref="importInput" class="file-input" type="file" accept=".csv,text/csv" @change="uploadImport">
        </template>
        <el-button
          v-if="auth.hasPermission('system:job:create') && auth.hasPermission('system:job:view') && auth.hasPermission('catalog:product:export')"
          :loading="exporting"
          @click="exportCurrentStore"
        >导出当前门店</el-button>
        <el-button v-if="auth.hasPermission('catalog:product:manage')" type="primary" @click="openCreate">新建产品</el-button>
      </div>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="产品查询"><el-input v-model="filters.keyword" clearable placeholder="编号、名称或条码" /></el-form-item>
        <el-form-item label="门店"><el-select v-model="filters.storeId" clearable placeholder="全部门店" class="master-filter-select"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="分类"><el-select v-model="filters.categoryId" clearable placeholder="全部分类"><el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="销售状态"><el-select v-model="filters.saleStatus" clearable placeholder="全部"><el-option label="在售" value="ON_SALE" /><el-option label="未上架" value="OFF_SALE" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <div v-if="auth.hasPermission('catalog:product:manage')" class="product-batch-toolbar">
        <span>已选择 {{ selectedProducts.length }} 个</span>
        <div>
          <el-button :disabled="!selectedProducts.length" :loading="batchSubmitting" @click="batchSaleStatus('ON_SALE')">批量上架</el-button>
          <el-button type="warning" plain :disabled="!selectedProducts.length" :loading="batchSubmitting" @click="batchSaleStatus('OFF_SALE')">批量下架</el-button>
        </div>
      </div>
      <el-table v-loading="loading" :data="rows" stripe row-key="id" @selection-change="selectionChanged">
        <el-table-column v-if="auth.hasPermission('catalog:product:manage')" type="selection" width="48" />
        <el-table-column prop="code" label="产品编号" width="140" /><el-table-column prop="name" label="产品名称" min-width="180" />
        <el-table-column prop="categoryName" label="分类" width="120" /><el-table-column prop="unitName" label="单位" width="80" />
        <el-table-column prop="barcode" label="条码" width="150" /><el-table-column label="成本" width="110" align="right"><template #default="scope">{{ formatMoney(scope.row.costPrice) }}</template></el-table-column>
        <el-table-column label="标准价" width="110" align="right"><template #default="scope">{{ formatMoney(scope.row.salePrice) }}</template></el-table-column><el-table-column label="门店价" width="110" align="right"><template #default="scope">{{ formatMoney(scope.row.storePrice) }}</template></el-table-column>
        <el-table-column label="库存" width="90"><template #default="scope">{{ scope.row.trackStock ? '跟踪' : '不跟踪' }}</template></el-table-column>
        <el-table-column label="销售状态" width="100"><template #default="scope"><el-tag :type="scope.row.saleStatus === 'ON_SALE' ? 'success' : 'info'">{{ scope.row.saleStatus === 'ON_SALE' ? '在售' : '未上架' }}</el-tag></template></el-table-column>
        <el-table-column v-if="auth.hasPermission('catalog:product:manage')" label="操作" width="90" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑产品' : '新建产品'" width="720px" destroy-on-close>
      <el-form label-width="100px" class="dialog-form-grid">
        <el-form-item label="产品编号" required><el-input v-model="form.code" :disabled="!!editingId" maxlength="64" /></el-form-item><el-form-item label="产品名称" required><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item label="产品分类" required><el-select v-model="form.categoryId"><el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="计量单位" required><el-select v-model="form.unitId"><el-option v-for="item in units" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="条码"><el-input v-model="form.barcode" maxlength="64" /></el-form-item><el-form-item label="跟踪库存"><el-switch v-model="form.trackStock" /></el-form-item>
        <el-form-item label="成本"><el-input-number v-model="form.costPrice" :min="0" :precision="2" /></el-form-item><el-form-item label="标准售价"><el-input-number v-model="form.salePrice" :min="0" :precision="2" /></el-form-item><el-form-item label="门店售价"><el-input-number v-model="form.storePrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item v-if="!editingId" label="适用门店" required class="dialog-form-wide"><el-select v-model="form.storeIds" multiple class="dialog-full-control"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <template v-else><el-form-item label="维护门店" required><el-select v-model="form.storeId" @change="selectStore"><el-option v-for="store in editStores" :key="store.storeId" :label="store.storeName" :value="store.storeId" /></el-select></el-form-item><el-form-item label="销售状态"><el-select v-model="form.saleStatus"><el-option label="在售" value="ON_SALE" /><el-option label="未上架" value="OFF_SALE" /></el-select></el-form-item><el-form-item label="资料状态"><el-select v-model="form.status"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item></template>
        <el-form-item label="产品说明" class="dialog-form-wide"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="resultVisible" title="批量上下架结果" width="720px">
      <template v-if="batchResult">
        <div class="product-batch-summary">
          <strong>总计 {{ batchResult.total }}</strong>
          <span class="batch-success">成功 {{ batchResult.succeeded }}</span>
          <span>跳过 {{ batchResult.skipped }}</span>
          <span class="batch-failed">失败 {{ batchResult.failed }}</span>
        </div>
        <el-table :data="batchResult.items" max-height="420" stripe>
          <el-table-column prop="productCode" label="产品编号" width="150">
            <template #default="scope">{{ scope.row.productCode ?? `ID ${scope.row.productId}` }}</template>
          </el-table-column>
          <el-table-column prop="productName" label="产品名称" width="160">
            <template #default="scope">{{ scope.row.productName ?? '未找到' }}</template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.status === 'SUCCESS' ? 'success' : scope.row.status === 'FAILED' ? 'danger' : 'info'">
                {{ scope.row.status === 'SUCCESS' ? '成功' : scope.row.status === 'FAILED' ? '失败' : '跳过' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="220" />
        </el-table>
      </template>
      <template #footer><el-button type="primary" @click="resultVisible = false">关闭</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.file-input { display: none; }
.product-batch-toolbar, .product-batch-summary { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.product-batch-summary { justify-content: flex-start; }
.batch-success { color: var(--el-color-success); }
.batch-failed { color: var(--el-color-danger); }
</style>
