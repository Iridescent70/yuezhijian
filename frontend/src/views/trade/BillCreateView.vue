<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createBill } from '@/api/trade'
import { searchMembers } from '@/api/member'
import { useAuthStore } from '@/stores/auth'
import type { MemberSummary } from '@/types/api'

const router = useRouter()
const auth = useAuthStore()
const saving = ref(false)
const memberLoading = ref(false)
const memberKeyword = ref('')
const members = ref<MemberSummary[]>([])
const defaultStore = auth.user?.currentStoreId ?? auth.user?.stores[0]?.id
const form = reactive({ customerType: 'MEMBER', memberId: undefined as number | undefined, guestName: '', guestMobile: '', storeId: defaultStore as number | undefined, personCount: 1, note: '' })

async function searchMemberOptions() {
  memberLoading.value = true
  try { members.value = (await searchMembers({ keyword: memberKeyword.value || undefined, storeId: form.storeId, page: 1, size: 30 })).items }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '会员加载失败') }
  finally { memberLoading.value = false }
}

async function submit() {
  if (!form.storeId) return
  if (form.customerType === 'MEMBER' && !form.memberId) { ElMessage.warning('请选择会员'); return }
  if (form.customerType === 'GUEST' && (!form.guestName.trim() || !form.guestMobile.trim())) { ElMessage.warning('请填写散客姓名和手机号'); return }
  saving.value = true
  try {
    const bill = await createBill({ memberId: form.customerType === 'MEMBER' ? form.memberId : undefined, guestName: form.customerType === 'GUEST' ? form.guestName : undefined, guestMobile: form.customerType === 'GUEST' ? form.guestMobile : undefined, storeId: form.storeId, sourceType: 'PC', personCount: form.personCount, note: form.note || undefined, idempotencyKey: crypto.randomUUID() })
    ElMessage.success('账单草稿已创建，请添加消费项目')
    await router.replace(`/app/bills/${bill.id}`)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '账单创建失败') }
  finally { saving.value = false }
}
onMounted(searchMemberOptions)
</script>

<template>
  <section class="page-content"><div class="section-title-row"><div><h1>新建账单</h1><p>先建立客户账单，再在详情中添加多个消费项目和服务技师。</p></div><el-button @click="router.back()">返回</el-button></div><el-card class="member-form-card" shadow="never"><el-form label-position="top"><div class="member-form-grid"><el-form-item label="客户类型"><el-radio-group v-model="form.customerType"><el-radio-button value="MEMBER">会员</el-radio-button><el-radio-button value="GUEST">散客</el-radio-button></el-radio-group></el-form-item><el-form-item label="开单门店" required><el-select v-model="form.storeId"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item><el-form-item v-if="form.customerType === 'MEMBER'" label="消费会员" required><el-select v-model="form.memberId" filterable :loading="memberLoading"><template #header><el-input v-model="memberKeyword" @keyup.enter="searchMemberOptions"><template #append><el-button @click="searchMemberOptions">查询</el-button></template></el-input></template><el-option v-for="member in members" :key="member.id" :label="`${member.fullName} ${member.maskedMobile}`" :value="member.id" /></el-select></el-form-item><template v-else><el-form-item label="散客姓名" required><el-input v-model="form.guestName" /></el-form-item><el-form-item label="散客手机号" required><el-input v-model="form.guestMobile" maxlength="11" /></el-form-item></template><el-form-item label="消费人数"><el-input-number v-model="form.personCount" :min="1" :max="100" /></el-form-item><el-form-item label="备注"><el-input v-model="form.note" maxlength="1000" /></el-form-item></div><div class="form-actions"><el-button @click="router.back()">取消</el-button><el-button type="primary" :loading="saving" @click="submit">创建并添加项目</el-button></div></el-form></el-card></section>
</template>
