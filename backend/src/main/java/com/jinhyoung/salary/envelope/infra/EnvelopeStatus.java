package com.jinhyoung.salary.envelope.infra;

/**
 * 봉투 상태(ERD envelopes.status, ENV-01). ACTIVE만 목록·계획 스냅샷 대상이다.
 *
 * <ul>
 *   <li>{@code ACTIVE} — 적립 중인 봉투.
 *   <li>{@code CLOSED} — 일회성 봉투가 지출 후 종료된 상태(ENV-05). 과거 기록 참조용으로 행은 잔존.
 *   <li>{@code DELETED} — soft delete(규칙 5). 물리 삭제는 회원 탈퇴 cascade뿐.
 * </ul>
 */
public enum EnvelopeStatus {
    ACTIVE,
    CLOSED,
    DELETED
}
