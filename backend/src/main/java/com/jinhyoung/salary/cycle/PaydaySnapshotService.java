package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 지급일 사이클 스냅샷 트리거(CYCLE-03 후속, 아키텍처 4장 일일 배치의 스냅샷 단계). 고정 일자 크론이 아니라 매일 한 번
 * "오늘이 각 사용자의 실제 지급일인가"를 {@link PaydayService#resolvePaydayMonth}로 판정해(NOTI-01 지급일 알림과 동일
 * 판정 공유), 해당 사용자에 한해 그 달 사이클 스냅샷을 박는다. 기준일(today)은 주입된 KST {@code Clock}으로 산출
 * — {@code LocalDate.now()} 직접 호출 금지(규칙 3).
 *
 * <p>스냅샷 생성·영속화 자체는 이 서비스의 책임이 아니다 — owner 도메인인 {@link CycleSnapshotService#createSnapshot}에
 * 위임한다(계산 0줄). 이 서비스는 "누가·어느 달 사이클인가"만 판정해 호출하는 얇은 일일 오케스트레이션이다.
 *
 * <p><b>멱등</b>(규칙 8): {@code createSnapshot}이 {@code (user_id, cycle_start)} 게이트로 중복 적재를 막으므로,
 * 같은 날 배치가 다시 돌아도 사이클이 중복 생성되지 않는다. 사용자별 {@code createSnapshot}은 각자 독립 트랜잭션이라
 * 이 메서드는 트랜잭션 경계를 두지 않는다(한 사용자 적재가 다른 사용자에 영향 없음).
 */
@Service
public class PaydaySnapshotService {

    private final UserRepository userRepository;
    private final PaydayService paydayService;
    private final CycleSnapshotService cycleSnapshotService;
    private final Clock clock;

    public PaydaySnapshotService(
            UserRepository userRepository,
            PaydayService paydayService,
            CycleSnapshotService cycleSnapshotService,
            Clock clock) {
        this.userRepository = userRepository;
        this.paydayService = paydayService;
        this.cycleSnapshotService = cycleSnapshotService;
        this.clock = clock;
    }

    /**
     * 오늘이 실제 지급일인 사용자의 그 달 사이클 스냅샷을 보장하고(없으면 생성, 있으면 멱등 스킵) 처리한 사용자 수를
     * 반환한다. 온보딩 전(base_income=0, 플레이스홀더 월급일) 사용자는 배분할 항목이 없어 대상에서 제외한다
     * (NOTI-01 알림 대상과 동일 모집단).
     */
    public int createTodaysSnapshots() {
        LocalDate today = LocalDate.now(clock);
        int snapshotted = 0;
        for (User user : userRepository.findByBaseIncomeGreaterThan(0L)) {
            Optional<YearMonth> startMonth =
                    paydayService.resolvePaydayMonth(today, user.getPayday(), user.getPaydayAdjustment());
            if (startMonth.isPresent()) {
                cycleSnapshotService.createSnapshot(user.getId(), startMonth.get());
                snapshotted++;
            }
        }
        return snapshotted;
    }
}
