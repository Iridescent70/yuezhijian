<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { searchMembers } from '@/api/member'
import { useAuthStore } from '@/stores/auth'
import type { MemberSummary } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const members = ref<MemberSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
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

onMounted(loadMembers)
</script>

<template>
  <section class="page-content member-list-page">
    <div class="section-title-row">
      <div>
        <h1>会员管理</h1>
        <p>统一查询会员档案、归属门店和当前资产，手机号默认脱敏展示。</p>
      </div>
      <el-button
        v-if="auth.hasPermission('member:member:create')"
        type="primary"
        @click="router.push('/app/members/new')"
      >
        新建会员
      </el-button>
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
      <el-table v-loading="loading" :data="members" stripe row-key="id">
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
  </section>
</template>
