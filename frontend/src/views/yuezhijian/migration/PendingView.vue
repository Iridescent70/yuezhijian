<script lang="ts" setup>
const route = useRoute()

const title = computed(() => String(route.meta.title || '业务功能'))
</script>

<template>
  <ContentWrap :title="title">
    <ElResult icon="info" title="正在迁移到芋道底座">
      <template #sub-title>
        <p class="m-0 leading-7">
          该功能在 main 分支已有实现或已进入计划，当前页面正在适配新的布局、会话权限和 API 约定。
        </p>
        <p class="m-0 leading-7 text-gray-500">原路由：{{ route.fullPath }}</p>
      </template>
    </ElResult>
  </ContentWrap>
</template>
