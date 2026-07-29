<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createCatalogUnit,
  createItemCategory,
  getCatalogUnit,
  getCatalogUnits,
  getItemCategories,
  getItemCategory,
  updateCatalogUnit,
  updateItemCategory,
} from '@/api/masterData'
import { useAuthStore } from '@/stores/auth'
import type { CategoryOption, UnitOption } from '@/types/api'

type CategoryType = 'PRODUCT' | 'SERVICE'
type TabName = CategoryType | 'UNIT'

const auth = useAuthStore()
const activeTab = ref<TabName>('PRODUCT')
const loading = ref(false)
const saving = ref(false)
const categories = ref<CategoryOption[]>([])
const units = ref<UnitOption[]>([])
const categoryDialogVisible = ref(false)
const unitDialogVisible = ref(false)
const editingCategoryId = ref<number>()
const editingUnitId = ref<number>()

const categoryForm = reactive({
  type: 'PRODUCT' as CategoryType,
  code: '',
  name: '',
  sortNo: 10,
  status: 'ACTIVE',
  version: '',
})
const unitForm = reactive({ code: '', name: '', decimalPlaces: 0, status: 'ACTIVE', version: '' })

const currentCategories = computed(() =>
  categories.value.filter(category => category.type === activeTab.value),
)
const currentCategoryLabel = computed(() => activeTab.value === 'PRODUCT' ? '产品分类' : '服务分类')

async function load() {
  loading.value = true
  try {
    const [productCategories, serviceCategories, unitRows] = await Promise.all([
      getItemCategories('PRODUCT', false),
      getItemCategories('SERVICE', false),
      getCatalogUnits(false),
    ])
    categories.value = [...productCategories, ...serviceCategories]
    units.value = unitRows
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分类和单位加载失败')
  } finally {
    loading.value = false
  }
}

function openCategoryCreate() {
  const type = activeTab.value === 'SERVICE' ? 'SERVICE' : 'PRODUCT'
  editingCategoryId.value = undefined
  Object.assign(categoryForm, { type, code: '', name: '', sortNo: 10, status: 'ACTIVE', version: '' })
  categoryDialogVisible.value = true
}

async function openCategoryEdit(value: unknown) {
  const row = value as CategoryOption
  try {
    const detail = await getItemCategory(row.id)
    editingCategoryId.value = detail.id
    Object.assign(categoryForm, detail)
    categoryDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分类详情加载失败')
  }
}

async function saveCategory() {
  if (!categoryForm.code.trim() || !categoryForm.name.trim()) {
    ElMessage.warning('请填写分类编号和名称')
    return
  }
  saving.value = true
  try {
    if (editingCategoryId.value) {
      await updateItemCategory(editingCategoryId.value, {
        name: categoryForm.name,
        sortNo: categoryForm.sortNo,
        status: categoryForm.status,
        version: categoryForm.version,
      })
      ElMessage.success('分类资料已更新')
    } else {
      await createItemCategory({
        type: categoryForm.type,
        code: categoryForm.code,
        name: categoryForm.name,
        sortNo: categoryForm.sortNo,
      })
      ElMessage.success('分类已创建')
    }
    categoryDialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分类保存失败')
  } finally {
    saving.value = false
  }
}

function openUnitCreate() {
  editingUnitId.value = undefined
  Object.assign(unitForm, { code: '', name: '', decimalPlaces: 0, status: 'ACTIVE', version: '' })
  unitDialogVisible.value = true
}

async function openUnitEdit(value: unknown) {
  const row = value as UnitOption
  try {
    const detail = await getCatalogUnit(row.id)
    editingUnitId.value = detail.id
    Object.assign(unitForm, detail)
    unitDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '单位详情加载失败')
  }
}

async function saveUnit() {
  if (!unitForm.code.trim() || !unitForm.name.trim()) {
    ElMessage.warning('请填写单位编号和名称')
    return
  }
  saving.value = true
  try {
    if (editingUnitId.value) {
      await updateCatalogUnit(editingUnitId.value, {
        name: unitForm.name,
        decimalPlaces: unitForm.decimalPlaces,
        status: unitForm.status,
        version: unitForm.version,
      })
      ElMessage.success('单位资料已更新')
    } else {
      await createCatalogUnit({
        code: unitForm.code,
        name: unitForm.name,
        decimalPlaces: unitForm.decimalPlaces,
      })
      ElMessage.success('单位已创建')
    }
    unitDialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '单位保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div>
        <h1>分类与单位</h1>
        <p>维护产品、服务的一级分类及计量单位；停用后不可用于新建或编辑业务资料。</p>
      </div>
      <el-button
        v-if="auth.hasPermission('catalog:master:manage')"
        type="primary"
        @click="activeTab === 'UNIT' ? openUnitCreate() : openCategoryCreate()"
      >
        {{ activeTab === 'UNIT' ? '新建单位' : `新建${currentCategoryLabel}` }}
      </el-button>
    </div>

    <el-card class="data-card" shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="产品分类" name="PRODUCT" />
        <el-tab-pane label="服务分类" name="SERVICE" />
        <el-tab-pane label="计量单位" name="UNIT" />
      </el-tabs>

      <el-table v-if="activeTab !== 'UNIT'" v-loading="loading" :data="currentCategories" stripe row-key="id">
        <el-table-column prop="code" label="分类编号" width="210" />
        <el-table-column prop="name" label="分类名称" min-width="240" />
        <el-table-column prop="sortNo" label="排序" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="auth.hasPermission('catalog:master:manage')" label="操作" width="90" fixed="right">
          <template #default="scope"><el-button link type="primary" @click="openCategoryEdit(scope.row)">编辑</el-button></template>
        </el-table-column>
      </el-table>

      <el-table v-else v-loading="loading" :data="units" stripe row-key="id">
        <el-table-column prop="code" label="单位编号" width="210" />
        <el-table-column prop="name" label="单位名称" min-width="240" />
        <el-table-column prop="decimalPlaces" label="小数位数" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="auth.hasPermission('catalog:master:manage')" label="操作" width="90" fixed="right">
          <template #default="scope"><el-button link type="primary" @click="openUnitEdit(scope.row)">编辑</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="categoryDialogVisible" :title="editingCategoryId ? `编辑${currentCategoryLabel}` : `新建${currentCategoryLabel}`" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="分类编号" required><el-input v-model="categoryForm.code" maxlength="64" :disabled="Boolean(editingCategoryId)" /></el-form-item>
        <el-form-item label="分类名称" required><el-input v-model="categoryForm.name" maxlength="100" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sortNo" :min="0" :max="9999" /></el-form-item>
        <el-form-item v-if="editingCategoryId" label="状态"><el-radio-group v-model="categoryForm.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="categoryDialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCategory">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="unitDialogVisible" :title="editingUnitId ? '编辑单位' : '新建单位'" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="单位编号" required><el-input v-model="unitForm.code" maxlength="32" :disabled="Boolean(editingUnitId)" /></el-form-item>
        <el-form-item label="单位名称" required><el-input v-model="unitForm.name" maxlength="50" /></el-form-item>
        <el-form-item label="小数位数"><el-input-number v-model="unitForm.decimalPlaces" :min="0" :max="4" /></el-form-item>
        <el-form-item v-if="editingUnitId" label="状态"><el-radio-group v-model="unitForm.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon title="修改小数位数只影响后续录入和显示，不会改写历史业务数据。" />
      <template #footer><el-button @click="unitDialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveUnit">保存</el-button></template>
    </el-dialog>
  </section>
</template>
