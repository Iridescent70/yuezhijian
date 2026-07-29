<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createAppointment, getAppointmentAvailability } from '@/api/appointment'
import { getEmployees, getServices, getWorkstations } from '@/api/masterData'
import { searchMembers } from '@/api/member'
import { useAuthStore } from '@/stores/auth'
import type { AvailabilitySlot, EmployeeSummary, MemberSummary, ServiceItemSummary, WorkstationSummary } from '@/types/api'
import { formatMoney } from '@/utils/formatMoney'

const router = useRouter()
const auth = useAuthStore()
const saving = ref(false)
const resourceLoading = ref(false)
const memberLoading = ref(false)
const availabilityLoading = ref(false)
const availabilityVisible = ref(false)
const availability = ref<AvailabilitySlot[]>([])
const memberKeyword = ref('')
const members = ref<MemberSummary[]>([])
const employees = ref<EmployeeSummary[]>([])
const workstations = ref<WorkstationSummary[]>([])
const services = ref<ServiceItemSummary[]>([])
const defaultStore = auth.user?.stores.find((item) => item.id === 2)?.id ?? auth.user?.currentStoreId
const form = reactive({
  customerType: 'MEMBER', memberId: undefined as number | undefined, guestName: '', guestMobile: '',
  storeId: defaultStore as number | undefined, startAt: nextStart(), personCount: 1,
  employeeId: undefined as number | undefined, workstationId: undefined as number | undefined,
  serviceIds: [] as number[], designated: false, note: '',
})

const selectedServices = computed(() => services.value.filter((item) => form.serviceIds.includes(item.id)))
const totalMinutes = computed(() => selectedServices.value.reduce((sum, item) => sum + item.durationMinutes, 0))
const totalPrice = computed(() => selectedServices.value.reduce((sum, item) => sum + Number(item.storePrice), 0))

async function loadResources() {
  if (!form.storeId) return
  resourceLoading.value = true
  form.employeeId = undefined
  form.workstationId = undefined
  form.serviceIds = []
  try {
    ;[employees.value, workstations.value, services.value] = await Promise.all([
      getEmployees({ storeId: form.storeId }), getWorkstations(form.storeId), getServices({ storeId: form.storeId }),
    ])
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '预约资源加载失败') }
  finally { resourceLoading.value = false }
}

async function searchMemberOptions() {
  memberLoading.value = true
  try {
    const result = await searchMembers({ keyword: memberKeyword.value || undefined, storeId: form.storeId, page: 1, size: 30 })
    members.value = result.items
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '会员加载失败') }
  finally { memberLoading.value = false }
}

async function submit() {
  if (!form.storeId || !form.startAt || !form.employeeId || !form.workstationId || !form.serviceIds.length) {
    ElMessage.warning('请完整选择门店、时间、项目、技师和工位')
    return
  }
  if (form.customerType === 'MEMBER' && !form.memberId) { ElMessage.warning('请选择预约会员'); return }
  if (form.customerType === 'GUEST' && (!form.guestName.trim() || !form.guestMobile.trim())) { ElMessage.warning('请填写散客姓名和手机号'); return }
  saving.value = true
  try {
    await createAppointment({
      memberId: form.customerType === 'MEMBER' ? form.memberId : undefined,
      guestName: form.customerType === 'GUEST' ? form.guestName : undefined,
      guestMobile: form.customerType === 'GUEST' ? form.guestMobile : undefined,
      storeId: form.storeId, sourceType: 'PC', appointmentType: 'IN_STORE', startAt: form.startAt,
      personCount: form.personCount, employeeId: form.employeeId, workstationId: form.workstationId,
      serviceIds: form.serviceIds, designated: form.designated, note: form.note || undefined,
      idempotencyKey: crypto.randomUUID(),
    })
    ElMessage.success('预约已创建')
    await router.push('/app/appointments')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '预约创建失败') }
  finally { saving.value = false }
}

async function checkAvailability() {
  if (!form.storeId || !form.employeeId || form.serviceIds.length !== 1 || !form.startAt) {
    ElMessage.warning('请选择一项服务、技师和预约日期后查看可约时段')
    return
  }
  availabilityLoading.value = true
  availabilityVisible.value = true
  try {
    availability.value = await getAppointmentAvailability({
      storeId: form.storeId,
      employeeId: form.employeeId,
      serviceId: form.serviceIds[0]!,
      date: form.startAt.slice(0, 10),
    })
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '可约时段加载失败') }
  finally { availabilityLoading.value = false }
}

function chooseSlot(slot: AvailabilitySlot) {
  if (!slot.available) return
  form.startAt = slot.startAt
  availabilityVisible.value = false
}

function nextStart() {
  const date = new Date(Date.now() + 24 * 60 * 60 * 1000)
  date.setHours(10, 0, 0, 0)
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 19).replace('T', ' ')
}

watch(() => form.storeId, async () => { await Promise.all([loadResources(), searchMemberOptions()]) })
onMounted(async () => { await Promise.all([loadResources(), searchMemberOptions()]) })
</script>

<template>
  <section class="page-content">
    <div class="section-title-row"><div><h1>新建预约</h1><p>项目时长自动计算结束时间；同一技师或工位冲突时不能保存。</p></div><el-button @click="router.back()">返回</el-button></div>
    <el-card v-loading="resourceLoading" class="member-form-card appointment-form-card" shadow="never">
      <el-form label-position="top">
        <div class="form-section-title">客户与门店</div>
        <div class="member-form-grid">
          <el-form-item label="客户类型"><el-radio-group v-model="form.customerType"><el-radio-button value="MEMBER">会员</el-radio-button><el-radio-button value="GUEST">散客</el-radio-button></el-radio-group></el-form-item>
          <el-form-item label="预约门店" required><el-select v-model="form.storeId"><el-option v-for="store in auth.user?.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select></el-form-item>
          <el-form-item v-if="form.customerType === 'MEMBER'" label="预约会员" required>
            <el-select v-model="form.memberId" filterable :loading="memberLoading" placeholder="选择会员"><template #header><el-input v-model="memberKeyword" placeholder="输入姓名或手机号" @keyup.enter="searchMemberOptions"><template #append><el-button @click="searchMemberOptions">查询</el-button></template></el-input></template><el-option v-for="member in members" :key="member.id" :label="`${member.fullName} ${member.maskedMobile}`" :value="member.id" /></el-select>
          </el-form-item>
          <template v-else><el-form-item label="散客姓名" required><el-input v-model="form.guestName" maxlength="100" /></el-form-item><el-form-item label="散客手机号" required><el-input v-model="form.guestMobile" maxlength="11" /></el-form-item></template>
        </div>
        <el-divider />
        <div class="form-section-title">预约内容</div>
        <div class="member-form-grid">
          <el-form-item label="开始时间" required><div class="appointment-time-control"><el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" :minute-step="5" /><el-button @click="checkAvailability">查看可约时段</el-button></div></el-form-item>
          <el-form-item label="到店人数" required><el-input-number v-model="form.personCount" :min="1" :max="100" /></el-form-item>
          <el-form-item label="服务项目" required><el-select v-model="form.serviceIds" multiple collapse-tags><el-option v-for="item in services" :key="item.id" :label="`${item.name} · ${item.durationMinutes}分钟 · ${formatMoney(item.storePrice)}`" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="预约合计"><div class="appointment-total"><strong>{{ totalMinutes }} 分钟</strong><span>{{ formatMoney(totalPrice) }}</span></div></el-form-item>
          <el-form-item label="服务技师" required><el-select v-model="form.employeeId"><el-option v-for="item in employees.filter((employee) => employee.canService)" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="指定技师"><el-switch v-model="form.designated" active-text="客户指定" inactive-text="门店安排" /></el-form-item>
          <el-form-item label="服务工位" required><el-select v-model="form.workstationId"><el-option v-for="item in workstations" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="预约备注"><el-input v-model="form.note" maxlength="1000" /></el-form-item>
        </div>
        <div class="form-actions"><el-button @click="router.back()">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存预约</el-button></div>
      </el-form>
    </el-card>
    <el-dialog v-model="availabilityVisible" title="可约时段" width="680px">
      <div v-loading="availabilityLoading" class="availability-grid"><button v-for="slot in availability" :key="slot.startAt" type="button" :disabled="!slot.available" :class="{ unavailable: !slot.available }" @click="chooseSlot(slot)">{{ slot.startAt.slice(11, 16) }}<small>{{ slot.available ? '可预约' : '已占用' }}</small></button></div>
    </el-dialog>
  </section>
</template>
