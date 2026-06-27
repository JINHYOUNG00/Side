package com.jinhyoung.salary.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.checkin.infra.CheckIn;
import com.jinhyoung.salary.checkin.infra.CheckInRepository;
import com.jinhyoung.salary.cycle.domain.CycleDefinition;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 월말 체크인 기록(RPT-01) 통합 테스트. 실 PostgreSQL(Testcontainers)에 사이클을 박은 뒤
 * {@code POST /check-ins}로 생활비 잔액·추가 투입액을 기록하고, 초과액 계산·저장, 사이클당 1건 멱등(409),
 * 소유권 게이트(404), 범위 검증(400)을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CheckInIntegrationTest {

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
    CycleRepository cycleRepository;

    @Autowired
    CheckInRepository checkInRepository;

    @BeforeEach
    void clear() {
        checkInRepository.deleteAll();
        cycleRepository.deleteAll();
        userRepository.deleteAll();
    }

    private long newUser(String key) {
        return userRepository
                .save(User.createFromOAuth("KAKAO", key, key + "@x.com", key))
                .getId();
    }

    /** 사이클 1건을 직접 박는다(체크인은 plan_lines 비의존이라 경계·라벨만 있으면 된다). */
    private long newCycle(long userId) {
        CycleDefinition definition =
                new CycleDefinition(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 7, 24), "2026-06");
        return cycleRepository.save(Cycle.create(userId, definition, 2_473_110)).getId();
    }

    private ResultActions postCheckIn(long userId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/check-ins")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 체크인_기록시_초과액을_계산해_저장한다() throws Exception {
        long userId = newUser("over");
        long cycleId = newCycle(userId);

        // 30,000 충당했는데 10,000만 남음 → overspend = 30,000 − 10,000 = 20,000(초과).
        postCheckIn(
                        userId,
                        "{\"cycleId\":" + cycleId + ",\"livingRemaining\":10000,\"toppedUp\":30000,\"note\":\"성과급달\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cycleId").value(cycleId))
                .andExpect(jsonPath("$.livingRemaining").value(10000))
                .andExpect(jsonPath("$.toppedUp").value(30000))
                .andExpect(jsonPath("$.overspend").value(20000))
                .andExpect(jsonPath("$.note").value("성과급달"));

        CheckIn saved = checkInRepository.findAll().get(0);
        assertThat(saved.getCycleId()).isEqualTo(cycleId);
        assertThat(saved.getOverspend()).isEqualTo(20_000L);
    }

    @Test
    void 투입액을_생략하면_0으로_기록되고_잉여는_음수다() throws Exception {
        long userId = newUser("surplus");
        long cycleId = newCycle(userId);

        // toppedUp 생략 → 0, 41,000 남음 → overspend = 0 − 41,000 = -41,000(잉여).
        postCheckIn(userId, "{\"cycleId\":" + cycleId + ",\"livingRemaining\":41000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.toppedUp").value(0))
                .andExpect(jsonPath("$.overspend").value(-41000));

        assertThat(checkInRepository.findAll().get(0).getToppedUp()).isZero();
    }

    @Test
    void 사이클당_한_건만_허용한다() throws Exception {
        long userId = newUser("dup");
        long cycleId = newCycle(userId);
        postCheckIn(userId, "{\"cycleId\":" + cycleId + ",\"livingRemaining\":10000}")
                .andExpect(status().isCreated());

        // 같은 사이클 재기록은 409 — 사이클당 1건(ERD unique).
        postCheckIn(userId, "{\"cycleId\":" + cycleId + ",\"livingRemaining\":50000}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECK_IN_ALREADY_EXISTS"));

        // 첫 기록만 남고 값도 불변.
        assertThat(checkInRepository.count()).isEqualTo(1);
        assertThat(checkInRepository.findAll().get(0).getLivingRemaining()).isEqualTo(10_000L);
    }

    @Test
    void 타인의_사이클에는_체크인할_수_없다() throws Exception {
        long ownerId = newUser("owner");
        long cycleId = newCycle(ownerId);
        long otherId = newUser("other");

        postCheckIn(otherId, "{\"cycleId\":" + cycleId + ",\"livingRemaining\":10000}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 남의 사이클엔 한 건도 기록되지 않는다(존재 비노출).
        assertThat(checkInRepository.count()).isZero();
    }

    @Test
    void 존재하지_않는_사이클은_404다() throws Exception {
        long userId = newUser("nocycle");

        postCheckIn(userId, "{\"cycleId\":999999,\"livingRemaining\":10000}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(checkInRepository.count()).isZero();
    }

    @Test
    void 음수_잔액은_400이다() throws Exception {
        long userId = newUser("neg");
        long cycleId = newCycle(userId);

        postCheckIn(userId, "{\"cycleId\":" + cycleId + ",\"livingRemaining\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(checkInRepository.count()).isZero();
    }

    @Test
    void 상한을_넘는_잔액은_400이다() throws Exception {
        long userId = newUser("big");
        long cycleId = newCycle(userId);

        // 10억 + 1 → 구현규칙 5장 상한 초과.
        postCheckIn(userId, "{\"cycleId\":" + cycleId + ",\"livingRemaining\":1000000001}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(checkInRepository.count()).isZero();
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        long userId = newUser("noauth");
        long cycleId = newCycle(userId);

        mockMvc.perform(post("/api/v1/check-ins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cycleId\":" + cycleId + ",\"livingRemaining\":10000}"))
                .andExpect(status().isUnauthorized());

        assertThat(checkInRepository.count()).isZero();
    }
}
