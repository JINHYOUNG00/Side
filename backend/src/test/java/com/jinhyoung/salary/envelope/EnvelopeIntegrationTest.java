package com.jinhyoung.salary.envelope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.envelope.infra.Envelope;
import com.jinhyoung.salary.envelope.infra.EnvelopeRepository;
import com.jinhyoung.salary.envelope.infra.EnvelopeStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 봉투 CRUD + 소유권·검증·개수 상한·soft delete 통합 테스트(ENV-01). 실 PostgreSQL(Testcontainers)로 Flyway
 * V1(envelopes·accounts·users FK 포함)까지 함께 검증한다. 인증은 실제 JWT를 Bearer로 건다.
 *
 * <p>"오늘"은 next_due_date 검증(구현규칙 5장 next_due ≥ 오늘)을 결정론적으로 만들기 위해 KST {@code Clock}을
 * 고정 주입한다(규칙 3). 기준일은 2026-06-25.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(EnvelopeIntegrationTest.FixedClockConfig.class)
class EnvelopeIntegrationTest {

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
    EnvelopeRepository envelopeRepository;

    @Autowired
    JwtProvider jwtProvider;

    private long aliceId;
    private long bobId;
    private String aliceToken;
    private long aliceAccountId;

    @BeforeEach
    void setUp() {
        envelopeRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        aliceId = newUser("alice");
        bobId = newUser("bob");
        aliceToken = jwtProvider.issue(aliceId);
        aliceAccountId = newAccount(aliceId, "비상금통장");
    }

    private long newUser(String key) {
        return userRepository
                .save(User.createFromOAuth("KAKAO", key, key + "@x.com", key))
                .getId();
    }

    private long newAccount(long userId, String name) {
        return accountRepository
                .save(Account.create(userId, name, null, null, 0))
                .getId();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /** 반복형 봉투 본문(cycleMonths 포함). */
    private String body(String name, long targetAmount, long accountId, String nextDueDate, Integer cycleMonths) {
        String months = cycleMonths == null ? "null" : cycleMonths.toString();
        return "{\"accountId\":" + accountId + ",\"name\":\"" + name + "\",\"targetAmount\":" + targetAmount
                + ",\"nextDueDate\":\"" + nextDueDate + "\",\"cycleMonths\":" + months + "}";
    }

    private long createEnvelope(String token, String name, long targetAmount, long accountId, String nextDueDate)
            throws Exception {
        String response = mockMvc.perform(authed(post("/api/v1/envelopes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, targetAmount, accountId, nextDueDate, 12)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void 봉투를_생성하면_필드가_저장되고_savedAmount_0_status_ACTIVE다() throws Exception {
        String response = mockMvc.perform(authed(post("/api/v1/envelopes"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("자동차세", 600000, aliceAccountId, "2027-01-10", 12)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("자동차세"))
                .andExpect(jsonPath("$.targetAmount").value(600000))
                .andExpect(jsonPath("$.savedAmount").value(0))
                .andExpect(jsonPath("$.accountId").value(aliceAccountId))
                .andExpect(jsonPath("$.nextDueDate").value("2027-01-10"))
                .andExpect(jsonPath("$.cycleMonths").value(12))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(response).get("id").asLong();
        assertThat(envelopeRepository.findById(id)).isPresent().get().satisfies(e -> {
            assertThat(e.getStatus()).isEqualTo(EnvelopeStatus.ACTIVE);
            assertThat(e.getSavedAmount()).isZero();
            assertThat(e.getCycleMonths()).isEqualTo((short) 12);
        });
    }

    @Test
    void 일회성_봉투는_cycleMonths_null로_생성된다() throws Exception {
        mockMvc.perform(authed(post("/api/v1/envelopes"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("결혼축의금", 300000, aliceAccountId, "2026-09-01", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cycleMonths").isEmpty());
    }

    @Test
    void 단건_조회는_소유한_활성_봉투를_돌려준다() throws Exception {
        long id = createEnvelope(aliceToken, "명절비", 500000, aliceAccountId, "2026-09-15");

        mockMvc.perform(authed(get("/api/v1/envelopes/{id}", id), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("명절비"));
    }

    @Test
    void 다른_사용자의_봉투는_목록에_보이지_않는다() throws Exception {
        long bobAccount = newAccount(bobId, "밥통장");
        createEnvelope(jwtProvider.issue(bobId), "밥의봉투", 100000, bobAccount, "2026-12-01");

        mockMvc.perform(authed(get("/api/v1/envelopes"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 다른_사용자의_봉투는_단건_조회할_수_없다_NOT_FOUND() throws Exception {
        long bobAccount = newAccount(bobId, "밥통장");
        long bobEnvelope = createEnvelope(jwtProvider.issue(bobId), "밥의봉투", 100000, bobAccount, "2026-12-01");

        mockMvc.perform(authed(get("/api/v1/envelopes/{id}", bobEnvelope), aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 타인의_통장을_적립통장으로_봉투를_만들_수_없다_NOT_FOUND() throws Exception {
        long bobAccount = newAccount(bobId, "밥통장");

        mockMvc.perform(authed(post("/api/v1/envelopes"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("탈취봉투", 100000, bobAccount, "2026-12-01", 6)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(envelopeRepository.findByUserIdAndStatusOrderByIdAsc(aliceId, EnvelopeStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void 없는_통장을_적립통장으로_하면_NOT_FOUND() throws Exception {
        mockMvc.perform(authed(post("/api/v1/envelopes"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("유령봉투", 100000, 999999, "2026-12-01", 6)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 다음_지출일이_과거면_VALIDATION_FAILED() throws Exception {
        // 기준일 2026-06-25보다 이전.
        mockMvc.perform(authed(post("/api/v1/envelopes"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("지난봉투", 100000, aliceAccountId, "2026-06-24", 6)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 기준일 당일은 허용(≥ 오늘).
        mockMvc.perform(authed(post("/api/v1/envelopes"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("오늘봉투", 100000, aliceAccountId, "2026-06-25", 6)))
                .andExpect(status().isCreated());
    }

    @Test
    void 목표액이_0이면_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(authed(post("/api/v1/envelopes"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("공짜", 0, aliceAccountId, "2026-12-01", 6)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 목표액이_10억을_넘으면_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(authed(post("/api/v1/envelopes"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("거액", 1_000_000_001L, aliceAccountId, "2026-12-01", 6)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 이름이_비면_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(authed(post("/api/v1/envelopes"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", 100000, aliceAccountId, "2026-12-01", 6)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void cycleMonths가_0이면_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(authed(post("/api/v1/envelopes"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("영개월", 100000, aliceAccountId, "2026-12-01", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 활성_봉투가_50개면_추가_생성은_409_ENVELOPE_LIMIT_EXCEEDED다() throws Exception {
        for (int i = 0; i < 50; i++) {
            createEnvelope(aliceToken, "봉투" + i, 10000, aliceAccountId, "2026-12-01");
        }

        mockMvc.perform(authed(post("/api/v1/envelopes"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("쉰한번째", 10000, aliceAccountId, "2026-12-01", 6)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENVELOPE_LIMIT_EXCEEDED"));
    }

    @Test
    void 봉투_수정은_필드를_바꾸고_savedAmount_status는_불변이다() throws Exception {
        long id = createEnvelope(aliceToken, "자동차세", 600000, aliceAccountId, "2027-01-10");

        mockMvc.perform(authed(patch("/api/v1/envelopes/{id}", id), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("자동차세(수정)", 700000, aliceAccountId, "2027-02-10", 6)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("자동차세(수정)"))
                .andExpect(jsonPath("$.targetAmount").value(700000))
                .andExpect(jsonPath("$.nextDueDate").value("2027-02-10"))
                .andExpect(jsonPath("$.cycleMonths").value(6))
                .andExpect(jsonPath("$.savedAmount").value(0));

        assertThat(envelopeRepository.findById(id))
                .isPresent()
                .get()
                .extracting(Envelope::getStatus)
                .isEqualTo(EnvelopeStatus.ACTIVE);
    }

    @Test
    void 다른_사용자의_봉투는_수정할_수_없다_NOT_FOUND() throws Exception {
        long bobAccount = newAccount(bobId, "밥통장");
        long bobEnvelope = createEnvelope(jwtProvider.issue(bobId), "밥의봉투", 100000, bobAccount, "2026-12-01");

        mockMvc.perform(authed(patch("/api/v1/envelopes/{id}", bobEnvelope), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("탈취", 200000, aliceAccountId, "2026-12-01", 6)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(envelopeRepository.findById(bobEnvelope))
                .isPresent()
                .get()
                .extracting(Envelope::getName)
                .isEqualTo("밥의봉투");
    }

    @Test
    void 봉투를_삭제하면_204이고_조회에서_제외되며_행은_잔존_status_DELETED다() throws Exception {
        long id = createEnvelope(aliceToken, "지울봉투", 100000, aliceAccountId, "2026-12-01");

        mockMvc.perform(authed(delete("/api/v1/envelopes/{id}", id), aliceToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/v1/envelopes"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(authed(get("/api/v1/envelopes/{id}", id), aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 물리 삭제가 아니라 행은 남고 status=DELETED (과거 스냅샷 참조 보존, 규칙 4·5).
        assertThat(envelopeRepository.findById(id))
                .isPresent()
                .get()
                .extracting(Envelope::getStatus)
                .isEqualTo(EnvelopeStatus.DELETED);
    }

    @Test
    void 다른_사용자의_봉투는_삭제할_수_없다_NOT_FOUND_원본_불변() throws Exception {
        long bobAccount = newAccount(bobId, "밥통장");
        long bobEnvelope = createEnvelope(jwtProvider.issue(bobId), "밥의봉투", 100000, bobAccount, "2026-12-01");

        mockMvc.perform(authed(delete("/api/v1/envelopes/{id}", bobEnvelope), aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(envelopeRepository.findById(bobEnvelope))
                .isPresent()
                .get()
                .extracting(Envelope::getStatus)
                .isEqualTo(EnvelopeStatus.ACTIVE);
    }

    @Test
    void 삭제하면_활성_상한_자리가_빈다() throws Exception {
        for (int i = 0; i < 50; i++) {
            createEnvelope(aliceToken, "봉투" + i, 10000, aliceAccountId, "2026-12-01");
        }
        long first = envelopeRepository
                .findByUserIdAndStatusOrderByIdAsc(aliceId, EnvelopeStatus.ACTIVE)
                .get(0)
                .getId();

        mockMvc.perform(authed(delete("/api/v1/envelopes/{id}", first), aliceToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(post("/api/v1/envelopes"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("빈자리채움", 10000, aliceAccountId, "2026-12-01", 6)))
                .andExpect(status().isCreated());
    }

    @Test
    void 토큰_없이_봉투_목록_접근은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/envelopes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
