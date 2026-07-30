<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSystemParameters, updateSystemParameter } from '@/api/settings'
import { useAuthStore } from '@/stores/auth'
import type { SettingStatus, SystemParameterItem } from '@/types/api'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SystemParameterItem[]>([])
const groups = ref<string[]>([])
const group = ref('')
const dialogVisible = ref(false)
const current = ref<SystemParameterItem>()
const form = reactive({ value: '', status: 'ACTIVE' as SettingStatus, version: '' })
const groupLabels: Record<string, string> = { ASSET: '会员资产', VISIT: '客户回访' }

async function load() {
  loading.value = true
  try {
    rows.value = await getSystemParameters(group.value || undefined)
    if (!group.value) groups.value = [...new Set(rows.value.map(item => item.paramGroup))]
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '系统参数加载失败')
  } finally {
    loading.value = false
  }
}

function edit(rowValue: unknown) {
  const row = rowValue as SystemParameterItem
  current.value = row
  Object.assign(form, { value: row.value, status: row.status, version: row.version })
  dialogVisible.value = true
}

async function save() {
  if (!current.value || !form.value.trim()) return ElMessage.warning('参数值不能为空')
  saving.value = true
  try {
    await updateSystemParameter(current.value.id, {
      value: form.value.trim(), status: form.status, version: form.version,
    })
    dialogVisible.value = false
    ElMessage.success('系统参数已更新，新生成的业务数据将使用新值')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '系统参数保存失败')
  } finally {
    saving.value = false
  }
}

function dateTime(value: string) { return value.replace('T', ' ').slice(0, 16) }
onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>系统参数</h1><p>只展示可维护的非密钥业务参数；修改只影响之后生成的数据，不追改历史账单或回访。</p></div>
    </div>
    <el-alert type="warning" :closable="false" show-icon title="参数修改会改变后续业务计算，请确认影响后再保存；短信、支付、大模型等密钥不在此页面维护。" />
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="参数分组"><el-select v-model="group" clearable placeholder="全部" style="width: 180px"><el-option v-for="item in groups" :key="item" :label="groupLabels[item] || item" :value="item" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column label="分组" width="130"><template #default="scope">{{ groupLabels[scope.row.paramGroup] || scope.row.paramGroup }}</template></el-table-column>
        <el-table-column prop="paramKey" label="参数键" min-width="210" />
        <el-table-column prop="description" label="用途" min-width="260" />
        <el-table-column label="当前值" width="130"><template #default="scope"><strong>{{ scope.row.value }}</strong></template></el-table-column>
        <el-table-column prop="valueType" label="类型" width="100" />
        <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="更新时间" width="150"><template #default="scope">{{ dateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column v-if="auth.hasPermission('system:parameter:manage')" label="操作" width="80" fixed="right"><template #default="scope"><el-button link type="primary" @click="edit(scope.row)">修改</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="修改系统参数" width="520px">
      <el-form v-if="current" label-position="top">
        <el-form-item label="参数"><el-input :model-value="`${current.paramGroup} / ${current.paramKey}`" disabled /></el-form-item>
        <el-form-item label="说明"><span>{{ current.description }}</span></el-form-item>
        <el-form-item label="参数值" required>
          <el-select v-if="current.valueType === 'BOOLEAN'" v-model="form.value" style="width: 100%"><el-option label="true" value="true" /><el-option label="false" value="false" /></el-select>
          <el-input v-else v-model="form.value" :type="current.valueType === 'JSON' ? 'textarea' : 'text'" :rows="5" />
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" active-value="ACTIVE" inactive-value="DISABLED" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">确认保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.el-alert { margin-bottom: 16px; }
</style>
