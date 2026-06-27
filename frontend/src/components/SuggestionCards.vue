<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Card from '@/components/base/Card.vue'
import { ApiError } from '@/api/client'
import {
  listSuggestions,
  applySuggestion,
  dismissSuggestion,
  type Suggestion,
} from '@/api/suggestions'

// MOD-06 제안 확인·반영(SUG-01~03). 서버가 일일 배치로 만든 PENDING 제안을 보라(제안 색) 카드로 노출하고,
// 반영(APPLIED)·닫기(DISMISSED)로 해소한다. 서버는 type+payload 구조화 데이터만 주므로(규칙 7) 문구는 여기서
// i18n 템플릿으로 조립한다. 홈(SCR-03)·리포트(SCR-06) 어디에 놓아도 스스로 로드해 제안이 없으면 아무것도 그리지 않는다.
//
// 반영의 범위: 서버 반영은 상태 전이만 한다(폭포 자동 변경 없음, 소유자 도메인). 카드는 payload 권고치를 보여
// 사용자가 직접 편집하도록 안내하는 advisory다.

const suggestions = ref<Suggestion[]>([])
const errorCode = ref<string | null>(null)
const busyId = ref<number | null>(null) // 반영/닫기 진행 중인 카드(중복 클릭 가드)

function num(payload: Record<string, unknown>, key: string): number {
  const v = payload[key]
  return typeof v === 'number' ? v : 0
}
function str(payload: Record<string, unknown>, key: string): string {
  const v = payload[key]
  return typeof v === 'string' ? v : ''
}
function fmt(n: number): string {
  return n.toLocaleString('ko-KR')
}

// 제안 → 표시 모델(제목·본문 i18n 키 + 파라미터). 문장은 i18n 템플릿이 조립한다(규칙 7).
type Params = Record<string, string | number>
interface CardView {
  id: number
  titleKey: string
  titleParams: Params
  bodyKey: string
  bodyParams: Params
  extra?: { key: string; params: Params }
}

const cards = computed<CardView[]>(() =>
  suggestions.value.map((s): CardView => {
    const p = s.payload
    if (s.type === 'RAISE_LIVING') {
      return {
        id: s.id,
        titleKey: 'suggestion.raiseLiving.title',
        titleParams: {},
        bodyKey: 'suggestion.raiseLiving.body',
        bodyParams: {
          streak: num(p, 'streak'),
          avg: fmt(num(p, 'avgOverspend')),
          amount: fmt(num(p, 'suggestedIncrease')),
        },
      }
    }
    if (s.type === 'RAISE_SAVING') {
      return {
        id: s.id,
        titleKey: 'suggestion.raiseSaving.title',
        titleParams: {},
        bodyKey: 'suggestion.raiseSaving.body',
        bodyParams: {
          streak: num(p, 'streak'),
          avg: fmt(num(p, 'avgSurplus')),
          amount: fmt(num(p, 'suggestedIncrease')),
        },
      }
    }
    // REBALANCE_MATURITY
    return {
      id: s.id,
      titleKey: 'suggestion.maturity.title',
      titleParams: { name: str(p, 'itemName') },
      bodyKey: 'suggestion.maturity.body',
      bodyParams: { monthly: fmt(num(p, 'monthlyAmount')), date: str(p, 'maturityDate') },
      extra:
        typeof p.expectedMaturityAmount === 'number'
          ? { key: 'suggestion.maturity.expected', params: { amount: fmt(p.expectedMaturityAmount) } }
          : undefined,
    }
  }),
)

async function load() {
  errorCode.value = null
  try {
    suggestions.value = await listSuggestions()
  } catch {
    // 제안은 보조 위젯이라 조회 실패 시 조용히 숨긴다(다른 화면 콘텐츠를 막지 않음).
    suggestions.value = []
  }
}

function remove(id: number) {
  suggestions.value = suggestions.value.filter((s) => s.id !== id)
}

async function apply(id: number) {
  if (busyId.value) return
  busyId.value = id
  errorCode.value = null
  try {
    await applySuggestion(id)
    remove(id)
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    busyId.value = null
  }
}

async function dismiss(id: number) {
  if (busyId.value) return
  busyId.value = id
  errorCode.value = null
  try {
    await dismissSuggestion(id)
    remove(id)
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    busyId.value = null
  }
}

onMounted(load)
defineExpose({ load })
</script>

<template>
  <div v-if="cards.length" class="suggestions">
    <Card v-for="c in cards" :key="c.id" class="sg-card">
      <p class="sg-title">{{ $t(c.titleKey, c.titleParams) }}</p>
      <p class="sg-body">{{ $t(c.bodyKey, c.bodyParams) }}</p>
      <p v-if="c.extra" class="sg-extra">{{ $t(c.extra.key, c.extra.params) }}</p>
      <div class="sg-actions">
        <button
          type="button"
          class="sg-btn dismiss"
          :disabled="busyId === c.id"
          @click="dismiss(c.id)"
        >
          {{ $t('suggestion.dismiss') }}
        </button>
        <button
          type="button"
          class="sg-btn apply"
          :disabled="busyId === c.id"
          @click="apply(c.id)"
        >
          {{ $t('suggestion.apply') }}
        </button>
      </div>
    </Card>
    <p v-if="errorCode" class="sg-error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>
  </div>
</template>

<style scoped>
.suggestions {
  margin-bottom: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.sg-card {
  padding: 18px;
  border-left: 3px solid var(--purple);
  background: var(--purple-soft);
}
.sg-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--purple);
  letter-spacing: -0.3px;
}
.sg-body {
  font-size: 13px;
  color: var(--ink);
  margin-top: 8px;
  line-height: 1.6;
}
.sg-extra {
  font-size: 12px;
  color: var(--sub);
  margin-top: 4px;
}
.sg-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}
.sg-btn {
  flex: 1;
  padding: 11px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
}
.sg-btn.dismiss {
  background: var(--surface);
  color: var(--sub);
}
.sg-btn.apply {
  background: var(--purple);
  color: #fff;
}
.sg-btn:disabled {
  opacity: 0.5;
}
.sg-error {
  font-size: 13px;
  color: var(--red);
  background: var(--red-soft);
  border-radius: var(--r);
  padding: 12px 14px;
  line-height: 1.5;
}
</style>
