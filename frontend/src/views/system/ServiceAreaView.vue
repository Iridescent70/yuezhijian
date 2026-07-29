<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createServiceArea, getServiceArea, getServiceAreas, updateServiceArea } from '@/api/serviceArea'
import { useAuthStore } from '@/stores/auth'
import type { ServiceArea } from '@/types/api'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const rows = ref<ServiceArea[]>([])
const filters = reactive<{
  storeId?: number
  keyword: string
  status?: 'ACTIVE' | 'DISABLED'
}>({ storeId: auth.user?.currentStoreId, keyword: '' })
const form = reactive({
  storeId: auth.user?.currentStoreId as number | undefined,
  city: '', district: '', address: '',
  longitude: undefined as number | undefined, latitude: undefined as number | undefined,
  radiusKm: 5, visitFee: 0, status: 'ACTIVE' as 'ACTIVE' | 'DISABLED', version: '',
})

function clearForm() {
  Object.assign(form, {
    storeId: filters.storeId ?? auth.user?.currentStoreId,
    city: '', district: '', address: '', longitude: undefined, latitude: undefined,
    radiusKm: 5, visitFee: 0, status: 'ACTIVE', version: '',
  })
}

async function load() {
  loading.value = true
  try {
    rows.value = await getServiceAreas({
      storeId: filters.storeId,
      keyword: filters.keyword.trim() || undefined,
      status: filters.status,
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '服务小区加载失败')
  } finally {
    loading.value = false
  }
}

function search() { void load() }

function reset() {
  Object.assign(filters, { storeId: auth.user?.currentStoreId, keyword: '', status: undefined })
  void load()
}

function openCreate() {
  editingId.value = undefined
  clearForm()
  dialogVisible.value = true
}

async function openEdit(value: unknown) {
  const row = value as ServiceArea
  try {
    const detail = await getServiceArea(row.id)
    editingId.value = detail.id
    Object.assign(form, {
      storeId: detail.storeId,
      city: detail.city,
      district: detail.district,
      address: detail.address,
      longitude: detail.longitude,
      latitude: detail.latitude,
      radiusKm: detail.radiusKm,
      visitFee: detail.visitFee,
      status: detail.status,
      version: detail.version,
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '服务小区详情加载失败')
  }
}

function validate(): string | undefined {
  if (!form.storeId) return '请选择服务门店'
  if (!form.city.trim()) return '请输入城市'
  if (!form.district.trim()) return '请输入区域'
  if (!form.address.trim()) return '请输入详细地址'
  if (form.longitude == null) return '请输入中心经度'
  if (form.latitude == null) return '请输入中心纬度'
  if (form.longitude < -180 || form.longitude > 180) return '经度必须在-180到180之间'
  if (form.latitude < -90 || form.latitude > 90) return '纬度必须在-90到90之间'
  if (form.radiusKm < 0.001 || form.radiusKm > 200) return '服务半径必须在0.001到200公里之间'
  if (form.visitFee < 0) return '上门费不能小于0'
  return undefined
}

async function submit() {
  const invalid = validate()
  if (invalid) { ElMessage.warning(invalid); return }
  saving.value = true
  try {
    const common = {
      city: form.city.trim(), district: form.district.trim(), address: form.address.trim(),
      longitude: form.longitude!, latitude: form.latitude!,
      radiusKm: form.radiusKm, visitFee: form.visitFee,
    }
    if (editingId.value) {
      await updateServiceArea(editingId.value, {
        ...common, status: form.status, version: form.version,
      })
      ElMessage.success('服务小区已更新')
    } else {
      await createServiceArea({ ...common, storeId: form.storeId! })
      ElMessage.success('服务小区已新建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '服务小区保存失败')
  } finally {
    saving.value = false
  }
}

function dateTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '—' }

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>服务小区</h1><p>维护门店可提供到家服务的中心地址、服务半径和上门费。</p></div>
      <el-button v-if="auth.hasPermission('home:service-area:manage')" type="primary" @click="openCreate">新建服务小区</el-button>
    </div>
    <el-alert type="info" :closable="false" show-icon>
      当前支持人工录入经纬度。地图选点、地址解析和范围预览须使用甲方地图Key完成真实联调后启用。
    </el-alert>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="search">
        <el-form-item label="服务门店"><el-select v-model="filters.storeId" clearable placeholder="全部门店" style="width: 190px"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="地址查询"><el-input v-model="filters.keyword" clearable maxlength="300" placeholder="城市、区域或详细地址" style="width: 220px" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column prop="city" label="城市" width="110" />
        <el-table-column prop="district" label="区域" width="120" />
        <el-table-column prop="storeName" label="服务门店" min-width="160" />
        <el-table-column prop="address" label="详细地址" min-width="220" show-overflow-tooltip />
        <el-table-column label="中心坐标" min-width="190"><template #default="scope">{{ scope.row.longitude }}, {{ scope.row.latitude }}</template></el-table-column>
        <el-table-column label="服务半径" width="105"><template #default="scope">{{ scope.row.radiusKm }} km</template></el-table-column>
        <el-table-column label="上门费" width="100"><template #default="scope">¥{{ Number(scope.row.visitFee).toFixed(2) }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="updatedByName" label="操作人" width="120" />
        <el-table-column label="操作时间" width="170"><template #default="scope">{{ dateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column v-if="auth.hasPermission('home:service-area:manage')" label="操作" width="80" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑服务小区' : '新建服务小区'" width="680px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="服务门店" required><el-select v-model="form.storeId" :disabled="Boolean(editingId)" class="dialog-full-control"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <div class="form-grid"><el-form-item label="城市" required><el-input v-model="form.city" maxlength="64" /></el-form-item><el-form-item label="区域" required><el-input v-model="form.district" maxlength="64" /></el-form-item></div>
        <el-form-item label="详细地址" required><el-input v-model="form.address" maxlength="300" show-word-limit /></el-form-item>
        <div class="form-grid"><el-form-item label="经度" required><el-input-number v-model="form.longitude" :min="-180" :max="180" :precision="7" :step="0.0001" /></el-form-item><el-form-item label="纬度" required><el-input-number v-model="form.latitude" :min="-90" :max="90" :precision="7" :step="0.0001" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="服务半径" required><el-input-number v-model="form.radiusKm" :min="0.001" :max="200" :precision="3" :step="0.5" /><span class="unit">公里</span></el-form-item><el-form-item label="上门费" required><el-input-number v-model="form.visitFee" :min="0" :precision="2" :step="10" /><span class="unit">元</span></el-form-item></div>
        <el-form-item v-if="editingId" label="状态"><el-radio-group v-model="form.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.el-alert { margin-bottom: 16px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.form-grid :deep(.el-input-number) { width: 180px; }
.unit { margin-left: 8px; color: var(--el-text-color-secondary); }
@media (max-width: 760px) { .form-grid { grid-template-columns: 1fr; } }
</style>
