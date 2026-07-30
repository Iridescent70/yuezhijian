<template>
  <div class="home-page">
    <el-card shadow="never">
      <div class="flex items-center gap-16px">
        <el-avatar :src="avatar" :size="64">
          <img src="@/assets/imgs/avatar.gif" alt="" />
        </el-avatar>
        <div>
          <div class="text-20px font-600">你好，{{ username }}</div>
          <div class="mt-8px text-14px text-[var(--el-text-color-secondary)]">
            欢迎进入悦指间门店管理系统重构环境
          </div>
        </div>
      </div>
    </el-card>

    <el-alert
      class="mt-12px"
      title="当前处于芋道全栈底座迁移阶段"
      description="系统、权限和基础设施使用芋道原生能力；main 已完成业务正按模块迁移。未迁移功能不会用演示数据冒充完成。"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-row class="mt-12px" :gutter="12">
      <el-col v-for="item in summary" :key="item.label" :xs="24" :sm="12" :lg="6">
        <el-card class="mb-12px" shadow="never">
          <div class="flex items-center justify-between">
            <div>
              <div class="text-13px text-[var(--el-text-color-secondary)]">{{ item.label }}</div>
              <div class="mt-10px text-28px font-600">{{ item.value }}</div>
            </div>
            <Icon :icon="item.icon" :size="34" :color="item.color" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="mb-12px">
          <template #header>
            <span class="font-600">迁移顺序</span>
          </template>
          <el-steps direction="vertical" :active="1" finish-status="success">
            <el-step title="P0 全栈底座" description="芋道前后端、SQL Server、Redis、构建与登录链路" />
            <el-step title="P1 平台样板" description="门店、员工、岗位、会员主档和数据范围" />
            <el-step title="P2 main 业务迁移" description="会员资产、预约、交易、库存、提成、回访与通知" />
            <el-step title="P3 未开发功能" description="到家、薪酬、短信、数据中心与 AI" />
            <el-step title="P4 数据迁移上线" description="旧库试迁移、对账、灰度、回滚与验收" />
          </el-steps>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="mb-12px">
          <template #header>
            <span class="font-600">当前运行模块</span>
          </template>
          <div class="flex flex-wrap gap-8px">
            <el-tag v-for="module in enabledModules" :key="module" type="success">
              {{ module }}
            </el-tag>
          </div>
          <el-divider />
          <el-descriptions :column="1" border>
            <el-descriptions-item label="业务基线">main@6cae2c8</el-descriptions-item>
            <el-descriptions-item label="后端底座">ruoyi-vue-pro@ec3f7cb</el-descriptions-item>
            <el-descriptions-item label="前端底座">yudao-ui-admin-vue3@9445977</el-descriptions-item>
            <el-descriptions-item label="下一样板">门店—员工—岗位—会员主档</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts" setup>
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'Index' })

const userStore = useUserStore()
const avatar = computed(() => userStore.getUser.avatar)
const username = computed(() => userStore.getUser.nickname || '管理员')

const summary = [
  { label: '芋道上游仓库', value: 2, icon: 'ep:connection', color: '#409eff' },
  { label: 'main 迭代资产', value: 52, icon: 'ep:document', color: '#67c23a' },
  { label: '历史迁移脚本', value: 44, icon: 'ep:coin', color: '#e6a23c' },
  { label: '已迁移业务模块', value: 0, icon: 'ep:finished', color: '#909399' }
]

const enabledModules = ['System 系统管理', 'Infra 基础设施', 'Yudao Server']
</script>

<style scoped>
.home-page {
  min-height: 100%;
}
</style>
