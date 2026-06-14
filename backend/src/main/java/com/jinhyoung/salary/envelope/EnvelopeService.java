package com.jinhyoung.salary.envelope;

import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.envelope.infra.Envelope;
import com.jinhyoung.salary.envelope.infra.EnvelopeRepository;
import com.jinhyoung.salary.envelope.infra.EnvelopeStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 봉투 CRUD 유스케이스(ENV-01). 모든 변경·조회는 호출 사용자의 소유분에 한정한다 — 소유권 검증을 이 한 곳
 * (+리포지토리 쿼리)으로 모아 컨트롤러가 우회할 수 없게 한다(아키텍처 8장).
 *
 * <p>적립 통장(accountId)도 호출 사용자의 활성 통장이어야 한다 — 타인·삭제된 통장이면 NOT_FOUND로 존재
 * 여부를 노출하지 않는다. 다음 지출일(next_due_date)은 KST 오늘 이후여야 한다(구현규칙 5장) — 판정은 주입된
 * {@code Clock}으로 수행한다(규칙 3, LocalDate.now() 직접 호출 금지).
 */
@Service
public class EnvelopeService {

    /** 활성 봉투 개수 상한(구현규칙 5장). */
    static final long MAX_ACTIVE_ENVELOPES = 50;

    private final EnvelopeRepository envelopeRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    public EnvelopeService(EnvelopeRepository envelopeRepository, AccountRepository accountRepository, Clock clock) {
        this.envelopeRepository = envelopeRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Envelope> list(long userId) {
        return envelopeRepository.findByUserIdAndStatusOrderByIdAsc(userId, EnvelopeStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Envelope get(long userId, long envelopeId) {
        return ownedActiveOrThrow(userId, envelopeId);
    }

    @Transactional
    public Envelope create(
            long userId,
            long accountId,
            String name,
            long targetAmount,
            LocalDate nextDueDate,
            Short cycleMonths,
            String memo) {
        requireOwnedActiveAccount(userId, accountId);
        requireNextDueNotPast(nextDueDate);
        if (envelopeRepository.countByUserIdAndStatus(userId, EnvelopeStatus.ACTIVE) >= MAX_ACTIVE_ENVELOPES) {
            throw new ApiException(ErrorCode.ENVELOPE_LIMIT_EXCEEDED, Map.of("limit", MAX_ACTIVE_ENVELOPES));
        }
        return envelopeRepository.save(
                Envelope.create(userId, accountId, name, targetAmount, nextDueDate, cycleMonths, memo));
    }

    /**
     * 봉투 수정(ENV-01). 호출 사용자의 활성 봉투와 활성 적립 통장만 다룬다(미소유·비활성·부재는 NOT_FOUND로
     * 존재 비노출). 개수는 변하지 않으므로 상한 검사는 없다. saved_amount·status는 갱신하지 않는다.
     */
    @Transactional
    public Envelope update(
            long userId,
            long envelopeId,
            long accountId,
            String name,
            long targetAmount,
            LocalDate nextDueDate,
            Short cycleMonths,
            String memo) {
        Envelope envelope = ownedActiveOrThrow(userId, envelopeId);
        requireOwnedActiveAccount(userId, accountId);
        requireNextDueNotPast(nextDueDate);
        envelope.update(accountId, name, targetAmount, nextDueDate, cycleMonths, memo);
        return envelope;
    }

    /**
     * 봉투 soft delete(ENV-01) — 활성 봉투를 DELETED로 전환한다. 행은 잔존하며 이후 조회(목록·단건)에서
     * 제외된다. 이미 삭제·종료된 봉투나 타인·부재 봉투는 NOT_FOUND(존재 비노출). 과거 스냅샷은 불변(규칙 4).
     */
    @Transactional
    public void delete(long userId, long envelopeId) {
        ownedActiveOrThrow(userId, envelopeId).markDeleted();
    }

    /** 봉투 소유권 + 활성 검증의 단일 관문 — 미소유·비활성·부재는 모두 NOT_FOUND. */
    private Envelope ownedActiveOrThrow(long userId, long envelopeId) {
        return envelopeRepository
                .findByIdAndUserIdAndStatus(envelopeId, userId, EnvelopeStatus.ACTIVE)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "envelope", "id", envelopeId)));
    }

    /** 적립 통장이 호출 사용자의 활성 통장인지 검증 — 아니면 NOT_FOUND(존재 비노출). */
    private void requireOwnedActiveAccount(long userId, long accountId) {
        accountRepository
                .findByIdAndUserIdAndActiveTrue(accountId, userId)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "account", "id", accountId)));
    }

    /** 다음 지출일은 KST 오늘 이후여야 한다(구현규칙 5장). 과거면 400 VALIDATION_FAILED. */
    private void requireNextDueNotPast(LocalDate nextDueDate) {
        if (nextDueDate.isBefore(LocalDate.now(clock))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("field", "nextDueDate"));
        }
    }
}
