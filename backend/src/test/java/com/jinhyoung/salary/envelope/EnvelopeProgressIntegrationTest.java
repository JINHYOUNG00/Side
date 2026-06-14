package com.jinhyoung.salary.envelope;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 봉투 진행률·D-day·월 적립액 조회 노출 통합 테스트(ENV-03). 실 PostgreSQL(Testcontainers)·실 JWT로 검증한다.
 *
 * <p>"오늘"은 KST {@code Clock}을 고정 주입한다(규칙 3). 기준일 2026-06-25 + 사용자 월급일 25·조정 NONE이면
 * 사이클은 매월 25일 시작(25~익월 24)이라, 구현규칙 1장 예시(next_due 2027-01-10 → 남은 사이클 7)가 그대로 성립한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(EnvelopeProgressIntegrationTest.FixedClockConfig.class)
class EnvelopeProgressIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JwtProvider jwtProvider;

    private long aliceId;
    private String aliceToken;
    private long aliceAccountId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from envelopes");
        accountRepository.deleteAll();
        userRepository.deleteAll();

        User alice = userRepository.save(User.createFromOAuth("KAKAO", "alice", "alice@x.com", "alice"));
        // 월급일 25·조정 NONE → 사이클 25~익월 24. 구현규칙 1장 7-사이클 예시 재현.
        alice.updateSettings(2_473_110L, (short) 25, PaydayAdjustment.NONE, null);
        userRepository.save(alice);
        aliceId = alice.getId();
        aliceToken = jwtProvider.issue(aliceId);
        aliceAccountId = accountRepository
                .save(Account.create(aliceId, "비상금통장", null, null, 0))
                .getId();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken);
    }

    private long createEnvelope(long targetAmount, String nextDueDate) throws Exception {
        String body = "{\"accountId\":" + aliceAccountId + ",\"name\":\"자동차세\",\"targetAmount\":" + targetAmount
                + ",\"nextDueDate\":\"" + nextDueDate + "\",\"cycleMonths\":12}";
        String response = mockMvc.perform(authed(post("/api/v1/envelopes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void 조회는_진행률_Dday_월적립액을_포함한다() throws Exception {
        // next_due 2027-01-10, 남은 사이클 7(6/25~7/24 포함 ~ 12/25~1/24). target 700,000 → 월 100,000.
        long id = createEnvelope(700_000, "2027-01-10");

        mockMvc.perform(authed(get("/api/v1/envelopes/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(0)) // saved 0
                .andExpect(jsonPath("$.dDay").value(199)) // 2026-06-25 → 2027-01-10
                .andExpect(jsonPath("$.monthlyAmount").value(100_000));

        // 목록도 동일 파생값을 싣는다.
        mockMvc.perform(authed(get("/api/v1/envelopes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].monthlyAmount").value(100_000))
                .andExpect(jsonPath("$[0].dDay").value(199));
    }

    @Test
    void 남은_사이클이_1이면_월적립액은_잔여_전액이다() throws Exception {
        // next_due 2026-07-05는 오늘 사이클(6/25~7/24) 안 → 남은 사이클 1 → 잔여 전액.
        long id = createEnvelope(700_000, "2026-07-05");

        mockMvc.perform(authed(get("/api/v1/envelopes/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyAmount").value(700_000))
                .andExpect(jsonPath("$.dDay").value(10));
    }

    @Test
    void 적립액이_있으면_진행률과_월적립액에_반영된다() throws Exception {
        long id = createEnvelope(700_000, "2027-01-10"); // 남은 사이클 7
        // saved_amount는 API로 못 바꾸므로(트랜잭션 캐시값) 직접 주입해 조회 파생값을 검증한다.
        jdbcTemplate.update("update envelopes set saved_amount = 350000 where id = ?", id);

        mockMvc.perform(authed(get("/api/v1/envelopes/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(50)) // 350,000 / 700,000
                .andExpect(jsonPath("$.monthlyAmount").value(50_000)); // ceil((700,000−350,000)/7)
    }

    @Test
    void 이미_충족한_봉투는_진행률_100_월적립액_0이다() throws Exception {
        long id = createEnvelope(700_000, "2027-01-10");
        jdbcTemplate.update("update envelopes set saved_amount = 700000 where id = ?", id);

        mockMvc.perform(authed(get("/api/v1/envelopes/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(100))
                .andExpect(jsonPath("$.monthlyAmount").value(0));
    }

    @Test
    void 생성_응답에도_파생값이_담긴다() throws Exception {
        String body = "{\"accountId\":" + aliceAccountId
                + ",\"name\":\"자동차세\",\"targetAmount\":700000,\"nextDueDate\":\"2027-01-10\",\"cycleMonths\":12}";
        mockMvc.perform(authed(post("/api/v1/envelopes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.progressPercent").value(0))
                .andExpect(jsonPath("$.dDay").value(199))
                .andExpect(jsonPath("$.monthlyAmount").value(100_000));
    }
}
