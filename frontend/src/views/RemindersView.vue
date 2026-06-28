<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/base/Card.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import ReminderFormSheet from '@/components/ReminderFormSheet.vue'
import { ApiError } from '@/api/client'
import { listReminders, type Reminder } from '@/api/reminders'

// NOTI-06 점검 리마인더 설정 화면 — 사용자 정의 리마인더 목록 + 추가·수정·삭제 폼 진입점.
// 짝 백엔드(GET|POST|PATCH|DELETE /reminders). soft delete라 목록은 활성만 온다.
// 분기 외화 예수금 점검은 서버가 분기마다 자동 발송(설정 없음)이라 안내 문구로만 알린다.
const router = useRouter()

const reminders = ref<Reminder[]>([])
const loading = ref(true)
const errorCode = ref<string | null>(null)

const sheetOpen = ref(false)
const editing = ref<Reminder | null>(null)

async function load() {
  loading.value = true
  errorCode.value = null
  try {
    reminders.value = await listReminders()
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    loading.value = false
  }
}

function openAdd() {
  editing.value = null
  sheetOpen.value = true
}

function openEdit(reminder: Reminder) {
  editing.value = reminder
  sheetOpen.value = true
}

function closeSheet() {
  sheetOpen.value = false
}

async function onSaved() {
  sheetOpen.value = false
  await load()
}

function goBack() {
  router.back()
}

onMounted(load)
</script>

<template>
  <section class="reminders">
    <header class="head">
      <button class="back" type="button" :aria-label="$t('reminders.back')" @click="goBack">‹</button>
      <h1 class="title">{{ $t('reminders.title') }}</h1>
    </header>

    <p class="intro">{{ $t('reminders.fxNote') }}</p>

    <p v-if="loading" class="state">{{ $t('reminders.loading') }}</p>
    <p v-else-if="errorCode" class="state error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <template v-else>
      <Card v-if="reminders.length > 0" class="list">
        <button
          v-for="reminder in reminders"
          :key="reminder.id"
          type="button"
          class="row"
          @click="openEdit(reminder)"
        >
          <span class="nm">{{ reminder.label }}</span>
          <span class="meta">
            {{ $t('reminders.every', { months: reminder.intervalMonths }) }} · {{ reminder.nextRemindDate }}
          </span>
        </button>
      </Card>

      <EmptyState v-else :title="$t('reminders.emptyTitle')" :body="$t('reminders.emptyBody')" />
    </template>

    <button class="btn add" type="button" @click="openAdd">{{ $t('reminders.add') }}</button>

    <ReminderFormSheet :open="sheetOpen" :reminder="editing" @close="closeSheet" @saved="onSaved" />
  </section>
</template>

<style scoped>
.reminders {
  flex: 1;
}
.head {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0 12px;
}
.back {
  font-size: 26px;
  line-height: 1;
  color: var(--sub);
  width: 32px;
  margin-left: -6px;
}
.title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.4px;
}
.intro {
  font-size: 13px;
  color: var(--hint);
  line-height: 1.5;
  padding: 0 4px 16px;
}
.state {
  font-size: 14px;
  color: var(--hint);
  text-align: center;
  padding: 32px 0;
}
.state.error {
  color: var(--red);
}
.list {
  padding: 4px 20px;
}
.row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  width: 100%;
  text-align: left;
  padding: 14px 0;
  border-bottom: 1px solid var(--line-2);
}
.row:last-child {
  border-bottom: 0;
}
.nm {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}
.meta {
  font-size: 13px;
  color: var(--hint);
}
.btn.add {
  display: block;
  width: 100%;
  padding: 15px;
  border-radius: var(--r);
  font-size: 15px;
  font-weight: 600;
  background: var(--blue);
  color: #fff;
  margin-top: 6px;
}
</style>
