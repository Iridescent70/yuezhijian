<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import SidebarMenu from './SidebarMenu.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const activeMenu = computed(() => route.path)

async function handleLogout() {
  await ElMessageBox.confirm('确认退出当前账号吗？', '退出登录', { type: 'warning' })
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <el-container class="app-shell">
    <el-aside :width="collapsed ? '72px' : '228px'" class="app-sidebar">
      <div class="brand" :class="{ collapsed }">
        <span class="brand-mark">悦</span>
        <span v-if="!collapsed" class="brand-name">悦·指间</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        router
        class="sidebar-menu"
        background-color="transparent"
        text-color="#e8dde1"
        active-text-color="#ffffff"
      >
        <SidebarMenu :menus="auth.user?.menus ?? []" />
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <button class="collapse-button" type="button" @click="collapsed = !collapsed">
          {{ collapsed ? '展开' : '收起' }}
        </button>
        <div class="header-actions">
          <el-select :model-value="auth.user?.currentStoreId" class="store-selector" disabled>
            <el-option
              v-for="store in auth.user?.stores"
              :key="store.id"
              :label="store.name"
              :value="store.id"
            />
          </el-select>
          <el-dropdown trigger="click">
            <button class="user-button" type="button">
              <span class="avatar">{{ auth.user?.fullName.slice(0, 1) }}</span>
              <span>{{ auth.user?.fullName }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="app-main">
        <div class="page-heading">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>悦·指间</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
