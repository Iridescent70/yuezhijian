<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getOperationHistory } from '@/api/audit'
import type { OperationHistoryItem } from '@/types/api'

const props = defineProps<{
  objectType: 'PRODUCT' | 'SERVICE'
  objectId?: number
  title: string
}>()
const visible = defineModel<boolean>({ required: true })
const loading = ref(false)
const rows = ref<OperationHistoryItem[]>([])

watch(visible, async (opened) => {
  if (!opened || !props.objectId) return
  loading.value = true
  try {
    rows.value = await getOperationHistory(props.objectType, props.objectId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作历史加载失败')
  } finally {
    loading.value = false
  }
})

function formatTime(value: string) {
  return value.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <el-drawer v-model="visible" :title="`${title} · 操作历史`" size="720px" destroy-on-close>
    <div v-loading="loading" class="history-content">
      <el-empty v-if="!loading && !rows.length" description="暂无操作历史" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="item in rows"
          :key="item.id"
          :timestamp="formatTime(item.occurredAt)"
          placement="top"
        >
          <el-card shadow="never">
            <div class="history-heading">
              <strong>{{ item.actionLabel }}</strong>
              <span>{{ item.operatorName }}</span>
            </div>
            <el-table :data="item.changes" size="small" border class="history-change-table">
              <el-table-column prop="label" label="字段" width="145" />
              <el-table-column label="修改前" min-width="190"><template #default="scope">{{ scope.row.beforeValue ?? '—' }}</template></el-table-column>
              <el-table-column label="修改后" min-width="190"><template #default="scope">{{ scope.row.afterValue ?? '—' }}</template></el-table-column>
            </el-table>
            <small class="muted-text">追踪号：{{ item.traceId }}</small>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </div>
  </el-drawer>
</template>

<style scoped>
.history-content { min-height: 180px; }
.history-heading { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.history-heading span { color: var(--el-text-color-secondary); }
.history-change-table { margin-bottom: 10px; }
</style>
