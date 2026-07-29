<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createMember } from '@/api/member'
import { getStores } from '@/api/platform'
import type { CreateMemberPayload, StoreSummary } from '@/types/api'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const stores = ref<StoreSummary[]>([])
const form = reactive<CreateMemberPayload>({
  fullName: '',
  nickname: '',
  mobile: '',
  gender: 'UNKNOWN',
  birthday: undefined,
  email: '',
  sourceType: 'MANUAL',
  joinStoreId: 0,
  ownerStoreId: undefined,
  membershipCardNo: '',
})

const rules: FormRules<CreateMemberPayload> = {
  fullName: [
    { required: true, message: '请输入会员姓名', trigger: 'blur' },
    { max: 100, message: '姓名不能超过100个字符', trigger: 'blur' },
  ],
  mobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的11位手机号', trigger: 'blur' },
  ],
  joinStoreId: [{ required: true, message: '请选择入会门店', trigger: 'change' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

async function loadStores() {
  try {
    stores.value = await getStores()
    const preferred = stores.value.find((store) => store.code !== 'HQ') ?? stores.value[0]
    if (preferred) {
      form.joinStoreId = preferred.id
      form.ownerStoreId = preferred.id
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '门店加载失败')
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const result = await createMember({
      ...form,
      nickname: form.nickname || undefined,
      email: form.email || undefined,
      birthday: form.birthday || undefined,
      membershipCardNo: form.membershipCardNo || undefined,
      ownerStoreId: form.ownerStoreId || form.joinStoreId,
    })
    ElMessage.success(`会员 ${result.memberNo} 建档成功`)
    await router.replace(`/app/members/${result.memberId}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会员建档失败')
  } finally {
    submitting.value = false
  }
}

function syncOwnerStore() {
  if (!form.ownerStoreId) form.ownerStoreId = form.joinStoreId
}

onMounted(loadStores)
</script>

<template>
  <section class="page-content member-form-page">
    <div class="section-title-row">
      <div>
        <h1>新建会员</h1>
        <p>完成基础档案、入会门店和会员卡登记；资产账户由系统同步建立。</p>
      </div>
      <el-button @click="router.push('/app/members')">返回列表</el-button>
    </div>

    <el-card class="member-form-card" shadow="never">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" status-icon>
        <div class="form-section-title">基本信息</div>
        <div class="member-form-grid">
          <el-form-item label="会员姓名" prop="fullName">
            <el-input v-model="form.fullName" maxlength="100" placeholder="请输入姓名" />
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="form.nickname" maxlength="100" placeholder="选填" />
          </el-form-item>
          <el-form-item label="手机号" prop="mobile">
            <el-input v-model="form.mobile" maxlength="11" placeholder="用于会员识别，系统加密保存" />
          </el-form-item>
          <el-form-item label="性别" prop="gender">
            <el-select v-model="form.gender">
              <el-option label="未填写" value="UNKNOWN" />
              <el-option label="女" value="FEMALE" />
              <el-option label="男" value="MALE" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="生日" prop="birthday">
            <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" placeholder="选择生日" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" maxlength="255" placeholder="选填" />
          </el-form-item>
        </div>

        <el-divider />
        <div class="form-section-title">归属与入会</div>
        <div class="member-form-grid">
          <el-form-item label="入会门店" prop="joinStoreId">
            <el-select v-model="form.joinStoreId" @change="syncOwnerStore">
              <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="归属门店" prop="ownerStoreId">
            <el-select v-model="form.ownerStoreId">
              <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="会员来源" prop="sourceType">
            <el-select v-model="form.sourceType">
              <el-option label="门店建档" value="MANUAL" />
              <el-option label="历史导入" value="IMPORT" />
              <el-option label="到家服务" value="HOME_SERVICE" />
              <el-option label="第三方平台" value="THIRD_PARTY" />
            </el-select>
          </el-form-item>
          <el-form-item label="指定会员卡号" prop="membershipCardNo">
            <el-input v-model="form.membershipCardNo" maxlength="64" placeholder="留空则自动生成" />
          </el-form-item>
        </div>

        <div class="form-actions">
          <el-button @click="router.push('/app/members')">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submit">保存并查看会员</el-button>
        </div>
      </el-form>
    </el-card>
  </section>
</template>
