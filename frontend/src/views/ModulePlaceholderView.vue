<script setup lang="ts">
defineProps<{ moduleName: string; nextMilestone: string }>()
</script>

<template>
  <section class="page-content placeholder-page">
    <div class="placeholder-mark">{{ moduleName.slice(0, 1) }}</div>
    <h1>{{ moduleName }}</h1>
    <p>路由与权限入口已建立，业务页面将在 {{ nextMilestone }} 中按计划开发。</p>
  </section>
</template>
