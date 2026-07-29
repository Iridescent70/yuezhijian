<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createNotificationTemplate,
  getNotificationTemplate,
  getNotificationTemplates,
  sendTestNotification,
  updateNotificationTemplate,
} from '@/api/notification'
import { useAuthStore } from '@/stores/auth'
import type { NotificationTemplate } from '@/types/api'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<NotificationTemplate[]>([])
const dialogVisible = ref(false)
const testVisible = ref(false)
const editingId = ref<number>()
const testing = ref<NotificationTemplate>()
const query = reactive<{ keyword: string; status?: 'ACTIVE' | 'DISABLED' }>({ keyword: '' })
const form = reactive({
  eventCode: '', eventName: '', titleTemplate: '', bodyTemplate: '', variablesText: '',
  status: 'ACTIVE' as 'ACTIVE' | 'DISABLED', version: '',
})
const testValues = reactive<Record<string, string>>({})
const variables = computed(() => form.variablesText.split(',').map(item => item.trim()).filter(Boolean))

async function load() {
  loading.value = true
  try { rows.value = await getNotificationTemplates({ keyword: query.keyword.trim() || undefined, status: query.status }) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '通知模板加载失败') }
  finally { loading.value = false }
}
function resetQuery() { Object.assign(query, { keyword: '', status: undefined }); void load() }
function clearForm() { Object.assign(form, { eventCode: '', eventName: '', titleTemplate: '', bodyTemplate: '', variablesText: '', status: 'ACTIVE', version: '' }) }
function openCreate() { editingId.value = undefined; clearForm(); dialogVisible.value = true }
async function openEdit(value: unknown) {
  try {
    const detail = await getNotificationTemplate((value as NotificationTemplate).id)
    editingId.value = detail.id
    Object.assign(form, {
      eventCode: detail.eventCode, eventName: detail.eventName, titleTemplate: detail.titleTemplate,
      bodyTemplate: detail.bodyTemplate, variablesText: detail.variables.join(','),
      status: detail.status, version: detail.version,
    })
    dialogVisible.value = true
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '模板详情加载失败') }
}
function validate(): boolean {
  if (!form.eventCode.trim() || !form.eventName.trim() || !form.titleTemplate.trim() || !form.bodyTemplate.trim()) {
    ElMessage.warning('事件、标题模板和正文模板不能为空'); return false
  }
  if (new Set(variables.value).size !== variables.value.length) { ElMessage.warning('模板变量不能重复'); return false }
  return true
}
async function save() {
  if (!validate()) return
  saving.value = true
  const payload = {
    eventCode: form.eventCode.trim().toUpperCase(), eventName: form.eventName.trim(),
    titleTemplate: form.titleTemplate.trim(), bodyTemplate: form.bodyTemplate.trim(),
    variables: variables.value, status: form.status,
  }
  try {
    if (editingId.value) {
      const { eventCode: _eventCode, ...update } = payload
      await updateNotificationTemplate(editingId.value, { ...update, version: form.version })
    } else await createNotificationTemplate(payload)
    ElMessage.success('通知模板已保存')
    dialogVisible.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '通知模板保存失败') }
  finally { saving.value = false }
}
function openTest(value: unknown) {
  testing.value = value as NotificationTemplate
  Object.keys(testValues).forEach(key => delete testValues[key])
  testing.value.variables.forEach(key => { testValues[key] = `示例${key}` })
  testVisible.value = true
}
async function sendTest() {
  if (!testing.value) return
  saving.value = true
  try {
    const result = await sendTestNotification(testing.value.id, { ...testValues })
    ElMessage.success(`测试消息已发送：${result.notificationNo}`)
    testVisible.value = false
    window.dispatchEvent(new Event('notification-read'))
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '测试消息发送失败') }
  finally { saving.value = false }
}
function dateTime(value: string) { return value.replace('T', ' ').slice(0, 16) }
onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>通知模板</h1><p>当前只发送站内消息；变量使用 <code v-pre>{{variable}}</code>，保存时会校验声明和引用。</p></div>
      <el-button v-if="auth.hasPermission('system:notification-template:manage')" type="primary" @click="openCreate">新建模板</el-button>
    </div>
    <el-alert type="warning" :closable="false" show-icon title="停用业务事件模板后，该事件不会再生成新消息；已经生成的消息和阅读记录不受影响。" />
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="事件"><el-input v-model="query.keyword" clearable maxlength="200" placeholder="编码或名称" style="width: 210px" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部" style="width: 120px"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column prop="eventCode" label="事件编码" min-width="170" />
        <el-table-column prop="eventName" label="事件名称" min-width="150" />
        <el-table-column prop="titleTemplate" label="标题模板" min-width="230" show-overflow-tooltip />
        <el-table-column label="变量" min-width="250"><template #default="scope"><el-tag v-for="item in scope.row.variables" :key="item" size="small" class="variable-tag">{{ item }}</el-tag><span v-if="!scope.row.variables.length">无变量</span></template></el-table-column>
        <el-table-column label="状态" width="85"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="updatedByName" label="操作人" width="110" />
        <el-table-column label="更新时间" width="150"><template #default="scope">{{ dateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="130" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">{{ auth.hasPermission('system:notification-template:manage') ? '编辑' : '查看' }}</el-button><el-button v-if="auth.hasPermission('system:notification-template:manage') && scope.row.status === 'ACTIVE'" link type="primary" @click="openTest(scope.row)">测试</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑通知模板' : '新建通知模板'" width="720px" destroy-on-close>
      <el-form label-width="100px" :disabled="!auth.hasPermission('system:notification-template:manage')">
        <el-form-item label="事件编码" required><el-input v-model="form.eventCode" :disabled="Boolean(editingId)" maxlength="64" placeholder="如 APPOINTMENT_REMINDER" /></el-form-item>
        <el-form-item label="事件名称" required><el-input v-model="form.eventName" maxlength="100" /></el-form-item>
        <el-form-item label="变量清单"><el-input v-model="form.variablesText" maxlength="1000" placeholder="英文逗号分隔，如 billNo,storeName" /><div class="form-tip">变量名使用小驼峰英文；正文中按 <code v-pre>{{billNo}}</code> 引用。</div></el-form-item>
        <el-form-item label="标题模板" required><el-input v-model="form.titleTemplate" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="正文模板" required><el-input v-model="form.bodyTemplate" type="textarea" :rows="7" maxlength="4000" show-word-limit /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">关闭</el-button><el-button v-if="auth.hasPermission('system:notification-template:manage')" type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="testVisible" title="发送站内测试消息" width="560px" destroy-on-close>
      <el-alert type="info" :closable="false">测试消息只发送到当前门店的消息中心，不调用短信或微信通道。</el-alert>
      <el-form v-if="testing" label-width="130px" class="test-form">
        <el-form-item v-for="variable in testing.variables" :key="variable" :label="variable" required><el-input v-model="testValues[variable]" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="testVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="sendTest">发送测试</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.el-alert { margin-bottom: 16px; }
.variable-tag { margin: 2px 5px 2px 0; }
.form-tip { color: var(--muted); font-size: 12px; }
.test-form { margin-top: 18px; }
</style>
