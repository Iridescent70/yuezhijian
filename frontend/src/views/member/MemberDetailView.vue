<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  changeMemberStatus,
  getMember,
  getMemberTagOptions,
  updateMember,
  updateMemberTags,
} from '@/api/member'
import { useAuthStore } from '@/stores/auth'
import type { MemberDetail, MemberTagOption } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'
import MemberAssetsPanel from './MemberAssetsPanel.vue'
import type { BalanceAccount, PointAccount } from '@/types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(true)
const member = ref<MemberDetail>()
const activeTab = ref('overview')
const liveBalance = ref<BalanceAccount>()
const livePoints = ref<PointAccount>()
const liveCardCount = ref<number>()
const editVisible = ref(false)
const editSaving = ref(false)
const statusVisible = ref(false)
const statusSaving = ref(false)
const targetStatus = ref<'ACTIVE' | 'FROZEN' | 'INACTIVE'>('FROZEN')
const statusReason = ref('')
const tagsVisible = ref(false)
const tagsLoading = ref(false)
const tagsSaving = ref(false)
const tagOptions = ref<MemberTagOption[]>([])
const selectedTagIds = ref<number[]>([])
const editForm = reactive({
  fullName: '',
  nickname: '',
  mobile: '',
  gender: 'UNKNOWN',
  birthday: '',
  email: '',
  special: false,
})

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

function openEdit() {
  if (!member.value) return
  editForm.fullName = member.value.fullName
  editForm.nickname = member.value.nickname ?? ''
  editForm.mobile = ''
  editForm.gender = member.value.gender
  editForm.birthday = member.value.birthday ?? ''
  editForm.email = member.value.email ?? ''
  editForm.special = member.value.special
  editVisible.value = true
}

async function saveEdit() {
  if (!member.value || !editForm.fullName.trim()) {
    ElMessage.warning('请输入会员姓名')
    return
  }
  editSaving.value = true
  try {
    member.value = await updateMember(member.value.id, {
      fullName: editForm.fullName.trim(),
      nickname: editForm.nickname.trim() || undefined,
      mobile: editForm.mobile.trim() || undefined,
      gender: editForm.gender,
      birthday: editForm.birthday || undefined,
      email: editForm.email.trim() || undefined,
      advisorEmployeeId: member.value.advisorEmployeeId,
      special: editForm.special,
      version: member.value.version,
    })
    editVisible.value = false
    ElMessage.success('会员档案已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会员档案更新失败')
  } finally {
    editSaving.value = false
  }
}

function openStatus(status: 'ACTIVE' | 'FROZEN' | 'INACTIVE') {
  targetStatus.value = status
  statusReason.value = ''
  statusVisible.value = true
}

async function saveStatus() {
  if (!member.value || !statusReason.value.trim()) {
    ElMessage.warning('请填写状态变更原因')
    return
  }
  statusSaving.value = true
  try {
    member.value = await changeMemberStatus(member.value.id, {
      status: targetStatus.value,
      reason: statusReason.value.trim(),
      version: member.value.version,
    })
    statusVisible.value = false
    ElMessage.success(targetStatus.value === 'FROZEN' ? '会员已冻结' : '会员状态已恢复')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会员状态更新失败')
  } finally {
    statusSaving.value = false
  }
}

async function openTags() {
  if (!member.value) return
  tagsVisible.value = true
  tagsLoading.value = true
  selectedTagIds.value = member.value.tags.map((tag) => tag.id)
  try {
    tagOptions.value = await getMemberTagOptions()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会员标签加载失败')
    tagsVisible.value = false
  } finally {
    tagsLoading.value = false
  }
}

async function saveTags() {
  if (!member.value) return
  const currentIds = member.value.tags.map((tag) => tag.id)
  const addIds = selectedTagIds.value.filter((id) => !currentIds.includes(id))
  const removeIds = currentIds.filter((id) => !selectedTagIds.value.includes(id))
  tagsSaving.value = true
  try {
    member.value = await updateMemberTags(member.value.id, {
      addIds,
      removeIds,
      version: member.value.version,
    })
    tagsVisible.value = false
    ElMessage.success('会员标签已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会员标签更新失败')
  } finally {
    tagsSaving.value = false
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
      <div
        v-if="member && (auth.hasPermission('member:member:manage') || auth.hasPermission('member:ownership:manage'))"
        class="member-actions"
      >
        <el-button v-if="auth.hasPermission('member:member:manage')" @click="openEdit">编辑档案</el-button>
        <el-button
          v-if="auth.hasPermission('member:ownership:manage')"
          @click="router.push(`/app/members/ownership?memberId=${member.id}`)"
        >调整归属</el-button>
        <el-button
          v-if="auth.hasPermission('member:member:manage') && member.status === 'ACTIVE'"
          type="warning"
          plain
          @click="openStatus('FROZEN')"
        >
          冻结会员
        </el-button>
        <el-button
          v-else-if="auth.hasPermission('member:member:manage')"
          type="success"
          plain
          @click="openStatus('ACTIVE')"
        >恢复正常</el-button>
      </div>
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
                <el-descriptions-item v-if="member.status === 'FROZEN'" label="冻结原因" :span="2">
                  {{ member.freezeReason }} · {{ member.frozenAt?.replace('T', ' ') }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>

            <el-card shadow="never">
              <template #header>
                <div class="card-header-row">
                  <strong>会员标签</strong>
                  <el-button
                    v-if="auth.hasPermission('member:tag:manage')"
                    link
                    type="primary"
                    @click="openTags"
                  >维护标签</el-button>
                </div>
              </template>
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

    <el-dialog v-model="editVisible" title="编辑会员档案" width="620px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="会员姓名" required>
          <el-input v-model="editForm.fullName" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="昵称"><el-input v-model="editForm.nickname" maxlength="100" /></el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="editForm.mobile" maxlength="11" placeholder="留空表示不修改" />
        </el-form-item>
        <el-form-item label="性别" required>
          <el-radio-group v-model="editForm.gender">
            <el-radio-button value="UNKNOWN">未填写</el-radio-button>
            <el-radio-button value="FEMALE">女</el-radio-button>
            <el-radio-button value="MALE">男</el-radio-button>
            <el-radio-button value="OTHER">其他</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker v-model="editForm.birthday" type="date" value-format="YYYY-MM-DD" clearable />
        </el-form-item>
        <el-form-item label="邮箱"><el-input v-model="editForm.email" maxlength="255" /></el-form-item>
        <el-form-item label="特殊会员"><el-switch v-model="editForm.special" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statusVisible" :title="targetStatus === 'FROZEN' ? '冻结会员' : '恢复会员'" width="500px">
      <el-alert
        :title="targetStatus === 'FROZEN' ? '冻结后不能开单、充值或变更会员资产。' : '恢复后会员可重新参与正常业务。'"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form label-width="90px" class="dialog-form">
        <el-form-item label="变更原因" required>
          <el-input v-model="statusReason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button type="primary" :loading="statusSaving" @click="saveStatus">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tagsVisible" title="维护会员标签" width="560px">
      <div v-loading="tagsLoading">
        <el-select v-model="selectedTagIds" multiple filterable class="tag-select" placeholder="请选择标签">
          <el-option v-for="tag in tagOptions" :key="tag.id" :label="tag.name" :value="tag.id">
            <span>{{ tag.name }}</span>
            <el-tag v-if="tag.negative" type="danger" size="small" class="tag-option-mark">负向</el-tag>
          </el-option>
        </el-select>
        <p class="dialog-hint">本页只维护人工选中结果；规则标签和AI建议仍保留来源，不自动生成未经确认的标签。</p>
      </div>
      <template #footer>
        <el-button @click="tagsVisible = false">取消</el-button>
        <el-button type="primary" :loading="tagsSaving" :disabled="tagsLoading" @click="saveTags">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.member-actions,
.card-header-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-header-row {
  justify-content: space-between;
}

.dialog-form {
  margin-top: 18px;
}

.tag-select {
  width: 100%;
}

.tag-option-mark {
  margin-left: 8px;
}

.dialog-hint {
  margin: 14px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
</style>
