package com.jinhyoung.salary.envelope;

import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.cycle.HolidayCalendar;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.envelope.domain.EnvelopeAccrual;
import com.jinhyoung.salary.envelope.domain.EnvelopeProgress;
import com.jinhyoung.salary.envelope.domain.EnvelopeSpend;
import com.jinhyoung.salary.envelope.infra.Envelope;
import com.jinhyoung.salary.envelope.infra.EnvelopeRepository;
import com.jinhyoung.salary.envelope.infra.EnvelopeStatus;
import com.jinhyoung.salary.envelope.infra.EnvelopeTransaction;
import com.jinhyoung.salary.envelope.infra.EnvelopeTransactionRepository;
import com.jinhyoung.salary.envelope.infra.ShortfallSource;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final EnvelopeTransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final CycleRepository cycleRepository;
    private final HolidayCalendar holidayCalendar;
    private final Clock clock;

    public EnvelopeService(
            EnvelopeRepository envelopeRepository,
            EnvelopeTransactionRepository transactionRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            CycleRepository cycleRepository,
            HolidayCalendar holidayCalendar,
            Clock clock) {
        this.envelopeRepository = envelopeRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.cycleRepository = cycleRepository;
        this.holidayCalendar = holidayCalendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EnvelopeView> list(long userId) {
        User user = ownerOrThrow(userId);
        LocalDate today = LocalDate.now(clock);
        Set<LocalDate> holidaysAroundToday = holidayCalendar.holidaysAround(YearMonth.from(today));
        return envelopeRepository.findByUserIdAndStatusOrderByIdAsc(userId, EnvelopeStatus.ACTIVE).stream()
                .map(envelope -> toView(envelope, user, today, holidaysAroundToday))
                .toList();
    }

    @Transactional(readOnly = true)
    public EnvelopeView get(long userId, long envelopeId) {
        User user = ownerOrThrow(userId);
        Envelope envelope = ownedActiveOrThrow(userId, envelopeId);
        LocalDate today = LocalDate.now(clock);
        return toView(envelope, user, today, holidayCalendar.holidaysAround(YearMonth.from(today)));
    }

    @Transactional
    public EnvelopeView create(
            long userId,
            long accountId,
            String name,
            long targetAmount,
            LocalDate nextDueDate,
            Short cycleMonths,
            String memo) {
        User user = ownerOrThrow(userId);
        requireOwnedActiveAccount(userId, accountId);
        requireNextDueNotPast(nextDueDate);
        if (envelopeRepository.countByUserIdAndStatus(userId, EnvelopeStatus.ACTIVE) >= MAX_ACTIVE_ENVELOPES) {
            throw new ApiException(ErrorCode.ENVELOPE_LIMIT_EXCEEDED, Map.of("limit", MAX_ACTIVE_ENVELOPES));
        }
        Envelope envelope = envelopeRepository.save(
                Envelope.create(userId, accountId, name, targetAmount, nextDueDate, cycleMonths, memo));
        LocalDate today = LocalDate.now(clock);
        return toView(envelope, user, today, holidayCalendar.holidaysAround(YearMonth.from(today)));
    }

    /**
     * 봉투 수정(ENV-01). 호출 사용자의 활성 봉투와 활성 적립 통장만 다룬다(미소유·비활성·부재는 NOT_FOUND로
     * 존재 비노출). 개수는 변하지 않으므로 상한 검사는 없다. saved_amount·status는 갱신하지 않는다.
     */
    @Transactional
    public EnvelopeView update(
            long userId,
            long envelopeId,
            long accountId,
            String name,
            long targetAmount,
            LocalDate nextDueDate,
            Short cycleMonths,
            String memo) {
        User user = ownerOrThrow(userId);
        Envelope envelope = ownedActiveOrThrow(userId, envelopeId);
        requireOwnedActiveAccount(userId, accountId);
        requireNextDueNotPast(nextDueDate);
        envelope.update(accountId, name, targetAmount, nextDueDate, cycleMonths, memo);
        LocalDate today = LocalDate.now(clock);
        return toView(envelope, user, today, holidayCalendar.holidaysAround(YearMonth.from(today)));
    }

    /**
     * 봉투 soft delete(ENV-01) — 활성 봉투를 DELETED로 전환한다. 행은 잔존하며 이후 조회(목록·단건)에서
     * 제외된다. 이미 삭제·종료된 봉투나 타인·부재 봉투는 NOT_FOUND(존재 비노출). 과거 스냅샷은 불변(규칙 4).
     */
    @Transactional
    public void delete(long userId, long envelopeId) {
        ownedActiveOrThrow(userId, envelopeId).markDeleted();
    }

    /**
     * 봉투 지출 처리(ENV-04). 실제 지출액을 받아 SPEND 트랜잭션을 남기고 적립액 캐시를 갱신한다. 차액에 따라:
     * 부족(actual &gt; saved)이면 충당 출처(LIVING/EMERGENCY)를, 잉여(actual &lt; saved)면 이월/회수(carryOver)를
     * 함께 기록한다 — 필요한 필드가 없거나 불필요한 필드가 들어오면 VALIDATION_FAILED로 막는다(UI 동선과 1:1).
     *
     * <p>지출 산술(부족·잉여·지출 후 적립액)은 순수 {@link EnvelopeSpend}에 위임한다. 다음 지출일 이동·적립
     * 재시작(반복형)·종료(일회성)는 ENV-05 소관이라 여기선 next_due_date·status를 건드리지 않는다 — 이 호출은
     * saved_amount만 바꾼다. 기록 일자·현재 사이클은 주입 {@code Clock}으로 판정한다(규칙 3).
     */
    @Transactional
    public EnvelopeView spend(
            long userId, long envelopeId, long actualAmount, ShortfallSource shortfallSource, Boolean carryOver) {
        User user = ownerOrThrow(userId);
        Envelope envelope = ownedActiveOrThrow(userId, envelopeId);
        long saved = envelope.getSavedAmount();
        requireConsistentSpendFields(saved, actualAmount, shortfallSource, carryOver);

        LocalDate today = LocalDate.now(clock);
        Long cycleId = cycleRepository
                .findByUserIdAndCycleStartLessThanEqualAndCycleEndGreaterThanEqual(userId, today, today)
                .map(Cycle::getId)
                .orElse(null);
        transactionRepository.save(
                EnvelopeTransaction.spend(envelopeId, saved, actualAmount, shortfallSource, carryOver, cycleId, today));

        envelope.applySpend(EnvelopeSpend.savedAfterSpend(saved, actualAmount, Boolean.TRUE.equals(carryOver)));
        return toView(envelope, user, today, holidayCalendar.holidaysAround(YearMonth.from(today)));
    }

    /**
     * 지출 입력 필드 일관성 검증(ENV-04) — 차액 종류에 맞는 필드만 받는다. 부족이면 {@code shortfallSource} 필수·
     * {@code carryOver} 금지, 잉여면 {@code carryOver} 필수·{@code shortfallSource} 금지, 정확이면 둘 다 금지.
     * 어긋나면 어느 필드가 문제인지 담아 VALIDATION_FAILED(400).
     */
    private void requireConsistentSpendFields(
            long saved, long actualAmount, ShortfallSource shortfallSource, Boolean carryOver) {
        boolean shortfall = EnvelopeSpend.shortfall(saved, actualAmount) > 0;
        boolean surplus = EnvelopeSpend.surplus(saved, actualAmount) > 0;
        if (shortfall) {
            require(shortfallSource != null, "shortfallSource");
            require(carryOver == null, "carryOver");
        } else if (surplus) {
            require(carryOver != null, "carryOver");
            require(shortfallSource == null, "shortfallSource");
        } else { // 정확히 일치 — 충당도 이월도 없다
            require(shortfallSource == null, "shortfallSource");
            require(carryOver == null, "carryOver");
        }
    }

    private void require(boolean condition, String field) {
        if (!condition) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("field", field));
        }
    }

    /**
     * 봉투 한 건의 조회 표시값을 조립한다(ENV-03) — 진행률(saved/target)·D-day(next_due−오늘)·월 적립액. 진행률·D-day는
     * 순수 {@link EnvelopeProgress}, 월 적립액은 순수 {@link EnvelopeAccrual}(ENV-02)에 위임한다(계산 0줄). 오늘은
     * 주입 {@code Clock}으로 산출한다(규칙 3).
     */
    private EnvelopeView toView(Envelope envelope, User user, LocalDate today, Set<LocalDate> holidaysAroundToday) {
        int progressPercent = EnvelopeProgress.progressPercent(envelope.getSavedAmount(), envelope.getTargetAmount());
        long dDay = EnvelopeProgress.dDay(today, envelope.getNextDueDate());
        long monthlyAmount = monthlyAmount(envelope, user, today, holidaysAroundToday);
        return new EnvelopeView(envelope, progressPercent, dDay, monthlyAmount);
    }

    /**
     * 이번 사이클의 월 적립액 = {@code ceil((target − saved) ÷ 남은 사이클 수)}(ENV-02, 구현규칙 1장). 남은 사이클 수는
     * owner의 {@link EnvelopeAccrual}이 사용자 월급일·조정 규칙과 공휴일로 계산한다 — 오늘이 속한 사이클과 지출일이
     * 속한 사이클만 풀면 되므로(그 사이 모든 경계가 아니라), 두 시점 주변({@link HolidayCalendar#holidaysAround})의
     * 공휴일 합집합만 주입한다.
     */
    private long monthlyAmount(Envelope envelope, User user, LocalDate today, Set<LocalDate> holidaysAroundToday) {
        Set<LocalDate> holidays = new HashSet<>(holidaysAroundToday);
        holidays.addAll(holidayCalendar.holidaysAround(YearMonth.from(envelope.getNextDueDate())));
        int remainingCycles = EnvelopeAccrual.remainingCycles(
                today, envelope.getNextDueDate(), user.getPayday(), user.getPaydayAdjustment(), holidays);
        return EnvelopeAccrual.monthlyAmount(envelope.getTargetAmount(), envelope.getSavedAmount(), remainingCycles);
    }

    /** 호출 사용자(월급일·조정 규칙 출처) 조회. 정상 인증 경로에선 항상 존재하지만 방어적으로 NOT_FOUND. */
    private User ownerOrThrow(long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "user", "id", userId)));
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

    /**
     * 봉투 + 조회 표시값(ENV-03) 묶음. 엔티티의 영속 상태는 그대로 두고, 진행률·D-day·월 적립액은 조회 시점에
     * 계산한 파생값이라 컬럼으로 저장하지 않는다(구현규칙 1장). 컨트롤러가 응답 DTO로 매핑한다.
     */
    public record EnvelopeView(Envelope envelope, int progressPercent, long dDay, long monthlyAmount) {}
}
