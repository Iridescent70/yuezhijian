<script setup lang="ts">
import type { MenuItem } from '@/types/api'

defineProps<{ menus: MenuItem[] }>()
</script>

<template>
  <template v-for="menu in menus" :key="menu.id">
    <el-sub-menu v-if="menu.children.length" :index="menu.code">
      <template #title>
        <span>{{ menu.name }}</span>
      </template>
      <SidebarMenu :menus="menu.children" />
    </el-sub-menu>
    <el-menu-item v-else :index="menu.route">
      <span>{{ menu.name }}</span>
    </el-menu-item>
  </template>
</template>
