<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { activeBannerImageUrl, getActiveBanners } from '@/api/banner'
import { getWorkbenchOverview } from '@/api/platform'
import { getNotifications } from '@/api/notification'
import { useAuthStore } from '@/stores/auth'
import type { ActiveBanner, NotificationItem, WorkbenchOverview } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(true)
const overview = ref<WorkbenchOverview | null>(null)
const banners = ref<ActiveBanner[]>([])
const announcements = ref<NotificationItem[]>([])

async function load() {
  loading.value = true
  try {
    const [overviewResult, bannerResult, announcementResult] = await Promise.allSettled([
      getWorkbenchOverview(), getActiveBanners('PC_HOME'),
      auth.hasPermission('notification:view')
        ? getNotifications({ messageType: 'ANNOUNCEMENT', page: 1, size: 5 })
        : Promise.resolve({ items: [], page: 1, size: 5, total: 0 }),
    ])
    if (overviewResult.status === 'rejected') throw overviewResult.reason
    overview.value = overviewResult.value
    banners.value = bannerResult.status === 'fulfilled' ? bannerResult.value : []
    announcements.value = announcementResult.status === 'fulfilled' ? announcementResult.value.items : []
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

function dateTime(value: string) { return value.replace('T', ' ').slice(5, 16) }

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

    <el-card v-if="auth.hasPermission('notification:view')" class="notice-card" shadow="never">
      <template #header><div class="notice-header"><strong>通知公告</strong><el-button link type="primary" @click="router.push('/app/notifications')">查看全部</el-button></div></template>
      <button v-for="item in announcements" :key="item.id" type="button" class="notice-row" @click="router.push(`/app/notifications?notificationId=${item.id}`)">
        <span class="notice-title"><i v-if="!item.read" />{{ item.title }}</span>
        <time>{{ dateTime(item.publishedAt) }}</time>
      </button>
      <el-empty v-if="announcements.length === 0" description="暂无有效公告" :image-size="54" />
    </el-card>

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
.notice-card { margin-bottom: 20px; }
.notice-header { display: flex; align-items: center; justify-content: space-between; }
.notice-row { display: flex; width: 100%; align-items: center; justify-content: space-between; padding: 11px 0; border: 0; border-bottom: 1px solid var(--border); color: inherit; background: transparent; text-align: left; }
.notice-row:last-child { border-bottom: 0; }
.notice-title { display: flex; align-items: center; min-width: 0; overflow: hidden; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.notice-title i { flex: 0 0 auto; width: 7px; height: 7px; margin-right: 8px; border-radius: 50%; background: #e65f78; }
.notice-row time { flex: 0 0 auto; margin-left: 20px; color: var(--muted); font-size: 13px; }
</style>
