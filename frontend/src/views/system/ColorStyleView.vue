<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import {
  addColorStyleAsset,
  colorStyleAssetUrl,
  colorStyleCategoryImageUrl,
  createColorStyle,
  createColorStyleCategory,
  getColorStyle,
  getColorStyleCategories,
  getColorStyles,
  replaceColorStyleCategoryImage,
  updateColorStyle,
  updateColorStyleAsset,
  updateColorStyleCategory,
} from '@/api/colorStyle'
import { useAuthStore } from '@/stores/auth'
import type { ColorStyle, ColorStyleAsset, ColorStyleCategory } from '@/types/api'

const auth = useAuthStore()
const canManage = computed(() => auth.hasPermission('system:color-style:manage'))
const loading = ref(false)
const saving = ref(false)
const categories = ref<ColorStyleCategory[]>([])
const styles = ref<ColorStyle[]>([])
const total = ref(0)
const filters = reactive<{
  categoryId?: number
  keyword: string
  status?: 'ACTIVE' | 'DISABLED'
  page: number
  size: number
}>({ keyword: '', page: 1, size: 20 })

const categoryDialog = ref(false)
const categoryEditingId = ref<number>()
const categoryForm = reactive({
  parentId: undefined as number | undefined,
  code: '', name: '', sortNo: 10,
  status: 'ACTIVE' as 'ACTIVE' | 'DISABLED', version: '',
})

const styleDialog = ref(false)
const styleEditingId = ref<number>()
const styleForm = reactive({
  code: '', name: '', description: '', sortNo: 10,
  status: 'ACTIVE' as 'ACTIVE' | 'DISABLED', categoryIds: [] as number[], version: '',
})

const imageDialog = ref(false)
const imageCategory = ref<ColorStyleCategory>()
const assetDialog = ref(false)
const assetStyle = ref<ColorStyle>()
const selectedFile = ref<File>()
const uploadSortNo = ref(10)

const categoryNames = computed(() => new Map(categories.value.map(item => [item.id, item.name])))
const activeCategories = computed(() => categories.value.filter(item => item.status === 'ACTIVE'))

async function load() {
  loading.value = true
  try {
    const [categoryRows, stylePage] = await Promise.all([
      getColorStyleCategories(),
      getColorStyles({
        categoryId: filters.categoryId,
        keyword: filters.keyword.trim() || undefined,
        status: filters.status,
        page: filters.page,
        size: filters.size,
      }),
    ])
    categories.value = categoryRows
    styles.value = stylePage.items
    total.value = stylePage.total
    filters.page = stylePage.page
    filters.size = stylePage.size
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '线上试色数据加载失败')
  } finally {
    loading.value = false
  }
}

function search() { filters.page = 1; void load() }

function resetFilters() {
  Object.assign(filters, { categoryId: undefined, keyword: '', status: undefined, page: 1 })
  void load()
}

function openCreateCategory() {
  categoryEditingId.value = undefined
  Object.assign(categoryForm, {
    parentId: undefined, code: '', name: '', sortNo: 10, status: 'ACTIVE', version: '',
  })
  categoryDialog.value = true
}

function openEditCategory(value: unknown) {
  const category = value as ColorStyleCategory
  categoryEditingId.value = category.id
  Object.assign(categoryForm, {
    parentId: category.parentId, code: category.code, name: category.name,
    sortNo: category.sortNo, status: category.status, version: category.version,
  })
  categoryDialog.value = true
}

async function saveCategory() {
  if (!categoryForm.name.trim()) { ElMessage.warning('请输入分类名称'); return }
  if (!categoryEditingId.value && !/^[A-Za-z0-9][A-Za-z0-9_-]*$/.test(categoryForm.code)) {
    ElMessage.warning('分类编码只能包含字母、数字、下划线和短横线')
    return
  }
  saving.value = true
  try {
    if (categoryEditingId.value) {
      await updateColorStyleCategory(categoryEditingId.value, {
        parentId: categoryForm.parentId,
        name: categoryForm.name.trim(), sortNo: categoryForm.sortNo,
        status: categoryForm.status, version: categoryForm.version,
      })
      ElMessage.success('试色分类已更新')
    } else {
      await createColorStyleCategory({
        parentId: categoryForm.parentId, code: categoryForm.code.trim(),
        name: categoryForm.name.trim(), sortNo: categoryForm.sortNo,
      })
      ElMessage.success('试色分类已新建')
    }
    categoryDialog.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '试色分类保存失败')
  } finally {
    saving.value = false
  }
}

function openCategoryImage(value: unknown) {
  const category = value as ColorStyleCategory
  imageCategory.value = category
  selectedFile.value = undefined
  imageDialog.value = true
}

function chooseFile(upload: UploadFile) {
  const file = upload.raw
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    ElMessage.warning('只允许选择JPG、PNG或WEBP图片')
    selectedFile.value = undefined
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片不能超过10 MiB')
    selectedFile.value = undefined
    return
  }
  selectedFile.value = file
}

function clearFile() { selectedFile.value = undefined }

async function saveCategoryImage() {
  if (!imageCategory.value || !selectedFile.value) { ElMessage.warning('请选择分类图片'); return }
  saving.value = true
  try {
    await replaceColorStyleCategoryImage(
      imageCategory.value.id, imageCategory.value.version, selectedFile.value,
    )
    ElMessage.success('分类图片已更新')
    imageDialog.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分类图片更新失败')
  } finally {
    saving.value = false
  }
}

function openCreateStyle() {
  if (!activeCategories.value.length) {
    ElMessage.warning('请先新建至少一个启用分类')
    return
  }
  styleEditingId.value = undefined
  Object.assign(styleForm, {
    code: '', name: '', description: '', sortNo: 10,
    status: 'ACTIVE', categoryIds: [] as number[], version: '',
  })
  styleDialog.value = true
}

function openEditStyle(value: unknown) {
  const style = value as ColorStyle
  styleEditingId.value = style.id
  Object.assign(styleForm, {
    code: style.code, name: style.name, description: style.description ?? '',
    sortNo: style.sortNo, status: style.status,
    categoryIds: [...style.categoryIds], version: style.version,
  })
  styleDialog.value = true
}

async function saveStyle() {
  if (!styleForm.name.trim()) { ElMessage.warning('请输入色号名称'); return }
  if (!styleEditingId.value && !/^[A-Za-z0-9][A-Za-z0-9_-]*$/.test(styleForm.code)) {
    ElMessage.warning('色号只能包含字母、数字、下划线和短横线')
    return
  }
  if (!styleForm.categoryIds.length) { ElMessage.warning('至少选择一个分类'); return }
  saving.value = true
  try {
    const common = {
      name: styleForm.name.trim(), description: styleForm.description.trim() || undefined,
      sortNo: styleForm.sortNo, categoryIds: styleForm.categoryIds,
    }
    if (styleEditingId.value) {
      await updateColorStyle(styleEditingId.value, {
        ...common, status: styleForm.status, version: styleForm.version,
      })
      ElMessage.success('色号已更新')
    } else {
      await createColorStyle({ ...common, code: styleForm.code.trim() })
      ElMessage.success('色号已新建')
    }
    styleDialog.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '色号保存失败')
  } finally {
    saving.value = false
  }
}

function openAssets(value: unknown) {
  const style = value as ColorStyle
  assetStyle.value = { ...style, assets: style.assets.map(asset => ({ ...asset })) }
  selectedFile.value = undefined
  uploadSortNo.value = 10
  assetDialog.value = true
}

async function uploadAsset() {
  if (!assetStyle.value || !selectedFile.value) { ElMessage.warning('请选择展示素材'); return }
  saving.value = true
  try {
    await addColorStyleAsset(assetStyle.value.id, uploadSortNo.value, selectedFile.value)
    ElMessage.success('展示素材已上传')
    await refreshAssets(assetStyle.value.id)
    selectedFile.value = undefined
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '展示素材上传失败')
  } finally {
    saving.value = false
  }
}

async function saveAsset(value: unknown) {
  const asset = value as ColorStyleAsset
  if (!assetStyle.value) return
  saving.value = true
  try {
    await updateColorStyleAsset(assetStyle.value.id, asset.id, {
      sortNo: asset.sortNo, status: asset.status, version: asset.version,
    })
    ElMessage.success('素材配置已更新')
    await refreshAssets(assetStyle.value.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '素材配置更新失败')
  } finally {
    saving.value = false
  }
}

async function refreshAssets(styleId: number) {
  const current = await getColorStyle(styleId)
  assetStyle.value = { ...current, assets: current.assets.map(asset => ({ ...asset })) }
  await load()
}

function categoryLabel(value: unknown) {
  const category = value as ColorStyleCategory
  return category.parentId
    ? `${categoryNames.value.get(category.parentId) ?? '未知父级'} / ${category.name}`
    : category.name
}

function styleCategoryNames(value: unknown) {
  const style = value as ColorStyle
  return style.categoryIds.map(id => categoryNames.value.get(id) ?? `#${id}`).join('、')
}

function categoryImageUrl(value: unknown) {
  return colorStyleCategoryImageUrl(value as ColorStyleCategory)
}

function assetImageUrl(value: unknown) {
  return colorStyleAssetUrl(value as ColorStyleAsset)
}

function changePage(value: number) { filters.page = value; void load() }
function changeSize(value: number) { filters.size = value; filters.page = 1; void load() }

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div>
        <h1>线上试色</h1>
        <p>维护分层分类、色号和多张展示素材；图片通过鉴权接口读取。</p>
      </div>
      <div v-if="canManage">
        <el-button @click="openCreateCategory">新建分类</el-button>
        <el-button type="primary" @click="openCreateStyle">新建色号</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon>
      旧系统确认支持分类图片、色号多分类和多素材。合同要求的“导入”尚无甲方模板，当前不提供可能误导数据的临时导入格式。
    </el-alert>

    <el-card class="category-card" shadow="never">
      <template #header><strong>试色分类</strong></template>
      <el-table v-loading="loading" :data="categories" row-key="id" stripe>
        <el-table-column label="图片" width="92">
          <template #default="scope">
            <el-image
              v-if="scope.row.imageFileId"
              class="category-image"
              :src="categoryImageUrl(scope.row)"
              :preview-src-list="[categoryImageUrl(scope.row)]"
              preview-teleported
              fit="cover"
            />
            <span v-else class="muted">未上传</span>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="分类编码" width="150" />
        <el-table-column label="分类名称" min-width="190"><template #default="scope">{{ categoryLabel(scope.row) }}</template></el-table-column>
        <el-table-column prop="sortNo" label="排序" width="70" />
        <el-table-column label="状态" width="85"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="updatedByName" label="操作人" width="110" />
        <el-table-column v-if="canManage" label="操作" width="140" fixed="right">
          <template #default="scope"><el-button link type="primary" @click="openEditCategory(scope.row)">编辑</el-button><el-button link type="primary" @click="openCategoryImage(scope.row)">图片</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="search">
        <el-form-item label="分类"><el-select v-model="filters.categoryId" clearable filterable placeholder="全部" style="width: 190px"><el-option v-for="category in categories" :key="category.id" :label="categoryLabel(category)" :value="category.id" /></el-select></el-form-item>
        <el-form-item label="色号/名称"><el-input v-model="filters.keyword" clearable maxlength="100" style="width: 190px" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="resetFilters">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header><strong>色号与展示素材</strong></template>
      <el-table v-loading="loading" :data="styles" row-key="id" stripe>
        <el-table-column label="素材" width="126">
          <template #default="scope">
            <div class="asset-preview-row">
              <el-image v-for="asset in scope.row.assets.filter((item: ColorStyleAsset) => item.status === 'ACTIVE').slice(0, 2)" :key="asset.id" class="asset-thumb" :src="assetImageUrl(asset)" fit="cover" />
              <span v-if="!scope.row.assets.some((item: ColorStyleAsset) => item.status === 'ACTIVE')" class="muted">未上传</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="色号" width="135" />
        <el-table-column prop="name" label="名称" width="150" />
        <el-table-column label="分类" min-width="190" show-overflow-tooltip><template #default="scope">{{ styleCategoryNames(scope.row) }}</template></el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="sortNo" label="排序" width="70" />
        <el-table-column label="状态" width="85"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column v-if="canManage" label="操作" width="140" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEditStyle(scope.row)">编辑</el-button><el-button link type="primary" @click="openAssets(scope.row)">素材</el-button></template></el-table-column>
      </el-table>
      <el-pagination
        v-if="total > 0"
        class="pagination"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="filters.page"
        :page-size="filters.size"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="changePage"
        @size-change="changeSize"
      />
    </el-card>

    <el-dialog v-model="categoryDialog" :title="categoryEditingId ? '编辑试色分类' : '新建试色分类'" width="560px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="父分类"><el-select v-model="categoryForm.parentId" clearable filterable class="dialog-full-control"><el-option v-for="category in categories.filter(item => item.id !== categoryEditingId)" :key="category.id" :label="categoryLabel(category)" :value="category.id" /></el-select></el-form-item>
        <el-form-item label="分类编码" required><el-input v-model="categoryForm.code" :disabled="Boolean(categoryEditingId)" maxlength="64" /></el-form-item>
        <el-form-item label="分类名称" required><el-input v-model="categoryForm.name" maxlength="100" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sortNo" :min="0" :max="9999" /></el-form-item>
        <el-form-item v-if="categoryEditingId" label="状态"><el-radio-group v-model="categoryForm.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="categoryDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCategory">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="imageDialog" title="更新分类图片" width="500px" destroy-on-close>
      <p>{{ imageCategory?.name }}</p>
      <el-upload :auto-upload="false" :limit="1" accept=".jpg,.jpeg,.png,.webp" :on-change="chooseFile" :on-remove="clearFile"><el-button>选择图片</el-button><template #tip><div class="el-upload__tip">JPG/PNG/WEBP，最大10 MiB。</div></template></el-upload>
      <template #footer><el-button @click="imageDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCategoryImage">保存图片</el-button></template>
    </el-dialog>

    <el-dialog v-model="styleDialog" :title="styleEditingId ? '编辑色号' : '新建色号'" width="620px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="色号" required><el-input v-model="styleForm.code" :disabled="Boolean(styleEditingId)" maxlength="64" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="styleForm.name" maxlength="100" /></el-form-item>
        <el-form-item label="所属分类" required><el-select v-model="styleForm.categoryIds" multiple filterable class="dialog-full-control"><el-option v-for="category in categories" :key="category.id" :label="categoryLabel(category)" :value="category.id" :disabled="category.status !== 'ACTIVE'" /></el-select></el-form-item>
        <el-form-item label="描述"><el-input v-model="styleForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="styleForm.sortNo" :min="0" :max="9999" /></el-form-item>
        <el-form-item v-if="styleEditingId" label="状态"><el-radio-group v-model="styleForm.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="styleDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveStyle">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="assetDialog" :title="`${assetStyle?.name ?? ''} · 展示素材`" width="760px" destroy-on-close>
      <el-alert type="info" :closable="false">每个色号最多20张启用素材；停用素材保留记录和文件，便于恢复与审计。</el-alert>
      <div v-if="canManage" class="asset-uploader">
        <el-upload :auto-upload="false" :limit="1" accept=".jpg,.jpeg,.png,.webp" :on-change="chooseFile" :on-remove="clearFile"><el-button>选择素材</el-button></el-upload>
        <el-input-number v-model="uploadSortNo" :min="0" :max="9999" />
        <el-button type="primary" :loading="saving" @click="uploadAsset">上传</el-button>
      </div>
      <el-table :data="assetStyle?.assets ?? []" row-key="id" class="asset-table">
        <el-table-column label="预览" width="130"><template #default="scope"><el-image class="asset-large-thumb" :src="assetImageUrl(scope.row)" :preview-src-list="[assetImageUrl(scope.row)]" preview-teleported fit="cover" /></template></el-table-column>
        <el-table-column prop="fileName" label="文件" min-width="180" show-overflow-tooltip />
        <el-table-column label="排序" width="120"><template #default="scope"><el-input-number v-model="scope.row.sortNo" :min="0" :max="9999" size="small" /></template></el-table-column>
        <el-table-column label="状态" width="115"><template #default="scope"><el-select v-model="scope.row.status" size="small"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></template></el-table-column>
        <el-table-column v-if="canManage" label="操作" width="80"><template #default="scope"><el-button link type="primary" :loading="saving" @click="saveAsset(scope.row)">保存</el-button></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="assetDialog = false">关闭</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.el-alert { margin-bottom: 16px; }
.category-card { margin-bottom: 16px; }
.category-image { width: 60px; height: 44px; border-radius: 6px; background: #f3f4f6; }
.asset-preview-row { display: flex; gap: 5px; }
.asset-thumb { width: 44px; height: 44px; border-radius: 5px; background: #f3f4f6; }
.asset-large-thumb { width: 100px; height: 76px; border-radius: 6px; background: #f3f4f6; }
.muted { color: #909399; font-size: 12px; }
.asset-uploader { display: flex; align-items: center; gap: 12px; margin: 18px 0; }
.asset-table { margin-top: 12px; }
.pagination { justify-content: flex-end; margin-top: 18px; }
</style>
