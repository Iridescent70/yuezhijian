<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createExport } from '@/api/jobs'
import { createService, getService, getServiceCategories, getServices, updateService } from '@/api/masterData'
import { useAuthStore } from '@/stores/auth'
import type { CategoryOption, ServiceItemSummary, ServiceStoreConfig } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const exporting = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const editStores = ref<ServiceStoreConfig[]>([])
const services = ref<ServiceItemSummary[]>([])
const categories = ref<CategoryOption[]>([])
const filters = reactive<{ keyword: string; storeId?: number }>({ keyword: '', storeId: undefined })
const form = reactive({
  code: '', name: '', categoryId: undefined as number | undefined, durationMinutes: 60,
  costAmount: 0, listPrice: 0, storePrice: 0, storeIds: [] as number[], description: '',
  storeId: undefined as number | undefined, saleStatus: 'ON_SALE', status: 'ACTIVE', version: '',
})

async function load() {
  loading.value = true
  try {
    ;[services.value, categories.value] = await Promise.all([
      getServices({ keyword: filters.keyword || undefined, storeId: filters.storeId }), getServiceCategories(),
    ])
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '服务项目加载失败') }
  finally { loading.value = false }
}

async function exportCurrentStore() {
  if (filters.storeId && filters.storeId !== auth.user?.currentStoreId) {
    ElMessage.warning('服务项目导出固定使用当前登录门店，请切换门店后再导出')
    return
  }
  exporting.value = true
  try {
    await createExport({
      exportType: 'SERVICE_CATALOG',
      keyword: filters.keyword.trim() || undefined,
    })
    ElMessage.success('服务项目导出任务已创建')
    await router.push('/app/system/downloads')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '服务项目导出任务创建失败')
  } finally {
    exporting.value = false
  }
}

function openCreate() {
  editingId.value = undefined
  editStores.value = []
  Object.assign(form, {
    code: '', name: '', categoryId: categories.value[0]?.id, durationMinutes: 60,
    costAmount: 0, listPrice: 0, storePrice: 0,
    storeIds: auth.user?.currentStoreId ? [auth.user.currentStoreId] : [], description: '',
    storeId: undefined, saleStatus: 'ON_SALE', status: 'ACTIVE', version: '',
  })
  dialogVisible.value = true
}

async function openEdit(value: unknown) {
  const row = value as ServiceItemSummary
  saving.value = true
  try {
    const detail = await getService(row.id)
    const preferredStoreId = filters.storeId ?? auth.user?.currentStoreId
    const store = detail.stores.find((item) => item.storeId === preferredStoreId) ?? detail.stores[0]
    if (!store) throw new Error('服务项目没有可维护的门店配置')
    editingId.value = detail.id
    editStores.value = detail.stores
    Object.assign(form, {
      code: detail.code, name: detail.name, categoryId: detail.categoryId,
      durationMinutes: detail.durationMinutes, costAmount: detail.costAmount,
      listPrice: detail.listPrice, storePrice: store.storePrice, storeIds: [],
      description: detail.description ?? '', storeId: store.storeId,
      saleStatus: store.saleStatus, status: detail.status, version: detail.version,
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '服务项目详情加载失败')
  } finally {
    saving.value = false
  }
}

function selectEditStore(storeId: number) {
  const store = editStores.value.find((item) => item.storeId === storeId)
  if (!store) return
  form.storePrice = store.storePrice
  form.saleStatus = store.saleStatus
}

async function submit() {
  if (!form.code.trim() || !form.name.trim() || !form.categoryId
    || (editingId.value ? !form.storeId : !form.storeIds.length)) {
    ElMessage.warning('请填写编号、名称、分类和门店')
    return
  }
  saving.value = true
  try {
    if (editingId.value && form.storeId) {
      await updateService(editingId.value, {
        name: form.name, categoryId: form.categoryId, durationMinutes: form.durationMinutes,
        costAmount: form.costAmount, listPrice: form.listPrice, storeId: form.storeId,
        storePrice: form.storePrice, saleStatus: form.saleStatus, status: form.status,
        description: form.description || undefined, version: form.version,
      })
      ElMessage.success('服务项目已更新')
    } else {
      await createService({
        code: form.code, name: form.name, categoryId: form.categoryId,
        durationMinutes: form.durationMinutes, costAmount: form.costAmount,
        listPrice: form.listPrice, storePrice: form.storePrice,
        storeIds: form.storeIds, description: form.description || undefined,
      })
      ElMessage.success('服务项目已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '服务项目创建失败') }
  finally { saving.value = false }
}

onMounted(() => {
  filters.storeId = auth.user?.currentStoreId
  void load()
})
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>服务项目</h1><p>统一维护服务时长、标准售价和门店销售价格。</p></div>
      <div>
        <el-button
          v-if="auth.hasPermission('system:job:create') && auth.hasPermission('system:job:view') && auth.hasPermission('catalog:service:export')"
          :loading="exporting"
          @click="exportCurrentStore"
        >导出当前门店</el-button>
        <el-button v-if="auth.hasPermission('catalog:service:manage')" type="primary" @click="openCreate">新建服务</el-button>
      </div>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load"><el-form-item label="项目查询"><el-input v-model="filters.keyword" clearable placeholder="编号或名称" /></el-form-item><el-form-item label="适用门店"><el-select v-model="filters.storeId" clearable placeholder="全部门店" class="master-filter-select"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item></el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="services" stripe row-key="id">
        <el-table-column prop="code" label="项目编号" width="140" /><el-table-column prop="name" label="项目名称" min-width="210" /><el-table-column prop="categoryName" label="分类" width="140" /><el-table-column prop="durationMinutes" label="时长(分钟)" width="110" /><el-table-column label="成本" width="120" align="right"><template #default="scope">{{ formatMoney(scope.row.costAmount) }}</template></el-table-column><el-table-column label="标准价" width="120" align="right"><template #default="scope">{{ formatMoney(scope.row.listPrice) }}</template></el-table-column><el-table-column label="门店价" width="120" align="right"><template #default="scope">{{ formatMoney(scope.row.storePrice) }}</template></el-table-column><el-table-column label="销售状态" width="110"><template #default="scope"><el-tag :type="scope.row.saleStatus === 'ON_SALE' ? 'success' : 'info'">{{ scope.row.saleStatus === 'ON_SALE' ? '在售' : '未上架' }}</el-tag></template></el-table-column><el-table-column v-if="auth.hasPermission('catalog:service:manage')" label="操作" width="90" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑服务项目' : '新建服务项目'" width="700px" destroy-on-close>
      <el-form label-width="100px" class="dialog-form-grid">
        <el-form-item label="项目编号" required><el-input v-model="form.code" :disabled="!!editingId" maxlength="64" /></el-form-item><el-form-item label="项目名称" required><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item label="服务分类" required><el-select v-model="form.categoryId"><el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="服务时长"><el-input-number v-model="form.durationMinutes" :min="5" :max="1440" :step="5" /></el-form-item>
        <el-form-item label="服务成本"><el-input-number v-model="form.costAmount" :min="0" :precision="2" /></el-form-item><el-form-item label="标准售价"><el-input-number v-model="form.listPrice" :min="0" :precision="2" /></el-form-item><el-form-item label="门店售价"><el-input-number v-model="form.storePrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item v-if="!editingId" label="适用门店" required class="dialog-form-wide"><el-select v-model="form.storeIds" multiple class="dialog-full-control"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <template v-else>
          <el-form-item label="维护门店" required><el-select v-model="form.storeId" @change="selectEditStore"><el-option v-for="store in editStores" :key="store.storeId" :label="store.storeName" :value="store.storeId" /></el-select></el-form-item>
          <el-form-item label="销售状态"><el-select v-model="form.saleStatus"><el-option label="在售" value="ON_SALE" /><el-option label="未上架" value="OFF_SALE" /></el-select></el-form-item>
          <el-form-item label="项目状态"><el-select v-model="form.status"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        </template>
        <el-form-item label="项目说明" class="dialog-form-wide"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </section>
</template>
