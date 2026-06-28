<script setup lang="ts">
// 하단 시트 — 화면설계.html .dim/.sheet 토큰. open으로 제어, 배경 클릭 시 close.
defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <Teleport to="body">
    <Transition name="sheet">
      <div v-if="open" class="dim" @click.self="emit('close')">
        <div class="sheet" role="dialog" aria-modal="true">
          <div class="grab" />
          <slot />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.dim {
  position: fixed;
  inset: 0;
  background: rgba(25, 31, 40, 0.45);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  z-index: 100;
}
.sheet {
  background: #fff;
  border-radius: 24px 24px 0 0;
  padding: 24px 20px 28px;
  max-height: 80vh;
  overflow: auto;
  width: 100%;
  max-width: 430px;
  margin: 0 auto;
}
.grab {
  width: 36px;
  height: 4px;
  border-radius: 2px;
  background: var(--line-2);
  margin: 0 auto 16px;
}
/* 딤은 페이드, 시트는 아래에서 슬라이드업(정본 구현 노트: 바텀시트 슬라이드업 240ms). */
.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 0.24s ease;
}
.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}
.sheet {
  transition: transform 0.24s cubic-bezier(0.32, 0.72, 0, 1);
}
.sheet-enter-from .sheet,
.sheet-leave-to .sheet {
  transform: translateY(100%);
}
@media (prefers-reduced-motion: reduce) {
  .sheet {
    transition: none;
  }
  .sheet-enter-from .sheet,
  .sheet-leave-to .sheet {
    transform: none;
  }
}
/* 데스크톱(≥900px, tokens --bp-wide) — 바텀시트 대신 화면 중앙 모달. 모바일 슬라이드업은 그대로.
   기본 규칙들 뒤에 와야 같은 specificity에서 덮어쓴다(순서 의존). */
@media (min-width: 900px) {
  .dim {
    justify-content: center;
    align-items: center;
  }
  .sheet {
    border-radius: var(--r-lg);
    max-width: 480px;
    max-height: 85vh;
    margin: 0;
    padding: 28px 24px;
  }
  .grab {
    display: none;
  }
  /* 등장은 슬라이드업 대신 페이드 + 살짝 스케일. */
  .sheet-enter-from .sheet,
  .sheet-leave-to .sheet {
    transform: scale(0.96);
  }
}
@media (min-width: 900px) and (prefers-reduced-motion: reduce) {
  .sheet-enter-from .sheet,
  .sheet-leave-to .sheet {
    transform: none;
  }
}
</style>
