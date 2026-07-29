<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createPaymentMethod,
  getPaymentMethod,
  getPaymentMethodConfigurations,
  sortPaymentMethods,
  updatePaymentMethod,
  updatePaymentMethodStore,
} from '@/api/payment'
import { useAuthStore } from '@/stores/auth'
import type {
  PaymentMethodConfiguration,
  PaymentMethodStoreConfiguration,
  PaymentMethodType,
} from '@/types/api'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const configuringId = ref<number>()
const dialogVisible = ref(false)
const editingId = ref<number>()
const rows = ref<PaymentMethodConfiguration[]>([])
const filters = reactive({
  keyword: '', type: '' as PaymentMethodType | '', status: '' as '' | 'ACTIVE' | 'DISABLED',
  storeId: auth.user?.currentStoreId ?? auth.user?.stores[0]?.id,
})
const form = reactive({
  code: '', name: '', type: 'CASH' as PaymentMethodType, electronic: false,
  includedInRevenue: true, needsExternalReference: false,
  status: 'ACTIVE' as 'ACTIVE' | 'DISABLED', storeIds: [] as number[], version: '',
})

const typeLabels: Record<PaymentMethodType, string> = {
  CASH: '现金', BANK_CARD: '银行卡', WECHAT: '微信', ALIPAY: '支付宝',
  MEITUAN: '美团', STORED_VALUE: '储值支付', OTHER: '其他',
}
const typeOptions = Object.entries(typeLabels) as Array<[PaymentMethodType, string]>
const canManageDefinition = computed(() => auth.hasPermission('catalog:payment:manage'))
const canManageStore = computed(() => auth.hasPermission('catalog:payment:store-manage'))
const canReorder = computed(() => canManageStore.value
  && !filters.keyword.trim() && !filters.type && !filters.status)
const applicableRows = computed(() => rows.value.filter(row => storeConfig(row).applicable))

function storeConfig(value: unknown): PaymentMethodStoreConfiguration {
  const row = value as PaymentMethodConfiguration
  return row.stores[0] ?? {
    storeId: filters.storeId ?? 0, storeCode: '', storeName: '', applicable: false,
    enabled: false, sortNo: 0,
  }
}

async function load() {
  if (!filters.storeId) return
  loading.value = true
  try {
    rows.value = await getPaymentMethodConfigurations({
      keyword: filters.keyword.trim() || undefined,
      type: filters.type || undefined,
      status: filters.status || undefined,
      storeId: filters.storeId,
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '支付方式加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    code: '', name: '', type: 'CASH', electronic: false, includedInRevenue: true,
    needsExternalReference: false, status: 'ACTIVE',
    storeIds: filters.storeId ? [filters.storeId] : [], version: '',
  })
  dialogVisible.value = true
}

async function openEdit(value: unknown) {
  const row = value as PaymentMethodConfiguration
  try {
    const detail = await getPaymentMethod(row.id)
    editingId.value = detail.id
    Object.assign(form, {
      code: detail.code, name: detail.name, type: detail.type, electronic: detail.electronic,
      includedInRevenue: detail.includedInRevenue,
      needsExternalReference: detail.needsExternalReference,
      status: detail.status, storeIds: detail.stores.filter(item => item.applicable).map(item => item.storeId),
      version: detail.version,
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '支付方式详情加载失败')
  }
}

async function submit() {
  if (!form.code.trim() || !form.name.trim()) {
    ElMessage.warning('请填写支付方式编号和名称')
    return
  }
  if (!editingId.value && !form.storeIds.length) {
    ElMessage.warning('至少选择一个适用门店')
    return
  }
  saving.value = true
  try {
    const common = {
      name: form.name, type: form.type, electronic: form.electronic,
      includedInRevenue: form.includedInRevenue,
      needsExternalReference: form.needsExternalReference, status: form.status,
    }
    if (editingId.value) {
      await updatePaymentMethod(editingId.value, { ...common, version: form.version })
      ElMessage.success('支付方式定义已更新')
    } else {
      await createPaymentMethod({ ...common, code: form.code, storeIds: form.storeIds })
      ElMessage.success('支付方式已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '支付方式保存失败')
  } finally {
    saving.value = false
  }
}

async function configure(value: unknown, changes: Partial<PaymentMethodStoreConfiguration>) {
  const row = value as PaymentMethodConfiguration
  if (!filters.storeId) return
  const current = storeConfig(row)
  configuringId.value = row.id
  try {
    await updatePaymentMethodStore(row.id, filters.storeId, {
      applicable: changes.applicable ?? current.applicable,
      enabled: changes.enabled ?? current.enabled,
      sortNo: changes.sortNo ?? current.sortNo,
      version: current.version,
    })
    ElMessage.success('门店支付配置已更新')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '门店支付配置更新失败')
  } finally {
    configuringId.value = undefined
  }
}

async function move(value: unknown, offset: number) {
  const row = value as PaymentMethodConfiguration
  if (!filters.storeId) return
  const ordered = [...applicableRows.value]
  const index = ordered.findIndex(item => item.id === row.id)
  const target = index + offset
  if (index < 0 || target < 0 || target >= ordered.length) return
  ;[ordered[index], ordered[target]] = [ordered[target], ordered[index]]
  configuringId.value = row.id
  try {
    rows.value = await sortPaymentMethods({
      storeId: filters.storeId,
      items: ordered.map((item, itemIndex) => ({
        paymentMethodId: item.id,
        sortNo: (itemIndex + 1) * 10,
        version: storeConfig(item).version ?? '',
      })),
    })
    ElMessage.success('显示顺序已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '支付方式排序失败')
    await load()
  } finally {
    configuringId.value = undefined
  }
}

function typeChanged(type: PaymentMethodType) {
  if (['WECHAT', 'ALIPAY', 'MEITUAN', 'BANK_CARD'].includes(type)) form.electronic = true
  if (['CASH', 'STORED_VALUE'].includes(type)) form.electronic = false
}

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>支付方式</h1><p>总部维护支付定义，门店配置是否适用、启用状态和收银显示顺序。</p></div>
      <el-button v-if="canManageDefinition" type="primary" @click="openCreate">新建支付方式</el-button>
    </div>
    <el-alert type="info" :closable="false" show-icon>
      储值支付通过会员资产抵扣完成，不会作为外部支付重复提交；微信、支付宝等真实通道参数仍由独立集成配置管理。
    </el-alert>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="门店"><el-select v-model="filters.storeId" style="width: 180px" @change="load"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="查询"><el-input v-model="filters.keyword" clearable placeholder="编号或名称" style="width: 180px" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="filters.type" clearable placeholder="全部" style="width: 140px"><el-option v-for="[value, label] in typeOptions" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="定义状态"><el-select v-model="filters.status" clearable placeholder="全部" style="width: 130px"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column prop="code" label="编号" width="145" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column label="类型" width="110"><template #default="scope">{{ typeLabels[scope.row.type as PaymentMethodType] ?? scope.row.type }}</template></el-table-column>
        <el-table-column label="电子支付" width="90"><template #default="scope">{{ scope.row.electronic ? '是' : '否' }}</template></el-table-column>
        <el-table-column label="计入营业额" width="100"><template #default="scope">{{ scope.row.includedInRevenue ? '是' : '否' }}</template></el-table-column>
        <el-table-column label="需外部凭证" width="110"><template #default="scope">{{ scope.row.needsExternalReference ? '是' : '否' }}</template></el-table-column>
        <el-table-column label="适用本店" width="105"><template #default="scope"><el-switch :model-value="storeConfig(scope.row).applicable" :disabled="!canManageStore || configuringId === scope.row.id" @change="value => configure(scope.row, { applicable: Boolean(value), enabled: Boolean(value) })" /></template></el-table-column>
        <el-table-column label="本店启用" width="105"><template #default="scope"><el-switch :model-value="storeConfig(scope.row).enabled" :disabled="!canManageStore || !storeConfig(scope.row).applicable || scope.row.status !== 'ACTIVE' || configuringId === scope.row.id" @change="value => configure(scope.row, { enabled: Boolean(value) })" /></template></el-table-column>
        <el-table-column label="顺序" width="120"><template #default="scope"><div v-if="storeConfig(scope.row).applicable" class="sort-actions"><span>{{ storeConfig(scope.row).sortNo }}</span><template v-if="canReorder"><el-button link :disabled="configuringId === scope.row.id" @click="move(scope.row, -1)">上移</el-button><el-button link :disabled="configuringId === scope.row.id" @click="move(scope.row, 1)">下移</el-button></template></div><span v-else>—</span></template></el-table-column>
        <el-table-column label="定义状态" width="95"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column v-if="canManageDefinition" label="操作" width="80" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑支付方式' : '新建支付方式'" width="650px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="支付编号" required><el-input v-model="form.code" maxlength="64" :disabled="Boolean(editingId)" placeholder="大写字母、数字或下划线" /></el-form-item>
        <el-form-item label="支付名称" required><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="支付类型" required><el-select v-model="form.type" class="dialog-full-control" @change="typeChanged"><el-option v-for="[value, label] in typeOptions" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="电子支付"><el-switch v-model="form.electronic" /></el-form-item>
        <el-form-item label="计入营业额"><el-switch v-model="form.includedInRevenue" /></el-form-item>
        <el-form-item label="需外部凭证"><el-switch v-model="form.needsExternalReference" /></el-form-item>
        <el-form-item label="定义状态"><el-radio-group v-model="form.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item v-if="!editingId" label="初始适用门店" required><el-select v-model="form.storeIds" multiple class="dialog-full-control"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.sort-actions { display: flex; align-items: center; gap: 4px; white-space: nowrap; }
.el-alert { margin-bottom: 16px; }
</style>
