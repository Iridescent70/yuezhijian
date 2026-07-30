<script lang="ts" setup>
import { getWorkbenchOverview, type WorkbenchOverview } from '@/api/yuezhijian/workbench'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'Index' })

const router = useRouter()
const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const overview = ref<WorkbenchOverview>()

const metrics = computed(() => [
  { label: '今日预约', value: overview.value?.appointmentCount ?? 0, unit: '笔' },
  { label: '今日客量', value: overview.value?.customerTraffic ?? 0, unit: '人' },
  {
    label: '今日营业额',
    value: new Intl.NumberFormat('zh-CN', {
      style: 'currency',
      currency: 'CNY',
      minimumFractionDigits: 2
    }).format(overview.value?.revenue ?? 0),
    unit: '已结算'
  },
  { label: '待处理事项', value: overview.value?.pendingTaskCount ?? 0, unit: '项' }
])

const load = async () => {
  loading.value = true
  try {
    overview.value = await getWorkbenchOverview()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '工作台加载失败')
  } finally {
    loading.value = false
  }
}

const openShortcut = (route: string) => router.push(route)

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <ContentWrap>
      <div class="flex flex-wrap items-center justify-between gap-12px">
        <div>
          <h1 class="m-0 text-24px font-700">欢迎回来，{{ userStore.getUser.nickname }}</h1>
          <p class="mb-0 mt-8px text-14px text-gray-500">
            {{ userStore.getUser.currentStoreName || '当前门店' }} ·
            {{ overview?.businessDate || '经营数据加载中' }}
          </p>
        </div>
        <ElButton :loading="loading" @click="load">刷新数据</ElButton>
      </div>
    </ContentWrap>

    <ElRow :gutter="16">
      <ElCol v-for="metric in metrics" :key="metric.label" :lg="6" :md="12" :sm="12" :xs="24">
        <ContentWrap>
          <div class="text-14px text-gray-500">{{ metric.label }}</div>
          <div class="mt-14px flex items-end gap-8px">
            <strong class="text-28px">{{ metric.value }}</strong>
            <span class="pb-4px text-13px text-gray-400">{{ metric.unit }}</span>
          </div>
        </ContentWrap>
      </ElCol>
    </ElRow>

    <ElRow :gutter="16">
      <ElCol :lg="16" :xs="24">
        <ContentWrap title="常用功能">
          <div v-if="overview?.shortcuts.length" class="shortcut-grid">
            <ElButton
              v-for="shortcut in overview.shortcuts"
              :key="shortcut.code"
              class="!m-0 !h-72px"
              plain
              @click="openShortcut(shortcut.route)"
            >
              {{ shortcut.name }}
            </ElButton>
          </div>
          <ElEmpty v-else description="暂无可用快捷入口" :image-size="72" />
        </ContentWrap>
      </ElCol>
      <ElCol :lg="8" :xs="24">
        <ContentWrap title="重构进度">
          <ElAlert :closable="false" show-icon title="芋道管理端底座已接入" type="success" />
          <p class="mb-0 mt-14px text-14px leading-7 text-gray-500">
            会话登录、权限和菜单正在与现有业务后端对接。未迁移页面会保留菜单入口并显示迁移提示，业务规则仍以现有后端和项目文档为准。
          </p>
        </ContentWrap>
      </ElCol>
    </ElRow>
  </div>
</template>

<style scoped>
.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}
</style>
