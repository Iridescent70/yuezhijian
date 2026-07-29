<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMember } from '@/api/member'
import type { MemberDetail } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'
import MemberAssetsPanel from './MemberAssetsPanel.vue'
import type { BalanceAccount, PointAccount } from '@/types/api'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const member = ref<MemberDetail>()
const activeTab = ref('overview')
const liveBalance = ref<BalanceAccount>()
const livePoints = ref<PointAccount>()
const liveCardCount = ref<number>()

function updateAssets(payload: { balance: BalanceAccount; points: PointAccount; cardCount: number }) {
  liveBalance.value = payload.balance
  livePoints.value = payload.points
  liveCardCount.value = payload.cardCount
}

const genderLabels: Record<string, string> = {
  UNKNOWN: '未填写',
  FEMALE: '女',
  MALE: '男',
  OTHER: '其他',
}
const statusLabels: Record<string, string> = { ACTIVE: '正常', FROZEN: '已冻结', INACTIVE: '已停用' }

async function loadMember() {
  loading.value = true
  try {
    member.value = await getMember(Number(route.params.memberId))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会员详情加载失败')
    await router.replace('/app/members')
  } finally {
    loading.value = false
  }
}

onMounted(loadMember)
</script>

<template>
  <section v-loading="loading" class="page-content member-detail-page">
    <div class="section-title-row">
      <div class="member-title-block">
        <el-button link @click="router.push('/app/members')">← 返回会员列表</el-button>
        <div v-if="member" class="member-title-line">
          <h1>{{ member.fullName }}</h1>
          <el-tag :type="member.status === 'ACTIVE' ? 'success' : 'warning'">
            {{ statusLabels[member.status] ?? member.status }}
          </el-tag>
          <el-tag v-if="member.special" type="danger">特殊会员</el-tag>
        </div>
        <p v-if="member">{{ member.memberNo }} · {{ member.maskedMobile }} · {{ member.ownerStoreName }}</p>
      </div>
      <el-button disabled>编辑档案</el-button>
    </div>

    <template v-if="member">
      <div class="member-asset-grid">
        <article><span>可用储值</span><strong>{{ formatMoney(liveBalance?.availableBalance ?? member.assets.availableBalance) }}</strong></article>
        <article><span>可用积分</span><strong>{{ livePoints?.availablePoints ?? member.assets.availablePoints }}</strong></article>
        <article><span>有效次卡</span><strong>{{ liveCardCount ?? member.assets.cardCount }}</strong></article>
        <article><span>累计储值</span><strong>{{ formatMoney(liveBalance?.totalRecharged ?? member.assets.totalRecharged) }}</strong></article>
      </div>

      <el-tabs v-model="activeTab" class="member-tabs">
        <el-tab-pane label="概览" name="overview">
          <div class="member-detail-grid">
            <el-card shadow="never">
              <template #header><strong>基础档案</strong></template>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="会员卡号">{{ member.membershipCardNo }}</el-descriptions-item>
                <el-descriptions-item label="会员等级">{{ member.levelName }}</el-descriptions-item>
                <el-descriptions-item label="姓名/昵称">
                  {{ member.fullName }}{{ member.nickname ? `（${member.nickname}）` : '' }}
                </el-descriptions-item>
                <el-descriptions-item label="性别">{{ genderLabels[member.gender] ?? member.gender }}</el-descriptions-item>
                <el-descriptions-item label="生日">{{ member.birthday ?? '—' }}</el-descriptions-item>
                <el-descriptions-item label="邮箱">{{ member.email ?? '—' }}</el-descriptions-item>
                <el-descriptions-item label="入会门店">{{ member.joinStoreName }}</el-descriptions-item>
                <el-descriptions-item label="归属门店">{{ member.ownerStoreName }}</el-descriptions-item>
                <el-descriptions-item label="会员来源">{{ member.sourceType }}</el-descriptions-item>
                <el-descriptions-item label="建档时间">{{ member.createdAt.replace('T', ' ') }}</el-descriptions-item>
              </el-descriptions>
            </el-card>

            <el-card shadow="never">
              <template #header><strong>会员标签</strong></template>
              <div v-if="member.tags.length" class="member-tags">
                <el-tag
                  v-for="tag in member.tags"
                  :key="tag.id"
                  :type="tag.negative ? 'danger' : 'info'"
                  effect="plain"
                >
                  {{ tag.name }}
                </el-tag>
              </div>
              <el-empty v-else description="暂无标签" :image-size="64" />
            </el-card>
          </div>
        </el-tab-pane>
        <el-tab-pane label="会员资产" name="assets" lazy>
          <MemberAssetsPanel
            v-if="activeTab === 'assets'"
            :member-id="member.id"
            :store-id="member.ownerStoreId"
            @changed="updateAssets"
          />
        </el-tab-pane>
        <el-tab-pane label="消费记录" name="transactions" lazy><el-empty description="暂无消费记录" /></el-tab-pane>
        <el-tab-pane label="服务档案" name="profile" lazy><el-empty description="服务档案将在后续迭代接入" /></el-tab-pane>
        <el-tab-pane label="预约与回访" name="visits" lazy><el-empty description="预约模块尚未产生记录" /></el-tab-pane>
        <el-tab-pane label="AI建议" name="ai" lazy><el-empty description="AI分析接入后在此展示依据和建议" /></el-tab-pane>
      </el-tabs>
    </template>
  </section>
</template>
