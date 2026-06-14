package com.jinhyoung.salary.envelope.infra;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 봉투 트랜잭션 저장(ENV-04). 소유권은 봉투를 통해 검증하므로(EnvelopeService) 여기서는 봉투 id로만 다룬다.
 * 적립(DEPOSIT) 연동·트랜잭션 조회는 CYCLE-07·ENV-06에서 확장한다.
 */
public interface EnvelopeTransactionRepository extends JpaRepository<EnvelopeTransaction, Long> {}
