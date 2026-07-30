<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMember, searchMembers } from '@/api/member'
import {
  approveOwnershipAdjustment,
  createOwnershipAdjustment,
  getOwnershipAdjustments,
  rejectOwnershipAdjustment,
} from '@/api/ownership'
import { useAuthStore } from '@/stores/auth'
import type { MemberDetail, MemberSummary, OwnershipAdjustment } from '@/types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const rows = ref<OwnershipAdjustment[]>([])
const filters = reactive({ approvalStatus: '', executionStatus: '' })
const detailVisible = ref(false)
const selected = ref<OwnershipAdjustment>()
const createVisible = ref(false)
const createSaving = ref(false)
const memberSearching = ref(false)
const memberOptions = ref<MemberSummary[]>([])
const selectedMember = ref<MemberDetail>()
const createForm = reactive({
  memberId: undefined as number | undefined,
  newStoreId: undefined as number | undefined,
  effectiveDate: today(),
  reason: '',
  shareRuleText: '{}',
})
const reviewVisible = ref(false)
const reviewSaving = ref(false)
const reviewApproved = ref(true)
const reviewComment = ref('')

const approvalLabels: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}
const executionLabels: Record<string, string> = {
  WAITING: '待生效',
  PROCESSING: '执行中',
  APPLIED: '已生效',
  FAILED: '执行失败',
  CANCELLED: '已取消',
}

function today() {
  const date = new Date()
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function approvalType(status: string) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

function executionType(status: string) {
  if (status === 'APPLIED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  return 'warning'
}

async function loadRows() {
  loading.value = true
  try {
    rows.value = await getOwnershipAdjustments({
      approvalStatus: filters.approvalStatus || undefined,
      executionStatus: filters.executionStatus || undefined,
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '归属调整加载失败')
  } finally {
    loading.value = false
  }
}

async function searchMember(keyword: string) {
  if (!keyword.trim()) return
  memberSearching.value = true
  try {
    const result = await searchMembers({ keyword: keyword.trim(), page: 1, size: 20 })
    memberOptions.value = result.items
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会员查询失败')
  } finally {
    memberSearching.value = false
  }
}

async function chooseMember(memberId?: number): Promise<MemberDetail | undefined> {
  selectedMember.value = undefined
  createForm.newStoreId = undefined
  if (!memberId) return undefined
  try {
    selectedMember.value = await getMember(memberId)
    return selectedMember.value
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会员详情加载失败')
    return undefined
  }
}

async function openCreate(memberId?: number) {
  createForm.memberId = memberId
  createForm.newStoreId = undefined
  createForm.effectiveDate = today()
  createForm.reason = ''
  createForm.shareRuleText = '{}'
  memberOptions.value = []
  selectedMember.value = undefined
  createVisible.value = true
  if (memberId) {
    const detail = await chooseMember(memberId)
    if (detail) {
      memberOptions.value = [{
        id: detail.id,
        memberNo: detail.memberNo,
        fullName: detail.fullName,
        maskedMobile: detail.maskedMobile,
        gender: detail.gender,
        levelName: detail.levelName,
        ownerStoreId: detail.ownerStoreId,
        ownerStoreName: detail.ownerStoreName,
        availableBalance: detail.assets.availableBalance,
        availablePoints: detail.assets.availablePoints,
        cardCount: detail.assets.cardCount,
        status: detail.status,
        lastVisitAt: detail.lastVisitAt,
      }]
    }
  }
}

async function saveCreate() {
  if (!selectedMember.value || !createForm.newStoreId || !createForm.effectiveDate || !createForm.reason.trim()) {
    ElMessage.warning('请完整填写会员、新门店、生效日期和调整原因')
    return
  }
  let shareRule: Record<string, unknown>
  try {
    const parsed: unknown = JSON.parse(createForm.shareRuleText || '{}')
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error()
    shareRule = parsed as Record<string, unknown>
  } catch {
    ElMessage.warning('分润规则快照必须是JSON对象，例如 {}')
    return
  }
  createSaving.value = true
  try {
    const created = await createOwnershipAdjustment(selectedMember.value.id, {
      newStoreId: createForm.newStoreId,
      effectiveDate: createForm.effectiveDate,
      shareRule,
      reason: createForm.reason.trim(),
      memberVersion: selectedMember.value.version,
    })
    createVisible.value = false
    selected.value = created
    detailVisible.value = true
    await loadRows()
    ElMessage.success('归属调整申请已提交')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '归属调整申请失败')
  } finally {
    createSaving.value = false
  }
}

function showDetail(row: OwnershipAdjustment) {
  selected.value = row
  detailVisible.value = true
}

function openReview(approved: boolean) {
  reviewApproved.value = approved
  reviewComment.value = ''
  reviewVisible.value = true
}

async function saveReview() {
  if (!selected.value) return
  if (!reviewApproved.value && !reviewComment.value.trim()) {
    ElMessage.warning('驳回时必须填写意见')
    return
  }
  reviewSaving.value = true
  try {
    const payload = { comment: reviewComment.value.trim() || undefined, version: selected.value.version }
    selected.value = reviewApproved.value
      ? await approveOwnershipAdjustment(selected.value.id, payload)
      : await rejectOwnershipAdjustment(selected.value.id, payload)
    reviewVisible.value = false
    await loadRows()
    ElMessage.success(reviewApproved.value ? '申请已审批通过' : '申请已驳回')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审批操作失败')
  } finally {
    reviewSaving.value = false
  }
}

function disablePast(date: Date) {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  return date.getTime() < start.getTime()
}

onMounted(async () => {
  await loadRows()
  const memberId = Number(route.query.memberId)
  if (Number.isInteger(memberId) && memberId > 0 && auth.hasPermission('member:ownership:manage')) {
    await openCreate(memberId)
    await router.replace({ path: route.path })
  }
})
</script>

<template>
  <section class="page-content ownership-page">
    <div class="section-title-row">
      <div>
        <h1>会员归属调整</h1>
        <p>归属变更先审批，再按指定日期生效；历史账单和业绩不追溯改写。</p>
      </div>
      <el-button
        v-if="auth.hasPermission('member:ownership:manage')"
        type="primary"
        @click="openCreate()"
      >新建申请</el-button>
    </div>

    <el-card class="filter-card" shadow="never">
      <el-form inline>
        <el-form-item label="审批状态">
          <el-select v-model="filters.approvalStatus" clearable class="filter-select" placeholder="全部">
            <el-option v-for="(label, value) in approvalLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行状态">
          <el-select v-model="filters.executionStatus" clearable class="filter-select" placeholder="全部">
            <el-option v-for="(label, value) in executionLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="loadRows">查询</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card class="data-card" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe row-key="id" @row-click="showDetail">
        <el-table-column prop="adjustmentNo" label="申请单号" min-width="190" />
        <el-table-column label="会员" min-width="170">
          <template #default="scope"><strong>{{ scope.row.memberName }}</strong><br>{{ scope.row.memberNo }}</template>
        </el-table-column>
        <el-table-column label="归属变化" min-width="240">
          <template #default="scope">{{ scope.row.oldStoreName }} → {{ scope.row.newStoreName }}</template>
        </el-table-column>
        <el-table-column prop="effectiveDate" label="生效日期" width="120" />
        <el-table-column label="审批" width="105">
          <template #default="scope">
            <el-tag :type="approvalType(scope.row.approvalStatus)">
              {{ approvalLabels[scope.row.approvalStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行" width="115">
          <template #default="scope">
            <el-tag :type="executionType(scope.row.executionStatus)">
              {{ executionLabels[scope.row.executionStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="170">
          <template #default="scope">{{ scope.row.requestedAt.replace('T', ' ') }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="新建归属调整申请" width="680px" destroy-on-close>
      <el-alert
        title="审批通过后，今天生效的申请立即执行；未来日期由系统到期执行。旧账单归属不变。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form label-width="110px" class="dialog-form">
        <el-form-item label="会员" required>
          <el-select
            v-model="createForm.memberId"
            filterable
            remote
            :remote-method="searchMember"
            :loading="memberSearching"
            placeholder="输入姓名、手机号、会员号搜索"
            class="wide-control"
            @change="chooseMember"
          >
            <el-option
              v-for="member in memberOptions"
              :key="member.id"
              :label="`${member.fullName} · ${member.maskedMobile} · ${member.ownerStoreName}`"
              :value="member.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="当前归属">
          <el-input :model-value="selectedMember?.ownerStoreName ?? '请先选择会员'" disabled />
        </el-form-item>
        <el-form-item label="新归属门店" required>
          <el-select v-model="createForm.newStoreId" class="wide-control" placeholder="请选择">
            <el-option
              v-for="store in auth.user?.stores.filter((item) => item.status === 'ACTIVE' && item.id !== selectedMember?.ownerStoreId)"
              :key="store.id"
              :label="store.name"
              :value="store.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="生效日期" required>
          <el-date-picker
            v-model="createForm.effectiveDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled-date="disablePast"
          />
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="createForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="分润规则快照">
          <el-input v-model="createForm.shareRuleText" type="textarea" :rows="4" />
          <p class="form-hint">未确认分润规则时保持 {}。这里只保存已确认JSON快照，不在本页计算分润。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="saveCreate">提交申请</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="归属调整详情" size="600px">
      <template v-if="selected">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="申请单号">{{ selected.adjustmentNo }}</el-descriptions-item>
          <el-descriptions-item label="会员">
            <el-button link type="primary" @click="router.push(`/app/members/${selected.memberId}`)">
              {{ selected.memberName }}（{{ selected.memberNo }}）
            </el-button>
          </el-descriptions-item>
          <el-descriptions-item label="归属变化">{{ selected.oldStoreName }} → {{ selected.newStoreName }}</el-descriptions-item>
          <el-descriptions-item label="生效日期">{{ selected.effectiveDate }}</el-descriptions-item>
          <el-descriptions-item label="申请原因">{{ selected.reason }}</el-descriptions-item>
          <el-descriptions-item label="审批状态">{{ approvalLabels[selected.approvalStatus] }}</el-descriptions-item>
          <el-descriptions-item label="执行状态">{{ executionLabels[selected.executionStatus] }}</el-descriptions-item>
          <el-descriptions-item label="审批意见">{{ selected.reviewComment ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="执行结果">{{ selected.executionMessage ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="分润快照">
            <pre class="json-preview">{{ JSON.stringify(selected.shareRule, null, 2) }}</pre>
          </el-descriptions-item>
        </el-descriptions>
        <div
          v-if="selected.approvalStatus === 'PENDING' && auth.hasPermission('member:ownership:approve')"
          class="drawer-actions"
        >
          <el-button type="danger" plain @click="openReview(false)">驳回</el-button>
          <el-button type="primary" @click="openReview(true)">审批通过</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="reviewVisible" :title="reviewApproved ? '审批通过' : '驳回申请'" width="500px">
      <el-input
        v-model="reviewComment"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        :placeholder="reviewApproved ? '审批意见（选填）' : '驳回原因（必填）'"
      />
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button :type="reviewApproved ? 'primary' : 'danger'" :loading="reviewSaving" @click="saveReview">
          确认
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.filter-select {
  width: 150px;
}

.dialog-form {
  margin-top: 18px;
}

.wide-control {
  width: 100%;
}

.form-hint {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.json-preview {
  max-height: 180px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
</style>
