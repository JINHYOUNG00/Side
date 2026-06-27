package com.jinhyoung.salary.envelope.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 봉투 트랜잭션 저장(ENV-04). 소유권은 봉투를 통해 검증하므로(EnvelopeService) 여기서는 봉투 id로만 다룬다.
 * 적립(DEPOSIT) 연동·트랜잭션 조회는 CYCLE-07·ENV-06에서 확장한다.
 */
public interface EnvelopeTransactionRepository extends JpaRepository<EnvelopeTransaction, Long> {

    /** 그 봉투에 지정 종류(SPEND 등)의 기록이 하나라도 있는지 — DONE 해제 잠금 판정에 쓴다(구현규칙 2장). */
    boolean existsByEnvelopeIdAndType(Long envelopeId, TransactionType type);

    /** 특정 사이클의 적립(DEPOSIT) 기록 삭제 — 라인 DONE 해제 시 그 사이클의 적립을 회수한다(CYCLE-07). */
    void deleteByEnvelopeIdAndCycleIdAndType(Long envelopeId, Long cycleId, TransactionType type);

    /**
     * 봉투 집행(지출) 합계(RPT-02 요약) — 사용자가 소유한 봉투의 SPEND 실제 지출액(actual_amount) 총합. 트랜잭션엔
     * user_id가 없어 envelope를 거쳐 소유권을 건다. 지출이 없으면 0(coalesce).
     */
    @Query("select coalesce(sum(t.actualAmount), 0) from EnvelopeTransaction t, Envelope e"
            + " where t.envelopeId = e.id and e.userId = :userId"
            + " and t.type = com.jinhyoung.salary.envelope.infra.TransactionType.SPEND")
    long sumSpentActualByUserId(Long userId);
}
