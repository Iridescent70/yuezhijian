<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createSatisfactionRule,
  getSatisfactionRules,
  testSatisfactionRule,
  updateSatisfactionRule,
} from '@/api/settings'
import { useAuthStore } from '@/stores/auth'
import type {
  SatisfactionRule,
  SatisfactionRulePayload,
  SatisfactionRuleTestResult,
  SettingStatus,
} from '@/types/api'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const rows = ref<SatisfactionRule[]>([])
const status = ref<'' | SettingStatus>('')
const dialogVisible = ref(false)
const testVisible = ref(false)
const mappings = ref<Array<{ key: string; value: string }>>([])
const testText = ref('')
const testResult = ref<SatisfactionRuleTestResult>()
const form = reactive({
  id: undefined as number | undefined,
  ruleName: '', keywordsText: '', score: 5, priority: 100,
  status: 'ACTIVE' as SettingStatus, version: '',
})

async function load() {
  loading.value = true
  try {
    rows.value = await getSatisfactionRules(status.value || undefined)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '满意度规则加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined, ruleName: '', keywordsText: '', score: 5, priority: 100,
    status: 'ACTIVE', version: '',
  })
  mappings.value = []
  dialogVisible.value = true
}

function openEdit(value: unknown) {
  const row = value as SatisfactionRule
  Object.assign(form, {
    id: row.id, ruleName: row.ruleName, keywordsText: row.keywords.join('\n'),
    score: row.score, priority: row.priority, status: row.status, version: row.version,
  })
  mappings.value = Object.entries(row.componentMapping).map(([key, mappingValue]) => ({ key, value: mappingValue }))
  dialogVisible.value = true
}

function addMapping() { mappings.value.push({ key: '', value: '' }) }
function removeMapping(index: number) { mappings.value.splice(index, 1) }

async function save() {
  const keywords = [...new Set(form.keywordsText.split(/[\n,，]+/).map(item => item.trim()).filter(Boolean))]
  if (!form.ruleName.trim() || keywords.length === 0) return ElMessage.warning('请填写规则名称和至少一个关键词')
  const componentMapping: Record<string, string> = {}
  for (const item of mappings.value) {
    if (!item.key.trim() && !item.value.trim()) continue
    if (!item.key.trim() || !item.value.trim()) return ElMessage.warning('组件标识和映射值需要成对填写')
    if (componentMapping[item.key.trim()]) return ElMessage.warning(`组件标识 ${item.key.trim()} 重复`)
    componentMapping[item.key.trim()] = item.value.trim()
  }
  const payload: SatisfactionRulePayload = {
    ruleName: form.ruleName.trim(), keywords, score: form.score,
    componentMapping, priority: form.priority, status: form.status,
  }
  saving.value = true
  try {
    if (form.id) await updateSatisfactionRule(form.id, { ...payload, version: form.version })
    else await createSatisfactionRule(payload)
    dialogVisible.value = false
    ElMessage.success(form.id ? '满意度规则已更新' : '满意度规则已创建')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '满意度规则保存失败')
  } finally {
    saving.value = false
  }
}

function openTest() {
  testText.value = ''
  testResult.value = undefined
  testVisible.value = true
}

async function runTest() {
  if (!testText.value.trim()) return ElMessage.warning('请输入一段客户回复')
  testing.value = true
  try {
    testResult.value = await testSatisfactionRule(testText.value.trim())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '规则试算失败')
  } finally {
    testing.value = false
  }
}

function mappingText(value: unknown) {
  const mapping = value as Record<string, string>
  const entries = Object.entries(mapping)
  return entries.length ? entries.map(([key, item]) => `${key}=${item}`).join('；') : '—'
}

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>满意度规则</h1><p>按优先级匹配客户回复中的字面关键词，再映射评分和组件值；规则试算不会写入业务数据。</p></div>
      <div class="title-actions">
        <el-button @click="openTest">样例试算</el-button>
        <el-button v-if="auth.hasPermission('visit:satisfaction:manage')" type="primary" @click="openCreate">新建规则</el-button>
      </div>
    </div>
    <el-alert type="info" :closable="false" show-icon title="当前只提供规则配置和试算。短信通道接入前，不会自动读取或修改客户短信。优先级数字越小越先匹配。" />
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="状态"><el-select v-model="status" clearable placeholder="全部" style="width: 130px"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id">
        <el-table-column prop="priority" label="优先级" width="85" />
        <el-table-column prop="ruleName" label="规则名称" min-width="150" />
        <el-table-column label="识别关键词" min-width="260"><template #default="scope"><el-tag v-for="keyword in scope.row.keywords" :key="keyword" class="keyword-tag" effect="plain">{{ keyword }}</el-tag></template></el-table-column>
        <el-table-column label="评分" width="150"><template #default="scope"><el-rate :model-value="scope.row.score" disabled /></template></el-table-column>
        <el-table-column label="组件映射" min-width="220"><template #default="scope">{{ mappingText(scope.row.componentMapping) }}</template></el-table-column>
        <el-table-column label="状态" width="85"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column v-if="auth.hasPermission('visit:satisfaction:manage')" label="操作" width="80" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && rows.length === 0" description="尚未配置满意度识别规则" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑满意度规则' : '新建满意度规则'" width="680px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="规则名称" required><el-input v-model="form.ruleName" maxlength="100" /></el-form-item>
          <el-form-item label="优先级"><el-input-number v-model="form.priority" :min="0" :max="9999" /></el-form-item>
          <el-form-item label="识别关键词" required class="full-row"><el-input v-model="form.keywordsText" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="每行一个关键词；按字面包含匹配，不支持正则表达式" /></el-form-item>
          <el-form-item label="映射评分"><el-rate v-model="form.score" show-score /></el-form-item>
          <el-form-item label="状态"><el-switch v-model="form.status" active-value="ACTIVE" inactive-value="DISABLED" active-text="启用" inactive-text="停用" /></el-form-item>
          <el-form-item label="组件映射（甲方确认后填写）" class="full-row">
            <div class="mapping-list">
              <div v-for="(item, index) in mappings" :key="index" class="mapping-row">
                <el-input v-model="item.key" placeholder="组件标识" /><span>→</span><el-input v-model="item.value" placeholder="映射值" /><el-button link type="danger" @click="removeMapping(index)">删除</el-button>
              </div>
              <el-button @click="addMapping">增加组件映射</el-button>
            </div>
          </el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="testVisible" title="满意度规则样例试算" width="620px">
      <el-form label-position="top">
        <el-form-item label="客户回复"><el-input v-model="testText" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="输入一段测试文本" /></el-form-item>
      </el-form>
      <el-result v-if="testResult" :icon="testResult.matched ? 'success' : 'warning'" :title="testResult.matched ? `命中：${testResult.ruleName}` : '未命中规则'" :sub-title="testResult.message">
        <template v-if="testResult.matched" #extra>
          <p>关键词：{{ testResult.matchedKeyword }}　评分：{{ testResult.score }}分</p>
          <p>组件映射：{{ mappingText(testResult.componentMapping) }}</p>
        </template>
      </el-result>
      <template #footer><el-button @click="testVisible = false">关闭</el-button><el-button type="primary" :loading="testing" @click="runTest">开始试算</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.el-alert { margin-bottom: 16px; }
.keyword-tag { margin: 2px 5px 2px 0; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.full-row { grid-column: 1 / -1; }
.mapping-list { width: 100%; }
.mapping-row { display: grid; grid-template-columns: 1fr auto 1fr auto; gap: 8px; align-items: center; margin-bottom: 8px; }
@media (max-width: 720px) { .form-grid { grid-template-columns: 1fr; } .full-row { grid-column: auto; } }
</style>
