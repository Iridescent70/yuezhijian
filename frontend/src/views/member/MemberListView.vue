<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createExport } from '@/api/jobs'
import {
  batchAssignMemberAdvisor,
  batchFreezeMembers,
  batchUpdateMemberTags,
  getMemberTagOptions,
  searchMembers,
} from '@/api/member'
import { getEmployees } from '@/api/masterData'
import { useAuthStore } from '@/stores/auth'
import type { EmployeeSummary, MemberBatchResult, MemberSummary, MemberTagOption } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const exporting = ref(false)
const members = ref<MemberSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const selectedMembers = ref<MemberSummary[]>([])
const freezeVisible = ref(false)
const tagVisible = ref(false)
const advisorVisible = ref(false)
const resultVisible = ref(false)
const batchSubmitting = ref(false)
const freezeReason = ref('')
const tagOptions = ref<MemberTagOption[]>([])
const addTagIds = ref<number[]>([])
const removeTagIds = ref<number[]>([])
const advisorOptions = ref<EmployeeSummary[]>([])
const advisorEmployeeId = ref<number>()
const batchResult = ref<MemberBatchResult>()
const filters = reactive<{ keyword: string; storeId?: number; status: string }>({
  keyword: '',
  storeId: undefined,
  status: '',
})

const statusMap: Record<string, { label: string; type: 'success' | 'warning' | 'info' }> = {
  ACTIVE: { label: '正常', type: 'success' },
  FROZEN: { label: '已冻结', type: 'warning' },
  INACTIVE: { label: '已停用', type: 'info' },
}

const resultStatusMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' }> = {
  SUCCESS: { label: '成功', type: 'success' },
  SKIPPED: { label: '跳过', type: 'warning' },
  FAILED: { label: '失败', type: 'danger' },
}

const operationLabels: Record<string, string> = {
  FREEZE: '批量冻结',
  UPDATE_TAGS: '批量标签',
  ASSIGN_ADVISOR: '批量分配顾问',
}

async function loadMembers() {
  loading.value = true
  try {
    const result = await searchMembers({
      keyword: filters.keyword || undefined,
      storeId: filters.storeId,
      status: filters.status || undefined,
      page: page.value,
      size: size.value,
    })
    members.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会员列表加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void loadMembers()
}

function reset() {
  filters.keyword = ''
  filters.storeId = undefined
  filters.status = ''
  search()
}

function changePage(nextPage: number) {
  page.value = nextPage
  void loadMembers()
}

function changeSize(nextSize: number) {
  size.value = nextSize
  page.value = 1
  void loadMembers()
}

async function exportCurrentStore() {
  if (filters.storeId && filters.storeId !== auth.user?.currentStoreId) {
    ElMessage.warning('会员导出固定使用当前登录门店，请切换门店后再导出')
    return
  }
  exporting.value = true
  try {
    await createExport({
      exportType: 'MEMBER',
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
    })
    ElMessage.success('会员导出任务已创建')
    await router.push('/app/system/downloads')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会员导出任务创建失败')
  } finally {
    exporting.value = false
  }
}

function selectionChanged(rows: MemberSummary[]) {
  selectedMembers.value = rows
}

function selectedIds() {
  return selectedMembers.value.map((member) => member.id)
}

function openFreeze() {
  freezeReason.value = ''
  freezeVisible.value = true
}

async function openTags() {
  addTagIds.value = []
  removeTagIds.value = []
  try {
    tagOptions.value = await getMemberTagOptions()
    tagVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '标签选项加载失败')
  }
}

async function openAdvisor() {
  const storeIds = new Set(selectedMembers.value.map((member) => member.ownerStoreId))
  if (storeIds.size !== 1) {
    ElMessage.warning('批量分配顾问时请选择同一归属门店的会员')
    return
  }
  const storeId = selectedMembers.value[0]?.ownerStoreId
  if (!storeId) return
  try {
    advisorOptions.value = (await getEmployees({ storeId })).filter((employee) => employee.status === 'ACTIVE')
    advisorEmployeeId.value = undefined
    advisorVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '顾问选项加载失败')
  }
}

async function showResult(task: () => Promise<MemberBatchResult>) {
  batchSubmitting.value = true
  try {
    batchResult.value = await task()
    freezeVisible.value = false
    tagVisible.value = false
    advisorVisible.value = false
    resultVisible.value = true
    selectedMembers.value = []
    await loadMembers()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量操作失败')
  } finally {
    batchSubmitting.value = false
  }
}

function submitFreeze() {
  if (!freezeReason.value.trim()) {
    ElMessage.warning('请填写冻结原因')
    return
  }
  void showResult(() => batchFreezeMembers({ memberIds: selectedIds(), reason: freezeReason.value.trim() }))
}

function submitTags() {
  if (!addTagIds.value.length && !removeTagIds.value.length) {
    ElMessage.warning('请选择要添加或移除的标签')
    return
  }
  if (addTagIds.value.some((id) => removeTagIds.value.includes(id))) {
    ElMessage.warning('同一标签不能同时添加和移除')
    return
  }
  void showResult(() => batchUpdateMemberTags({
    memberIds: selectedIds(),
    addIds: addTagIds.value,
    removeIds: removeTagIds.value,
  }))
}

function submitAdvisor() {
  if (!advisorEmployeeId.value) {
    ElMessage.warning('请选择顾问')
    return
  }
  void showResult(() => batchAssignMemberAdvisor({
    memberIds: selectedIds(),
    employeeId: advisorEmployeeId.value as number,
  }))
}

onMounted(loadMembers)
</script>

<template>
  <section class="page-content member-list-page">
    <div class="section-title-row">
      <div>
        <h1>会员管理</h1>
        <p>统一查询会员档案、归属门店和当前资产，手机号默认脱敏展示。</p>
      </div>
      <div>
        <el-button
          v-if="auth.hasPermission('system:job:create') && auth.hasPermission('system:job:view') && auth.hasPermission('member:member:export')"
          :loading="exporting"
          @click="exportCurrentStore"
        >导出当前门店</el-button>
        <el-button
          v-if="auth.hasPermission('member:member:create')"
          type="primary"
          @click="router.push('/app/members/new')"
        >新建会员</el-button>
      </div>
    </div>

    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="search">
        <el-form-item label="会员查询">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="姓名、手机号、会员号或卡号"
            class="member-keyword-input"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="归属门店">
          <el-select v-model="filters.storeId" clearable placeholder="全部门店" class="member-filter-select">
            <el-option
              v-for="store in auth.user?.stores"
              :key="store.id"
              :label="store.name"
              :value="store.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" class="member-filter-select">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="已冻结" value="FROZEN" />
            <el-option label="已停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="data-card" shadow="never">
      <div
        v-if="auth.hasPermission('member:member:manage') || auth.hasPermission('member:tag:manage')"
        class="member-batch-toolbar"
      >
        <span>已选择 {{ selectedMembers.length }} 位</span>
        <div>
          <el-button
            v-if="auth.hasPermission('member:tag:manage')"
            :disabled="!selectedMembers.length"
            @click="openTags"
          >批量标签</el-button>
          <el-button
            v-if="auth.hasPermission('member:member:manage')"
            :disabled="!selectedMembers.length"
            @click="openAdvisor"
          >分配顾问</el-button>
          <el-button
            v-if="auth.hasPermission('member:member:manage')"
            type="warning"
            plain
            :disabled="!selectedMembers.length"
            @click="openFreeze"
          >批量冻结</el-button>
        </div>
      </div>
      <el-table
        v-loading="loading"
        :data="members"
        stripe
        row-key="id"
        @selection-change="selectionChanged"
      >
        <el-table-column
          v-if="auth.hasPermission('member:member:manage') || auth.hasPermission('member:tag:manage')"
          type="selection"
          width="48"
        />
        <el-table-column label="会员" min-width="210">
          <template #default="scope">
            <button class="member-link" type="button" @click="router.push(`/app/members/${scope.row.id}`)">
              <strong>{{ scope.row.fullName }}</strong>
              <small>{{ scope.row.memberNo }}</small>
            </button>
          </template>
        </el-table-column>
        <el-table-column prop="maskedMobile" label="手机号" width="150" />
        <el-table-column prop="levelName" label="等级" width="120" />
        <el-table-column prop="ownerStoreName" label="归属门店" min-width="160" />
        <el-table-column label="储值余额" width="140" align="right">
          <template #default="scope">{{ formatMoney(scope.row.availableBalance) }}</template>
        </el-table-column>
        <el-table-column prop="availablePoints" label="积分" width="100" align="right" />
        <el-table-column prop="cardCount" label="有效次卡" width="100" align="right" />
        <el-table-column label="最近到店" width="170">
          <template #default="scope">{{ scope.row.lastVisitAt?.replace('T', ' ') ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" fixed="right">
          <template #default="scope">
            <el-tag :type="statusMap[scope.row.status]?.type ?? 'info'">
              {{ statusMap[scope.row.status]?.label ?? scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <span>共 {{ total }} 位会员</span>
        <el-pagination
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next"
          @update:current-page="changePage"
          @update:page-size="changeSize"
        />
      </div>
    </el-card>

    <el-dialog v-model="freezeVisible" title="批量冻结会员" width="520px">
      <el-alert
        :title="`将处理已选择的 ${selectedMembers.length} 位会员；已冻结会员会自动跳过。`"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form label-position="top" class="member-batch-form">
        <el-form-item label="冻结原因" required>
          <el-input v-model="freezeReason" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="freezeVisible = false">取消</el-button>
        <el-button type="warning" :loading="batchSubmitting" @click="submitFreeze">确认冻结</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tagVisible" title="批量维护标签" width="560px">
      <el-alert
        title="只写入实际发生的添加或移除；已经处于目标状态的会员会自动跳过。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form label-position="top" class="member-batch-form">
        <el-form-item label="添加标签">
          <el-select v-model="addTagIds" multiple clearable class="dialog-full-control" placeholder="可不添加">
            <el-option v-for="tag in tagOptions" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="移除标签">
          <el-select v-model="removeTagIds" multiple clearable class="dialog-full-control" placeholder="可不移除">
            <el-option v-for="tag in tagOptions" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitTags">确认更新</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="advisorVisible" title="批量分配顾问" width="520px">
      <el-alert
        title="顾问必须为会员当前归属门店的在职员工，变更前后值会写入历史记录。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form label-position="top" class="member-batch-form">
        <el-form-item label="新顾问" required>
          <el-select v-model="advisorEmployeeId" class="dialog-full-control" placeholder="请选择顾问">
            <el-option
              v-for="employee in advisorOptions"
              :key="employee.id"
              :label="`${employee.name}（${employee.employeeNo}）`"
              :value="employee.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="advisorVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitAdvisor">确认分配</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" title="批量操作结果" width="760px">
      <template v-if="batchResult">
        <div class="member-batch-summary">
          <strong>{{ operationLabels[batchResult.operation] }}</strong>
          <span>总计 {{ batchResult.total }}</span>
          <span class="batch-success">成功 {{ batchResult.succeeded }}</span>
          <span>跳过 {{ batchResult.skipped }}</span>
          <span class="batch-failed">失败 {{ batchResult.failed }}</span>
        </div>
        <el-table :data="batchResult.items" max-height="420" stripe>
          <el-table-column prop="memberNo" label="会员号" width="150">
            <template #default="scope">{{ scope.row.memberNo ?? `ID ${scope.row.memberId}` }}</template>
          </el-table-column>
          <el-table-column prop="memberName" label="会员" width="130">
            <template #default="scope">{{ scope.row.memberName ?? '未找到' }}</template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="scope">
              <el-tag :type="resultStatusMap[scope.row.status]?.type">
                {{ resultStatusMap[scope.row.status]?.label ?? scope.row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="240" />
        </el-table>
      </template>
      <template #footer><el-button type="primary" @click="resultVisible = false">关闭</el-button></template>
    </el-dialog>
  </section>
</template>
