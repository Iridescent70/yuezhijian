<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getRoles } from '@/api/platform'
import type { RoleSummary } from '@/types/api'

const loading = ref(true)
const roles = ref<RoleSummary[]>([])

onMounted(async () => {
  try {
    roles.value = await getRoles()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="page-content">
    <div class="section-title-row">
      <div><h1>角色管理</h1><p>菜单、按钮、接口和数据范围由同一角色规则控制。</p></div>
      <el-button type="primary" disabled>新建角色</el-button>
    </div>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="roles" stripe>
        <el-table-column prop="code" label="角色编码" width="190" />
        <el-table-column prop="name" label="角色名称" width="160" />
        <el-table-column prop="dataScope" label="数据范围" width="140" />
        <el-table-column label="权限">
          <template #default="scope">
            <el-tag v-for="permission in scope.row.permissions" :key="permission" class="permission-tag" effect="plain">
              {{ permission }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>
