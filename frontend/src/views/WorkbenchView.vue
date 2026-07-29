<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { activeBannerImageUrl, getActiveBanners } from '@/api/banner'
import { getWorkbenchOverview } from '@/api/platform'
import type { ActiveBanner, WorkbenchOverview } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const router = useRouter()
const loading = ref(true)
const overview = ref<WorkbenchOverview | null>(null)
const banners = ref<ActiveBanner[]>([])

async function load() {
  loading.value = true
  try {
    const [overviewResult, bannerResult] = await Promise.allSettled([
      getWorkbenchOverview(), getActiveBanners('PC_HOME'),
    ])
    if (overviewResult.status === 'rejected') throw overviewResult.reason
    overview.value = overviewResult.value
    banners.value = bannerResult.status === 'fulfilled' ? bannerResult.value : []
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '工作台加载失败')
  } finally {
    loading.value = false
  }
}

function openBanner(banner: ActiveBanner) {
  if (!banner.linkValue || banner.linkType === 'NONE') return
  if (banner.linkType === 'INTERNAL') {
    void router.push(banner.linkValue)
    return
  }
  window.open(banner.linkValue, '_blank', 'noopener,noreferrer')
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

    <div v-if="banners.length" class="banner-strip" aria-label="首页推荐">
      <button
        v-for="banner in banners"
        :key="banner.id"
        type="button"
        class="banner-card"
        :class="{ clickable: banner.linkType !== 'NONE' }"
        :disabled="banner.linkType === 'NONE'"
        @click="openBanner(banner)"
      >
        <img :src="activeBannerImageUrl(banner)" :alt="banner.title">
        <span>{{ banner.title }}</span>
      </button>
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

<style scoped>
.banner-strip { display: flex; gap: 16px; overflow-x: auto; margin-bottom: 20px; scroll-snap-type: x mandatory; }
.banner-card { position: relative; min-width: min(560px, 82vw); aspect-ratio: 3 / 1; padding: 0; overflow: hidden; border: 0; border-radius: 12px; background: #eef2f7; scroll-snap-align: start; }
.banner-card img { width: 100%; height: 100%; object-fit: cover; display: block; }
.banner-card span { position: absolute; left: 14px; bottom: 12px; max-width: calc(100% - 28px); padding: 4px 9px; overflow: hidden; color: white; background: rgb(0 0 0 / 52%); border-radius: 5px; text-overflow: ellipsis; white-space: nowrap; }
.banner-card.clickable { cursor: pointer; }
.banner-card:disabled { color: inherit; }
</style>
