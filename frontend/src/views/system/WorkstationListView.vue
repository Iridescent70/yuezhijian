<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createWorkstation, getWorkstations } from '@/api/masterData'
import { useAuthStore } from '@/stores/auth'
import type { WorkstationSummary } from '@/types/api'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const workstations = ref<WorkstationSummary[]>([])
const storeId = ref<number | undefined>()
const form = reactive({ storeId: auth.user?.currentStoreId as number | undefined, code: '', name: '', capacity: 1, sortNo: 10 })

async function load() {
  loading.value = true
  try { workstations.value = await getWorkstations(storeId.value) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '工位加载失败') }
  finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, { storeId: storeId.value ?? auth.user?.currentStoreId, code: '', name: '', capacity: 1, sortNo: 10 })
  dialogVisible.value = true
}

async function submit() {
  if (!form.storeId || !form.code.trim() || !form.name.trim()) {
    ElMessage.warning('请填写门店、工位编号和名称')
    return
  }
  saving.value = true
  try {
    await createWorkstation({ storeId: form.storeId, code: form.code, name: form.name, capacity: form.capacity, sortNo: form.sortNo })
    ElMessage.success('工位已创建')
    dialogVisible.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '工位创建失败') }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>工位管理</h1><p>维护预约排期和到店服务使用的门店工位。</p></div>
      <el-button v-if="auth.hasPermission('org:workstation:manage')" type="primary" @click="openCreate">新建工位</el-button>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline><el-form-item label="所属门店"><el-select v-model="storeId" clearable placeholder="全部门店" class="master-filter-select"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item></el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="workstations" stripe row-key="id">
        <el-table-column prop="code" label="工位编号" width="150" /><el-table-column prop="name" label="工位名称" min-width="200" /><el-table-column prop="storeName" label="所属门店" min-width="180" /><el-table-column prop="capacity" label="容量" width="100" /><el-table-column prop="sortNo" label="排序" width="100" /><el-table-column label="状态" width="100"><template #default="scope"><el-tag type="success">{{ scope.row.status === 'ACTIVE' ? '启用' : scope.row.status }}</el-tag></template></el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialogVisible" title="新建工位" width="560px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="所属门店" required><el-select v-model="form.storeId" class="dialog-full-control"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="工位编号" required><el-input v-model="form.code" maxlength="64" /></el-form-item>
        <el-form-item label="工位名称" required><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="容量"><el-input-number v-model="form.capacity" :min="1" :max="100" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" :max="9999" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </section>
</template>
