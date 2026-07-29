<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    await auth.login(form.username.trim(), form.password)
    const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/app/workbench'
    await router.replace(target)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-intro">
      <div class="intro-content">
        <span class="eyebrow">YUE ZHI JIAN</span>
        <h1>把门店每天的细节，<br />变成清楚的经营结果。</h1>
        <p>会员、预约、结算、资产与经营分析，使用同一套业务数据和权限规则。</p>
      </div>
    </section>
    <section class="login-panel">
      <div class="login-card">
        <div class="login-logo">悦</div>
        <h2>登录管理系统</h2>
        <p class="login-description">请输入管理员分配的账号和密码</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" size="large" autocomplete="username" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入密码"
            />
          </el-form-item>
          <el-button type="primary" size="large" class="login-submit" :loading="auth.loading" @click="submit">
            登录
          </el-button>
        </el-form>
        <p class="login-help">账号锁定或无法登录时，请联系系统管理员。</p>
      </div>
    </section>
  </main>
</template>
