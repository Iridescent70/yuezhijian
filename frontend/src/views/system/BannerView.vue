<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import {
  createBanner,
  getBanner,
  getBanners,
  managementBannerImageUrl,
  replaceBannerImage,
  updateBanner,
} from '@/api/banner'
import { useAuthStore } from '@/stores/auth'
import type { Banner, BannerLinkType, BannerPositionCode } from '@/types/api'

const auth = useAuthStore()
const positionLabels: Record<BannerPositionCode, string> = {
  PC_HOME: 'PC工作台',
  HOME_SERVICE_HOME: '到家首页',
}
const positionOptions = Object.entries(positionLabels) as Array<[BannerPositionCode, string]>
const linkLabels: Record<BannerLinkType, string> = {
  NONE: '无跳转', INTERNAL: '站内页面', EXTERNAL: '站外HTTPS',
}
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const imageDialogVisible = ref(false)
const editingId = ref<number>()
const replacingId = ref<number>()
const replacingVersion = ref('')
const selectedFile = ref<File>()
const rows = ref<Banner[]>([])
const filters = reactive<{
  positionCode?: BannerPositionCode
  keyword: string
  status?: 'ACTIVE' | 'DISABLED'
}>({ keyword: '' })
const form = reactive({
  positionCode: 'PC_HOME' as BannerPositionCode,
  title: '',
  linkType: 'NONE' as BannerLinkType,
  linkValue: '',
  sortNo: 10,
  validFrom: '',
  validTo: '',
  status: 'ACTIVE' as 'ACTIVE' | 'DISABLED',
  version: '',
})
const fileRequired = computed(() => !editingId.value)

async function load() {
  loading.value = true
  try {
    rows.value = await getBanners({
      positionCode: filters.positionCode,
      keyword: filters.keyword.trim() || undefined,
      status: filters.status,
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '首页图片加载失败')
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(filters, { positionCode: undefined, keyword: '', status: undefined })
  void load()
}

function clearForm() {
  Object.assign(form, {
    positionCode: filters.positionCode ?? 'PC_HOME', title: '', linkType: 'NONE', linkValue: '',
    sortNo: 10, validFrom: '', validTo: '', status: 'ACTIVE', version: '',
  })
  selectedFile.value = undefined
}

function openCreate() {
  editingId.value = undefined
  clearForm()
  dialogVisible.value = true
}

async function openEdit(value: unknown) {
  const row = value as Banner
  try {
    const detail = await getBanner(row.id)
    editingId.value = detail.id
    selectedFile.value = undefined
    Object.assign(form, {
      positionCode: detail.positionCode, title: detail.title, linkType: detail.linkType,
      linkValue: detail.linkValue ?? '', sortNo: detail.sortNo,
      validFrom: detail.validFrom ?? '', validTo: detail.validTo ?? '',
      status: detail.status, version: detail.version,
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '首页图片详情加载失败')
  }
}

function clearSelectedFile() { selectedFile.value = undefined }

function chooseFile(upload: UploadFile) {
  const file = upload.raw
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    ElMessage.warning('只允许选择JPG、PNG或WEBP图片')
    clearSelectedFile()
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片不能超过10 MiB')
    clearSelectedFile()
    return
  }
  selectedFile.value = file
}

function payload() {
  return {
    positionCode: form.positionCode,
    title: form.title.trim(),
    linkType: form.linkType,
    linkValue: form.linkType === 'NONE' ? undefined : form.linkValue.trim(),
    sortNo: form.sortNo,
    validFrom: form.validFrom || undefined,
    validTo: form.validTo || undefined,
  }
}

function validate(): boolean {
  if (!form.title.trim()) { ElMessage.warning('请输入图片名称'); return false }
  if (form.linkType === 'INTERNAL'
      && (!form.linkValue.startsWith('/') || form.linkValue.startsWith('//'))) {
    ElMessage.warning('站内地址必须以单个/开头')
    return false
  }
  if (form.linkType === 'EXTERNAL' && !form.linkValue.startsWith('https://')) {
    ElMessage.warning('站外地址必须使用完整HTTPS地址')
    return false
  }
  if (form.validFrom && form.validTo && form.validTo <= form.validFrom) {
    ElMessage.warning('展示结束时间必须晚于开始时间')
    return false
  }
  if (fileRequired.value && !selectedFile.value) { ElMessage.warning('请选择首页图片'); return false }
  return true
}

async function submit() {
  if (!validate()) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateBanner(editingId.value, { ...payload(), status: form.status, version: form.version })
      ElMessage.success('首页图片配置已更新')
    } else {
      await createBanner(payload(), selectedFile.value as File)
      ElMessage.success('首页图片已新建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '首页图片保存失败')
  } finally {
    saving.value = false
  }
}

function imageUrl(value: unknown) { return managementBannerImageUrl(value as Banner) }

function openReplace(value: unknown) {
  const row = value as Banner
  replacingId.value = row.id
  replacingVersion.value = row.version
  clearSelectedFile()
  imageDialogVisible.value = true
}

async function submitReplacement() {
  if (!replacingId.value || !selectedFile.value) { ElMessage.warning('请选择新图片'); return }
  saving.value = true
  try {
    await replaceBannerImage(replacingId.value, replacingVersion.value, selectedFile.value)
    ElMessage.success('展示图片已替换')
    imageDialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图片替换失败')
  } finally {
    saving.value = false
  }
}

function dateTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '不限' }

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>首页图片</h1><p>维护PC工作台和到家首页展示图；图片正文保存在私有对象存储。</p></div>
      <el-button v-if="auth.hasPermission('system:banner:manage')" type="primary" @click="openCreate">新建首页图片</el-button>
    </div>
    <el-alert type="info" :closable="false" show-icon>
      停用或超出有效期后不再展示。站外链接只允许HTTPS；替换图片后旧文件进入待清理状态，不提供公开对象地址。
    </el-alert>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="展示位置"><el-select v-model="filters.positionCode" clearable placeholder="全部" style="width: 150px"><el-option v-for="[value, label] in positionOptions" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="图片名称"><el-input v-model="filters.keyword" clearable maxlength="200" style="width: 190px" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column label="图片" width="150"><template #default="scope"><el-image class="banner-thumb" :src="imageUrl(scope.row)" fit="cover" :preview-src-list="[imageUrl(scope.row)]" preview-teleported /></template></el-table-column>
        <el-table-column label="位置" width="120"><template #default="scope">{{ positionLabels[scope.row.positionCode as BannerPositionCode] }}</template></el-table-column>
        <el-table-column prop="title" label="名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="跳转" min-width="190" show-overflow-tooltip><template #default="scope">{{ linkLabels[scope.row.linkType as BannerLinkType] }}{{ scope.row.linkValue ? ` · ${scope.row.linkValue}` : '' }}</template></el-table-column>
        <el-table-column prop="sortNo" label="排序" width="70" />
        <el-table-column label="展示期" min-width="245"><template #default="scope">{{ dateTime(scope.row.validFrom) }} — {{ dateTime(scope.row.validTo) }}</template></el-table-column>
        <el-table-column label="状态" width="85"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="updatedByName" label="操作人" width="110" />
        <el-table-column v-if="auth.hasPermission('system:banner:manage')" label="操作" width="130" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button><el-button link type="primary" @click="openReplace(scope.row)">换图</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑首页图片' : '新建首页图片'" width="680px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="展示位置" required><el-select v-model="form.positionCode" class="dialog-full-control"><el-option v-for="[value, label] in positionOptions" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="图片名称" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item v-if="!editingId" label="图片文件" required><el-upload :auto-upload="false" :limit="1" accept=".jpg,.jpeg,.png,.webp" :on-change="chooseFile" :on-remove="clearSelectedFile"><el-button>选择图片</el-button><template #tip><div class="el-upload__tip">JPG/PNG/WEBP，最大10 MiB；当前不强制裁剪尺寸。</div></template></el-upload></el-form-item>
        <el-form-item label="跳转类型" required><el-radio-group v-model="form.linkType"><el-radio value="NONE">无跳转</el-radio><el-radio value="INTERNAL">站内页面</el-radio><el-radio value="EXTERNAL">站外HTTPS</el-radio></el-radio-group></el-form-item>
        <el-form-item v-if="form.linkType !== 'NONE'" label="跳转地址" required><el-input v-model="form.linkValue" maxlength="500" :placeholder="form.linkType === 'INTERNAL' ? '/app/members' : 'https://example.com'" /></el-form-item>
        <el-form-item label="显示顺序"><el-input-number v-model="form.sortNo" :min="0" :max="9999" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.validFrom" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" clearable placeholder="不限制" class="dialog-full-control" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.validTo" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" clearable placeholder="不限制" class="dialog-full-control" /></el-form-item>
        <el-form-item v-if="editingId" label="状态"><el-radio-group v-model="form.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="imageDialogVisible" title="替换展示图片" width="520px" destroy-on-close>
      <el-alert type="warning" :closable="false">保存后立即使用新图片；旧文件不再通过业务接口访问。</el-alert>
      <el-upload class="replace-upload" :auto-upload="false" :limit="1" accept=".jpg,.jpeg,.png,.webp" :on-change="chooseFile" :on-remove="clearSelectedFile"><el-button>选择新图片</el-button></el-upload>
      <template #footer><el-button @click="imageDialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitReplacement">确认替换</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.el-alert { margin-bottom: 16px; }
.banner-thumb { width: 120px; height: 54px; border-radius: 6px; background: #f3f4f6; }
.replace-upload { margin-top: 20px; }
</style>
