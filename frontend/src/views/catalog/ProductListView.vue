<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createProduct, getProduct, getProductCategories, getProducts, getUnits, updateProduct } from '@/api/product'
import { useAuthStore } from '@/stores/auth'
import type { CategoryOption, ProductStoreConfig, ProductSummary, UnitOption } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const auth = useAuthStore()
const loading = ref(false)
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
      <el-button v-if="auth.hasPermission('catalog:product:manage')" type="primary" @click="openCreate">新建产品</el-button>
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
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
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
  </section>
</template>
