<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getStores } from '@/api/platform'
import type { StoreSummary } from '@/types/api'

const loading = ref(true)
const stores = ref<StoreSummary[]>([])

onMounted(async () => {
  try {
    stores.value = await getStores()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '门店加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>组织门店</h1><p>当前为工程基线数据，接入SQL Server后由组织模块维护。</p></div>
      <el-button type="primary" disabled>新建门店</el-button>
    </div>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="stores" stripe>
        <el-table-column prop="code" label="门店编码" width="160" />
        <el-table-column prop="name" label="门店名称" min-width="220" />
        <el-table-column prop="level" label="等级/类型" width="160" />
        <el-table-column label="状态" width="120">
          <template #default="scope"><el-tag type="success">{{ scope.row.status }}</el-tag></template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>
