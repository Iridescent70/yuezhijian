<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getWorkbenchOverview } from '@/api/platform'
import type { WorkbenchOverview } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const router = useRouter()
const loading = ref(true)
const overview = ref<WorkbenchOverview | null>(null)

async function load() {
  loading.value = true
  try {
    overview.value = await getWorkbenchOverview()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '工作台加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="page-content">
    <div class="section-title-row">
      <div>
        <h1>今日经营</h1>
        <p>{{ overview?.businessDate ?? '—' }} · 数据以当前门店权限为准</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="metric-grid">
      <article class="metric-card">
        <span>今日预约</span><strong>{{ overview?.appointmentCount ?? 0 }}</strong><small>笔</small>
      </article>
      <article class="metric-card">
        <span>今日客量</span><strong>{{ overview?.customerTraffic ?? 0 }}</strong><small>人</small>
      </article>
      <article class="metric-card accent">
        <span>今日营业额</span><strong>{{ formatMoney(overview?.revenue ?? 0) }}</strong><small>已结算</small>
      </article>
      <article class="metric-card">
        <span>待处理</span><strong>{{ overview?.pendingTaskCount ?? 0 }}</strong><small>项</small>
      </article>
    </div>

    <el-card class="shortcut-card" shadow="never">
      <template #header><strong>常用功能</strong></template>
      <div class="shortcut-grid">
        <button
          v-for="shortcut in overview?.shortcuts"
          :key="shortcut.code"
          type="button"
          class="shortcut-item"
          @click="router.push(shortcut.route)"
        >
          <span>{{ shortcut.name.slice(0, 1) }}</span>
          {{ shortcut.name }}
        </button>
      </div>
    </el-card>
  </section>
</template>
