<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createAnnouncement,
  getAnnouncement,
  getAnnouncements,
  updateAnnouncement,
} from '@/api/notification'
import { useAuthStore } from '@/stores/auth'
import type { Announcement, AnnouncementStatus } from '@/types/api'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<Announcement[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref<number>()
const query = reactive<{ storeId?: number; keyword: string; status?: AnnouncementStatus; page: number; size: number }>({ keyword: '', page: 1, size: 20 })
const form = reactive({
  title: '', body: '', scopeType: 'STORES' as 'ALL' | 'STORES', storeIds: [] as number[],
  validFrom: '', validTo: '', priority: 0, pinned: false,
  status: 'DRAFT' as AnnouncementStatus, version: '',
})

async function load() {
  loading.value = true
  try {
    const result = await getAnnouncements({ ...query, keyword: query.keyword.trim() || undefined })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '通知公告加载失败')
  } finally { loading.value = false }
}

function resetQuery() { Object.assign(query, { storeId: undefined, keyword: '', status: undefined, page: 1 }); void load() }
function clearForm() {
  Object.assign(form, {
    title: '', body: '', scopeType: 'STORES', storeIds: [auth.user?.currentStoreId].filter(Boolean) as number[],
    validFrom: '', validTo: '', priority: 0, pinned: false, status: 'DRAFT', version: '',
  })
}
function openCreate() { editingId.value = undefined; clearForm(); dialogVisible.value = true }
async function openEdit(value: unknown) {
  try {
    const detail = await getAnnouncement((value as Announcement).id)
    editingId.value = detail.id
    Object.assign(form, {
      title: detail.title, body: detail.body, scopeType: detail.scopeType,
      storeIds: [...detail.storeIds], validFrom: detail.validFrom ?? '', validTo: detail.validTo ?? '',
      priority: detail.priority, pinned: detail.pinned, status: detail.status, version: detail.version,
    })
    dialogVisible.value = true
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '公告详情加载失败') }
}

function validate(): boolean {
  if (!form.title.trim() || !form.body.trim()) { ElMessage.warning('请填写公告标题和内容'); return false }
  if (form.scopeType === 'STORES' && !form.storeIds.length) { ElMessage.warning('至少选择一个接收门店'); return false }
  if (form.validFrom && form.validTo && form.validTo <= form.validFrom) { ElMessage.warning('结束时间必须晚于开始时间'); return false }
  return true
}
function payload() {
  return {
    title: form.title.trim(), body: form.body.trim(), scopeType: form.scopeType,
    storeIds: form.scopeType === 'ALL' ? [] : form.storeIds,
    validFrom: form.validFrom || undefined, validTo: form.validTo || undefined,
    priority: form.priority, pinned: form.pinned, status: form.status,
  }
}
async function save() {
  if (!validate()) return
  saving.value = true
  try {
    if (editingId.value) await updateAnnouncement(editingId.value, { ...payload(), version: form.version })
    else await createAnnouncement(payload())
    ElMessage.success(form.status === 'PUBLISHED' ? '公告已保存并发布' : '公告已保存')
    dialogVisible.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '公告保存失败') }
  finally { saving.value = false }
}
function storeNames(ids: number[]) { return ids.map(id => auth.user?.stores.find(store => store.id === id)?.name ?? `门店${id}`).join('、') }
function dateTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '不限' }
function statusLabel(status: AnnouncementStatus) { return { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' }[status] }
function changePage(page: number) { query.page = page; void load() }
function changeSize(size: number) { query.size = size; query.page = 1; void load() }
onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>通知公告</h1><p>公告发布后进入所选门店的消息中心和工作台通知区；阅读记录按用户保留。</p></div>
      <el-button v-if="auth.hasPermission('system:announcement:manage')" type="primary" @click="openCreate">新建公告</el-button>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="门店"><el-select v-model="query.storeId" clearable placeholder="全部" style="width: 180px"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="关键字"><el-input v-model="query.keyword" clearable maxlength="200" style="width: 190px" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部" style="width: 120px"><el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" /><el-option label="已停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column prop="title" label="标题" min-width="230"><template #default="scope"><strong>{{ scope.row.title }}</strong><el-tag v-if="scope.row.pinned" size="small" type="danger" class="pin-tag">置顶</el-tag></template></el-table-column>
        <el-table-column label="发布范围" min-width="230"><template #default="scope">{{ scope.row.scopeType === 'ALL' ? '全部门店' : storeNames(scope.row.storeIds) }}</template></el-table-column>
        <el-table-column label="有效期" min-width="240"><template #default="scope">{{ dateTime(scope.row.validFrom) }} — {{ dateTime(scope.row.validTo) }}</template></el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 'PUBLISHED' ? 'success' : scope.row.status === 'DRAFT' ? 'info' : 'warning'">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="updatedByName" label="操作人" width="110" />
        <el-table-column label="更新时间" width="155"><template #default="scope">{{ dateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="85" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">{{ auth.hasPermission('system:announcement:manage') ? '编辑' : '查看' }}</el-button></template></el-table-column>
      </el-table>
      <el-pagination :current-page="query.page" :page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @update:current-page="changePage" @update:page-size="changeSize" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑通知公告' : '新建通知公告'" width="720px" destroy-on-close>
      <el-form label-width="90px" :disabled="!auth.hasPermission('system:announcement:manage')">
        <el-form-item label="公告标题" required><el-input v-model="form.title" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="公告内容" required><el-input v-model="form.body" type="textarea" :rows="7" maxlength="4000" show-word-limit /></el-form-item>
        <el-form-item label="发布范围" required><el-radio-group v-model="form.scopeType"><el-radio value="STORES">指定门店</el-radio><el-radio value="ALL">全部门店</el-radio></el-radio-group></el-form-item>
        <el-form-item v-if="form.scopeType === 'STORES'" label="接收门店" required><el-select v-model="form.storeIds" multiple filterable class="dialog-full-control"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.validFrom" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" clearable placeholder="立即生效" class="dialog-full-control" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.validTo" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" clearable placeholder="长期有效" class="dialog-full-control" /></el-form-item>
        <el-form-item label="展示顺序"><el-input-number v-model="form.priority" :min="0" :max="9999" /><el-checkbox v-model="form.pinned" class="pin-checkbox">置顶</el-checkbox></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio v-if="!editingId || form.status === 'DRAFT'" value="DRAFT">草稿</el-radio><el-radio value="PUBLISHED">发布</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">关闭</el-button><el-button v-if="auth.hasPermission('system:announcement:manage')" type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.pin-tag { margin-left: 8px; }
.pin-checkbox { margin-left: 18px; }
</style>
