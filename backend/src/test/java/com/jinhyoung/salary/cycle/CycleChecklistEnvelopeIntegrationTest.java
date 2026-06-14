package com.jinhyoung.salary.cycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.cycle.domain.CycleDefinition;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.cycle.infra.PlanLineRepository;
import com.jinhyoung.salary.cycle.infra.PlanLineStatus;
import com.jinhyoung.salary.envelope.infra.Envelope;
import com.jinhyoung.salary.envelope.infra.EnvelopeRepository;
import com.jinhyoung.salary.envelope.infra.EnvelopeTransaction;
import com.jinhyoung.salary.envelope.infra.EnvelopeTransactionRepository;
import com.jinhyoung.salary.envelope.infra.TransactionType;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 체크리스트 ENVELOPE 라인 ↔ 봉투 적립 연동(CYCLE-07, 구현규칙 2장) 통합 테스트. 실 PostgreSQL(Testcontainers) +
 * MockMvc + 실 JWT + 고정 KST Clock(2026-06-25).
 *
 * <p>스냅샷 빌더(owner)는 ITEM·LIVING만 산출하므로 ENVELOPE 라인은 테스트가 직접 plan_lines에 적재해
 * 전이를 실험한다(라인 생성 자체는 CYCLE-03 소관). 검증 대상은 "DONE 경계 전이의 적립 부수효과":
 * DONE 시 DEPOSIT 기록 + saved_amount 증가, 해제 시 회수, 이미 SPEND가 있으면 409, 건너뜀은 미반영,
 * 그리고 적립이 ENV-02 월할 적립액에 자동 반영되는 것.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(CycleChecklistEnvelopeIntegrationTest.FixedClockConfig.class)
class CycleChecklistEnvelopeIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
    private static final long PLANNED = 100_000;
    private static final long TARGET = 500_000;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atStartOfDay(KST).toInstant(), KST);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    CycleRepository cycleRepository;

    @Autowired
    PlanLineRepository planLineRepository;

    @Autowired
    EnvelopeRepository envelopeRepository;

    @Autowired
    EnvelopeTransactionRepository transactionRepository;

    @Autowired
    JdbcTemplate jdbc;

    private long userId;
    private long accountId;
    private long envelopeId;
    private Cycle cycle;
    private long lineId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        planLineRepository.deleteAll();
        cycleRepository.deleteAll();
        envelopeRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        userId = userRepository
                .save(User.createFromOAuth("KAKAO", "owner", "owner@x.com", "owner"))
                .getId();
        accountId = accountRepository
                .save(Account.create(userId, "국민", null, null, 0))
                .getId();
        User user = userRepository.findById(userId).orElseThrow();
        user.updateSettings(2_473_110, (short) 25, PaydayAdjustment.NONE, accountId);
        userRepository.save(user);

        // 반복형 봉투(12개월), 목표 500,000, 적립 0 시작. 다음 지출일은 오늘 이후.
        envelopeId = envelopeRepository
                .save(Envelope.create(userId, accountId, "자동차세", TARGET, LocalDate.of(2026, 12, 10), (short) 12, null))
                .getId();

        // 오늘이 속한 사이클(2026-06-25 ~ 2026-07-24).
        cycle = cycleRepository.save(
                Cycle.create(userId, new CycleDefinition(TODAY, LocalDate.of(2026, 7, 24), "2026-06"), 2_473_110));

        // 스냅샷 빌더가 만들지 않는 ENVELOPE 라인을 직접 적재(라인 생성은 CYCLE-03 소관).
        lineId = insertEnvelopeLine(cycle.getId(), envelopeId, accountId, PLANNED);
    }

    private long insertEnvelopeLine(long cycleId, long envId, long acctId, long planned) {
        return jdbc.queryForObject(
                "insert into plan_lines (cycle_id, line_type, envelope_id, account_id, name_snapshot,"
                        + " category_snapshot, account_name_snapshot, planned_amount, status)"
                        + " values (?, 'ENVELOPE', ?, ?, ?, 'ENVELOPE', ?, ?, 'PENDING') returning id",
                Long.class,
                cycleId,
                envId,
                acctId,
                "자동차세",
                "국민",
                planned);
    }

    private String token(long uid) {
        return "Bearer " + jwtProvider.issue(uid);
    }

    private ResultActions patchLine(long uid, long id, String statusValue) throws Exception {
        return mockMvc.perform(patch("/api/v1/plan-lines/" + id)
                .header(HttpHeaders.AUTHORIZATION, token(uid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + statusValue + "\"}"));
    }

    private long savedAmount() {
        return envelopeRepository.findById(envelopeId).orElseThrow().getSavedAmount();
    }

    private List<EnvelopeTransaction> deposits() {
        return transactionRepository.findAll().stream()
                .filter(t -> t.getType() == TransactionType.DEPOSIT)
                .toList();
    }

    @Test
    void 봉투_라인을_완료하면_적립과_DEPOSIT이_기록된다() throws Exception {
        patchLine(userId, lineId, "DONE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.plannedAmount").value(PLANNED));

        assertThat(savedAmount()).isEqualTo(PLANNED);

        List<EnvelopeTransaction> deposits = deposits();
        assertThat(deposits).hasSize(1);
        EnvelopeTransaction deposit = deposits.get(0);
        assertThat(deposit.getEnvelopeId()).isEqualTo(envelopeId);
        assertThat(deposit.getAmount()).isEqualTo(PLANNED);
        assertThat(deposit.getCycleId()).isEqualTo(cycle.getId());
        assertThat(deposit.getOccurredOn()).isEqualTo(TODAY);
        // 적립 전용 — SPEND 필드는 비어 있다.
        assertThat(deposit.getActualAmount()).isNull();
        assertThat(deposit.getCarryOver()).isNull();
        assertThat(deposit.getShortfallSource()).isNull();
    }

    @Test
    void 완료를_대기로_되돌리면_DEPOSIT이_삭제되고_적립이_회수된다() throws Exception {
        patchLine(userId, lineId, "DONE").andExpect(status().isOk());
        assertThat(savedAmount()).isEqualTo(PLANNED);

        patchLine(userId, lineId, "PENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(savedAmount()).isZero();
        assertThat(deposits()).isEmpty();
        assertThat(planLineRepository.findById(lineId).orElseThrow().getStatus())
                .isEqualTo(PlanLineStatus.PENDING);
    }

    @Test
    void 완료를_건너뜀으로_바꿔도_적립이_회수된다() throws Exception {
        patchLine(userId, lineId, "DONE").andExpect(status().isOk());

        patchLine(userId, lineId, "SKIPPED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));

        assertThat(savedAmount()).isZero();
        assertThat(deposits()).isEmpty();
    }

    @Test
    void 건너뜀_전이는_적립하지_않는다() throws Exception {
        patchLine(userId, lineId, "SKIPPED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));

        assertThat(savedAmount()).isZero();
        assertThat(deposits()).isEmpty();
    }

    @Test
    void 완료를_다시_완료해도_중복_적립되지_않는다() throws Exception {
        patchLine(userId, lineId, "DONE").andExpect(status().isOk());
        patchLine(userId, lineId, "DONE").andExpect(status().isOk());

        assertThat(savedAmount()).isEqualTo(PLANNED);
        assertThat(deposits()).hasSize(1);
    }

    @Test
    void 봉투에_SPEND가_있으면_완료를_해제할_수_없고_409다() throws Exception {
        patchLine(userId, lineId, "DONE").andExpect(status().isOk());
        // 적립 후 지출이 발생한 상태 — 적립이 소비되어 해제할 수 없다(구현규칙 2장).
        transactionRepository.save(
                EnvelopeTransaction.spend(envelopeId, PLANNED, PLANNED, null, null, cycle.getId(), TODAY));

        patchLine(userId, lineId, "PENDING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LINE_LOCKED_BY_SPEND"));

        // 라인·적립·DEPOSIT 모두 그대로다(전이 거부는 부수효과를 남기지 않는다).
        assertThat(planLineRepository.findById(lineId).orElseThrow().getStatus())
                .isEqualTo(PlanLineStatus.DONE);
        assertThat(savedAmount()).isEqualTo(PLANNED);
        assertThat(deposits()).hasSize(1);
    }

    @Test
    void 타인의_봉투_라인은_완료할_수_없고_404다() throws Exception {
        long intruderId = userRepository
                .save(User.createFromOAuth("KAKAO", "intruder", "intruder@x.com", "intruder"))
                .getId();

        patchLine(intruderId, lineId, "DONE")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 적립 부수효과는 소유권 게이트 이후라 일절 발생하지 않는다.
        assertThat(savedAmount()).isZero();
        assertThat(deposits()).isEmpty();
        assertThat(planLineRepository.findById(lineId).orElseThrow().getStatus())
                .isEqualTo(PlanLineStatus.PENDING);
    }

    @Test
    void 적립이_월할_적립액에_자동_반영된다() throws Exception {
        long monthlyBefore = mockMvc.perform(get("/api/v1/envelopes").header(HttpHeaders.AUTHORIZATION, token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].savedAmount").value(0))
                .andExpect(jsonPath("$[0].progressPercent").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .transform(this::firstMonthlyAmount);

        patchLine(userId, lineId, "DONE").andExpect(status().isOk());

        long monthlyAfter = mockMvc.perform(get("/api/v1/envelopes").header(HttpHeaders.AUTHORIZATION, token(userId)))
                .andExpect(status().isOk())
                // 적립이 saved_amount·진행률에 반영된다(100,000 / 500,000 = 20%).
                .andExpect(jsonPath("$[0].savedAmount").value(PLANNED))
                .andExpect(jsonPath("$[0].progressPercent").value(20))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .transform(this::firstMonthlyAmount);

        // 남은 목표(target − saved)가 줄어 월할 적립액도 줄어든다(ENV-02 자동 재계산).
        assertThat(monthlyAfter).isPositive().isLessThan(monthlyBefore);
    }

    /** 응답 배열 첫 봉투의 monthlyAmount만 뽑는다(jsonPath 의존 없이 단순 파싱 — 값 비교용). */
    private long firstMonthlyAmount(String body) {
        int key = body.indexOf("\"monthlyAmount\":");
        int start = key + "\"monthlyAmount\":".length();
        int end = start;
        while (end < body.length() && (Character.isDigit(body.charAt(end)) || body.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(body.substring(start, end));
    }
}
