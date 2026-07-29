<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createCardType, getCardTypes } from '@/api/card'
import { getServices } from '@/api/masterData'
import { useAuthStore } from '@/stores/auth'
import type { CardTypeDetail, ServiceItemSummary } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const items = ref<CardTypeDetail[]>([])
const services = ref<ServiceItemSummary[]>([])
const filters = reactive<{ keyword: string; storeId?: number; status?: string }>({ keyword: '', storeId: undefined, status: 'ACTIVE' })
const form = reactive({
  code: '', name: '', salePrice: 0, listPrice: 0, validDays: 365,
  purchaseThreshold: 0, autoRemindDays: 30, instructions: '', storeIds: [] as number[],
  serviceRules: [] as Array<{ serviceId?: number; includedTimes: number; deductTimes: number; priority: number }>,
})
const totalTimes = computed(() => form.serviceRules.reduce((sum, rule) => sum + Number(rule.includedTimes || 0), 0))

async function load() {
  loading.value = true
  try {
    items.value = await getCardTypes({
      keyword: filters.keyword || undefined, storeId: filters.storeId, status: filters.status,
    })
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '次卡类型加载失败') }
  finally { loading.value = false }
}

async function loadServices() {
  const storeId = form.storeIds[0]
  services.value = storeId ? await getServices({ storeId }) : []
}

async function openCreate() {
  const defaultStore = auth.user?.stores.find((store) => store.level !== 'HEADQUARTERS')?.id
    ?? auth.user?.currentStoreId ?? auth.user?.stores[0]?.id
  Object.assign(form, {
    code: '', name: '', salePrice: 0, listPrice: 0, validDays: 365,
    purchaseThreshold: 0, autoRemindDays: 30, instructions: '',
    storeIds: defaultStore ? [defaultStore] : [],
    serviceRules: [{ serviceId: undefined, includedTimes: 10, deductTimes: 1, priority: 10 }],
  })
  try { await loadServices(); visible.value = true }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '服务项目加载失败') }
}

function addRule() {
  form.serviceRules.push({ serviceId: undefined, includedTimes: 1, deductTimes: 1, priority: (form.serviceRules.length + 1) * 10 })
}

function removeRule(index: number) { if (form.serviceRules.length > 1) form.serviceRules.splice(index, 1) }

async function storesChanged() {
  form.serviceRules.forEach((rule) => { rule.serviceId = undefined })
  try { await loadServices() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '服务项目加载失败') }
}

async function submit() {
  if (!form.code.trim() || !form.name.trim() || !form.storeIds.length
      || form.serviceRules.some((rule) => !rule.serviceId || rule.includedTimes <= 0 || rule.deductTimes <= 0)) {
    ElMessage.warning('请完整填写次卡资料、适用门店和项目次数')
    return
  }
  saving.value = true
  try {
    await createCardType({
      code: form.code, name: form.name, salePrice: form.salePrice, listPrice: form.listPrice,
      totalTimes: totalTimes.value, validDays: form.validDays, purchaseThreshold: form.purchaseThreshold,
      autoRemindDays: form.autoRemindDays, instructions: form.instructions || undefined,
      storeIds: form.storeIds,
      serviceRules: form.serviceRules.map((rule) => ({
        serviceId: rule.serviceId!, includedTimes: rule.includedTimes,
        deductTimes: rule.deductTimes, priority: rule.priority,
      })),
    })
    visible.value = false
    ElMessage.success('次卡类型已创建')
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '次卡类型创建失败') }
  finally { saving.value = false }
}

function storeNames(ids: number[]) {
  return ids.map((id) => auth.user?.stores.find((store) => store.id === id)?.name ?? `门店${id}`).join('、')
}

onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>次卡类型</h1><p>维护售价、有效期、适用门店和项目扣次规则。</p></div>
      <el-button v-if="auth.hasPermission('catalog:card:manage')" type="primary" @click="openCreate">新建次卡类型</el-button>
    </div>
    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="次卡查询"><el-input v-model="filters.keyword" clearable placeholder="编码或名称" /></el-form-item>
        <el-form-item label="适用门店"><el-select v-model="filters.storeId" clearable placeholder="全部门店" class="master-filter-select"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable class="master-filter-select"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="items" stripe row-key="id">
        <el-table-column prop="code" label="编码" width="160" />
        <el-table-column prop="name" label="次卡名称" min-width="210" />
        <el-table-column label="销售价" width="120" align="right"><template #default="scope">{{ formatMoney(scope.row.salePrice) }}</template></el-table-column>
        <el-table-column prop="totalTimes" label="总次数" width="90" align="right" />
        <el-table-column prop="validDays" label="有效天数" width="100" align="right" />
        <el-table-column label="适用门店" min-width="180"><template #default="scope">{{ storeNames(scope.row.storeIds) }}</template></el-table-column>
        <el-table-column label="项目规则" min-width="220"><template #default="scope">{{ scope.row.serviceRules.map((rule: { serviceName: string; includedTimes: number }) => `${rule.serviceName}×${rule.includedTimes}`).join('；') }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" title="新建次卡类型" width="820px" destroy-on-close>
      <el-form label-width="100px" class="dialog-form-grid">
        <el-form-item label="次卡编码" required><el-input v-model="form.code" maxlength="64" /></el-form-item>
        <el-form-item label="次卡名称" required><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item label="销售价" required><el-input-number v-model="form.salePrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="原价" required><el-input-number v-model="form.listPrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="总次数"><strong>{{ totalTimes }}</strong></el-form-item>
        <el-form-item label="有效天数"><el-input-number v-model="form.validDays" :min="1" :max="3650" /></el-form-item>
        <el-form-item label="购卡门槛"><el-input-number v-model="form.purchaseThreshold" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="到期提醒"><el-input-number v-model="form.autoRemindDays" :min="0" :max="365" /><span class="field-suffix">天</span></el-form-item>
        <el-form-item label="适用门店" required class="dialog-form-wide"><el-select v-model="form.storeIds" multiple class="dialog-full-control" @change="storesChanged"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
        <el-form-item label="项目次数" required class="dialog-form-wide">
          <div class="card-rule-editor">
            <div v-for="(rule, index) in form.serviceRules" :key="index" class="card-rule-row">
              <el-select v-model="rule.serviceId" placeholder="选择项目"><el-option v-for="service in services" :key="service.id" :label="service.name" :value="service.id" /></el-select>
              <el-input-number v-model="rule.includedTimes" :min="0.0001" :precision="2" /><span>包含次数</span>
              <el-input-number v-model="rule.deductTimes" :min="0.0001" :precision="2" /><span>每次扣减</span>
              <el-button link type="danger" :disabled="form.serviceRules.length === 1" @click="removeRule(index)">删除</el-button>
            </div>
            <el-button link type="primary" @click="addRule">+ 添加项目</el-button>
          </div>
        </el-form-item>
        <el-form-item label="使用须知" class="dialog-form-wide"><el-input v-model="form.instructions" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </section>
</template>
