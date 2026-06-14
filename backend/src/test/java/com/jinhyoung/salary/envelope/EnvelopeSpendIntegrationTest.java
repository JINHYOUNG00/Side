package com.jinhyoung.salary.envelope;

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
import java.util.Map;
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
 * 봉투 지출 처리 통합 테스트(ENV-04). 실 PostgreSQL(Testcontainers)·실 JWT·고정 KST Clock으로 검증한다.
 *
 * <p>적립액(saved_amount)은 API로 못 바꾸는 트랜잭션 캐시값이라 {@code jdbcTemplate}로 직접 세팅한 뒤 지출
 * 동선을 태운다. 지출 후 saved_amount 갱신과 SPEND 트랜잭션 기록을 확인하고, 다음 지출일·상태가 불변임도
 * 가드한다(주기 갱신·종료는 ENV-05 소관).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(EnvelopeSpendIntegrationTest.FixedClockConfig.class)
class EnvelopeSpendIntegrationTest {

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
        jdbcTemplate.update("delete from envelope_transactions");
        jdbcTemplate.update("delete from plan_lines");
        jdbcTemplate.update("delete from cycles");
        jdbcTemplate.update("delete from envelopes");
        accountRepository.deleteAll();
        userRepository.deleteAll();

        User alice = userRepository.save(User.createFromOAuth("KAKAO", "alice", "alice@x.com", "alice"));
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

    /** 적립액 saved를 가진 활성 봉투를 만든다(next_due 미래, 반복형 12개월). */
    private long envelopeWithSaved(long target, long saved) throws Exception {
        String body = "{\"accountId\":" + aliceAccountId + ",\"name\":\"자동차세\",\"targetAmount\":" + target
                + ",\"nextDueDate\":\"2027-01-10\",\"cycleMonths\":12}";
        String response = mockMvc.perform(authed(post("/api/v1/envelopes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();
        jdbcTemplate.update("update envelopes set saved_amount = ? where id = ?", saved, id);
        return id;
    }

    private MockHttpServletRequestBuilder spend(long id, String jsonBody) {
        return authed(post("/api/v1/envelopes/{id}/spend", id))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody);
    }

    private int txCount(long envelopeId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from envelope_transactions where envelope_id = ?", Integer.class, envelopeId);
    }

    private long savedAmount(long envelopeId) {
        return jdbcTemplate.queryForObject("select saved_amount from envelopes where id = ?", Long.class, envelopeId);
    }

    @Test
    void 잉여_이월이면_남는_적립액만큼_봉투에_남고_SPEND가_기록된다() throws Exception {
        long id = envelopeWithSaved(700_000, 500_000);

        mockMvc.perform(spend(id, "{\"actualAmount\":300000,\"carryOver\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedAmount").value(200_000)); // 500,000 − 300,000 이월

        Map<String, Object> tx = jdbcTemplate.queryForMap(
                "select type, amount, actual_amount, shortfall_source, carry_over, occurred_on"
                        + " from envelope_transactions where envelope_id = ?",
                id);
        org.assertj.core.api.Assertions.assertThat(tx)
                .containsEntry("type", "SPEND")
                .containsEntry("amount", 500_000L) // 지출 시점 적립액(계획 금액)
                .containsEntry("actual_amount", 300_000L)
                .containsEntry("shortfall_source", null)
                .containsEntry("carry_over", true);
        org.assertj.core.api.Assertions.assertThat(tx.get("occurred_on")).hasToString("2026-06-25");
    }

    @Test
    void 잉여_회수면_봉투가_비워진다() throws Exception {
        long id = envelopeWithSaved(700_000, 500_000);

        mockMvc.perform(spend(id, "{\"actualAmount\":300000,\"carryOver\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedAmount").value(0));

        org.assertj.core.api.Assertions.assertThat(savedAmount(id)).isZero();
    }

    @Test
    void 정확히_지출하면_봉투가_비워지고_부가필드는_null이다() throws Exception {
        long id = envelopeWithSaved(700_000, 300_000);

        mockMvc.perform(spend(id, "{\"actualAmount\":300000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedAmount").value(0));

        Map<String, Object> tx = jdbcTemplate.queryForMap(
                "select shortfall_source, carry_over from envelope_transactions where envelope_id = ?", id);
        org.assertj.core.api.Assertions.assertThat(tx)
                .containsEntry("shortfall_source", null)
                .containsEntry("carry_over", null);
    }

    @Test
    void 부족_지출은_충당_출처를_기록하고_봉투를_비운다() throws Exception {
        long id = envelopeWithSaved(700_000, 200_000);

        mockMvc.perform(spend(id, "{\"actualAmount\":300000,\"shortfallSource\":\"LIVING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedAmount").value(0));

        Map<String, Object> tx = jdbcTemplate.queryForMap(
                "select amount, actual_amount, shortfall_source, carry_over"
                        + " from envelope_transactions where envelope_id = ?",
                id);
        org.assertj.core.api.Assertions.assertThat(tx)
                .containsEntry("amount", 200_000L)
                .containsEntry("actual_amount", 300_000L)
                .containsEntry("shortfall_source", "LIVING")
                .containsEntry("carry_over", null);
    }

    @Test
    void 부족_충당은_EMERGENCY도_기록한다() throws Exception {
        long id = envelopeWithSaved(700_000, 0);

        mockMvc.perform(spend(id, "{\"actualAmount\":150000,\"shortfallSource\":\"EMERGENCY\"}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                        "select shortfall_source from envelope_transactions where envelope_id = ?", String.class, id))
                .isEqualTo("EMERGENCY");
    }

    @Test
    void 현재_사이클이_있으면_지출에_cycle_id가_기록된다() throws Exception {
        long id = envelopeWithSaved(700_000, 500_000);
        // 오늘(2026-06-25)이 속한 사이클을 직접 박아 cycle_id 연결을 검증한다.
        jdbcTemplate.update(
                "insert into cycles (user_id, cycle_start, cycle_end, label, income, income_confirmed)"
                        + " values (?, ?, ?, ?, ?, false)",
                aliceId,
                LocalDate.of(2026, 6, 25),
                LocalDate.of(2026, 7, 24),
                "2026-06",
                2_473_110L);
        Long cycleId = jdbcTemplate.queryForObject("select id from cycles where user_id = ?", Long.class, aliceId);

        mockMvc.perform(spend(id, "{\"actualAmount\":300000,\"carryOver\":true}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                        "select cycle_id from envelope_transactions where envelope_id = ?", Long.class, id))
                .isEqualTo(cycleId);
    }

    @Test
    void 현재_사이클이_없으면_cycle_id는_null이다() throws Exception {
        long id = envelopeWithSaved(700_000, 500_000);

        mockMvc.perform(spend(id, "{\"actualAmount\":300000,\"carryOver\":true}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                        "select cycle_id from envelope_transactions where envelope_id = ?", Long.class, id))
                .isNull();
    }

    @Test
    void 지출은_다음_지출일과_상태를_바꾸지_않는다() throws Exception {
        long id = envelopeWithSaved(700_000, 500_000);

        mockMvc.perform(spend(id, "{\"actualAmount\":300000,\"carryOver\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextDueDate").value("2027-01-10")); // 주기 갱신은 ENV-05

        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject("select status from envelopes where id = ?", String.class, id))
                .isEqualTo("ACTIVE");
    }

    @Test
    void 부족인데_충당_출처가_없으면_400이고_기록도_적립액도_불변이다() throws Exception {
        long id = envelopeWithSaved(700_000, 200_000);

        mockMvc.perform(spend(id, "{\"actualAmount\":300000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        org.assertj.core.api.Assertions.assertThat(txCount(id)).isZero();
        org.assertj.core.api.Assertions.assertThat(savedAmount(id)).isEqualTo(200_000);
    }

    @Test
    void 부족인데_carryOver가_들어오면_400이다() throws Exception {
        long id = envelopeWithSaved(700_000, 200_000);

        mockMvc.perform(spend(id, "{\"actualAmount\":300000,\"shortfallSource\":\"LIVING\",\"carryOver\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        org.assertj.core.api.Assertions.assertThat(txCount(id)).isZero();
    }

    @Test
    void 잉여인데_이월_여부가_없으면_400이다() throws Exception {
        long id = envelopeWithSaved(700_000, 500_000);

        mockMvc.perform(spend(id, "{\"actualAmount\":300000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        org.assertj.core.api.Assertions.assertThat(txCount(id)).isZero();
    }

    @Test
    void 잉여인데_충당_출처가_들어오면_400이다() throws Exception {
        long id = envelopeWithSaved(700_000, 500_000);

        mockMvc.perform(spend(id, "{\"actualAmount\":300000,\"carryOver\":false,\"shortfallSource\":\"LIVING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        org.assertj.core.api.Assertions.assertThat(txCount(id)).isZero();
    }

    @Test
    void 실지출_0이나_10억_초과는_400이다() throws Exception {
        long id = envelopeWithSaved(700_000, 500_000);

        mockMvc.perform(spend(id, "{\"actualAmount\":0,\"carryOver\":false}")).andExpect(status().isBadRequest());
        mockMvc.perform(spend(id, "{\"actualAmount\":1000000001,\"shortfallSource\":\"LIVING\"}"))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(txCount(id)).isZero();
    }

    @Test
    void 잘못된_충당_출처_값은_400이다() throws Exception {
        long id = envelopeWithSaved(700_000, 200_000);

        mockMvc.perform(spend(id, "{\"actualAmount\":300000,\"shortfallSource\":\"SAVINGS\"}"))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(txCount(id)).isZero();
    }

    @Test
    void 타인_봉투_지출은_404이고_원본은_불변이다() throws Exception {
        User bob = userRepository.save(User.createFromOAuth("GOOGLE", "bob", "bob@x.com", "bob"));
        bob.updateSettings(3_000_000L, (short) 25, PaydayAdjustment.NONE, null);
        userRepository.save(bob);
        long bobAccountId = accountRepository
                .save(Account.create(bob.getId(), "밥통장", null, null, 0))
                .getId();
        Long bobEnvelopeId = jdbcTemplate.queryForObject(
                "insert into envelopes (user_id, account_id, name, target_amount, saved_amount, next_due_date,"
                        + " cycle_months, status) values (?, ?, '밥봉투', 700000, 500000, '2027-01-10', 12, 'ACTIVE')"
                        + " returning id",
                Long.class,
                bob.getId(),
                bobAccountId);

        mockMvc.perform(spend(bobEnvelopeId, "{\"actualAmount\":300000,\"carryOver\":true}"))
                .andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(txCount(bobEnvelopeId)).isZero();
        org.assertj.core.api.Assertions.assertThat(savedAmount(bobEnvelopeId)).isEqualTo(500_000);
    }
}
