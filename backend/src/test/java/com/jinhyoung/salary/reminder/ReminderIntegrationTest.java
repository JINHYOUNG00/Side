package com.jinhyoung.salary.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinhyoung.salary.auth.JwtProvider;
import com.jinhyoung.salary.reminder.infra.Reminder;
import com.jinhyoung.salary.reminder.infra.ReminderRepository;
import com.jinhyoung.salary.reminder.infra.ReminderStatus;
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
 * 사용자 정의 리마인더 CRUD + 소유권·검증·개수 상한·soft delete 통합 테스트(NOTI-06). 실
 * PostgreSQL(Testcontainers)로 Flyway V2(reminders) FK까지 함께 검증한다. 인증은 실제 JWT를 Bearer로 건다.
 *
 * <p>"오늘"은 next_remind_date 검증(≥ 오늘)을 결정론적으로 만들기 위해 KST {@code Clock}을 고정 주입한다(규칙 3).
 * 기준일은 2026-06-25.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(ReminderIntegrationTest.FixedClockConfig.class)
class ReminderIntegrationTest {

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
    ReminderRepository reminderRepository;

    @Autowired
    JwtProvider jwtProvider;

    private long aliceId;
    private long bobId;
    private String aliceToken;

    @BeforeEach
    void setUp() {
        reminderRepository.deleteAll();
        userRepository.deleteAll();
        aliceId = newUser("alice");
        bobId = newUser("bob");
        aliceToken = jwtProvider.issue(aliceId);
    }

    private long newUser(String key) {
        return userRepository
                .save(User.createFromOAuth("KAKAO", key, key + "@x.com", key))
                .getId();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String body(String label, int intervalMonths, String nextRemindDate) {
        return "{\"label\":\"" + label + "\",\"intervalMonths\":" + intervalMonths + ",\"nextRemindDate\":\""
                + nextRemindDate + "\"}";
    }

    private long createReminder(String token, String label, int intervalMonths, String nextRemindDate)
            throws Exception {
        String response = mockMvc.perform(authed(post("/api/v1/reminders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(label, intervalMonths, nextRemindDate)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void 리마인더를_생성하면_필드가_저장되고_status_ACTIVE다() throws Exception {
        String response = mockMvc.perform(authed(post("/api/v1/reminders"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("외화 예수금 점검", 3, "2026-07-01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("외화 예수금 점검"))
                .andExpect(jsonPath("$.intervalMonths").value(3))
                .andExpect(jsonPath("$.nextRemindDate").value("2026-07-01"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(response).get("id").asLong();
        assertThat(reminderRepository.findById(id)).isPresent().get().satisfies(r -> {
            assertThat(r.getStatus()).isEqualTo(ReminderStatus.ACTIVE);
            assertThat(r.getIntervalMonths()).isEqualTo((short) 3);
        });
    }

    @Test
    void 다른_사용자의_리마인더는_목록에_보이지_않는다() throws Exception {
        createReminder(jwtProvider.issue(bobId), "밥의 리마인더", 1, "2026-07-01");

        mockMvc.perform(authed(get("/api/v1/reminders"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 다음_알림일이_과거면_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(authed(post("/api/v1/reminders"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("지난 리마인더", 1, "2026-06-24")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 기준일 당일은 허용(≥ 오늘).
        mockMvc.perform(authed(post("/api/v1/reminders"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("오늘 리마인더", 1, "2026-06-25")))
                .andExpect(status().isCreated());
    }

    @Test
    void 메모가_비면_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(authed(post("/api/v1/reminders"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", 1, "2026-07-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 주기가_0이면_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(authed(post("/api/v1/reminders"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("영개월", 0, "2026-07-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 활성_리마인더가_50개면_추가_생성은_409_REMINDER_LIMIT_EXCEEDED다() throws Exception {
        for (int i = 0; i < 50; i++) {
            createReminder(aliceToken, "리마인더" + i, 1, "2026-07-01");
        }

        mockMvc.perform(authed(post("/api/v1/reminders"), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("쉰한번째", 1, "2026-07-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMINDER_LIMIT_EXCEEDED"));
    }

    @Test
    void 리마인더_수정은_필드를_바꾼다() throws Exception {
        long id = createReminder(aliceToken, "원본", 1, "2026-07-01");

        mockMvc.perform(authed(patch("/api/v1/reminders/{id}", id), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("수정됨", 6, "2026-08-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("수정됨"))
                .andExpect(jsonPath("$.intervalMonths").value(6))
                .andExpect(jsonPath("$.nextRemindDate").value("2026-08-01"));
    }

    @Test
    void 다른_사용자의_리마인더는_수정할_수_없다_NOT_FOUND() throws Exception {
        long bobReminder = createReminder(jwtProvider.issue(bobId), "밥의 리마인더", 1, "2026-07-01");

        mockMvc.perform(authed(patch("/api/v1/reminders/{id}", bobReminder), aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("탈취", 1, "2026-07-01")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(reminderRepository.findById(bobReminder))
                .isPresent()
                .get()
                .extracting(Reminder::getLabel)
                .isEqualTo("밥의 리마인더");
    }

    @Test
    void 리마인더를_삭제하면_204이고_조회에서_제외되며_행은_잔존_status_DELETED다() throws Exception {
        long id = createReminder(aliceToken, "지울 리마인더", 1, "2026-07-01");

        mockMvc.perform(authed(delete("/api/v1/reminders/{id}", id), aliceToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/v1/reminders"), aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(reminderRepository.findById(id))
                .isPresent()
                .get()
                .extracting(Reminder::getStatus)
                .isEqualTo(ReminderStatus.DELETED);
    }

    @Test
    void 토큰_없이_리마인더_목록_접근은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/reminders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
