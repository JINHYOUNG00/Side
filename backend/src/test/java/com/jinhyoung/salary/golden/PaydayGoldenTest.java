package com.jinhyoung.salary.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinhyoung.salary.cycle.domain.PaydayResolver;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 실제 지급일 골든 테스트 — 검증된 달력 fixture(payday-cases.json)를 PaydayResolver가
 * 정확히 재현하는지 강제한다(CYCLE-01/HARNESS-golden). fixture는 절대 수정 금지(깨지면 코드가
 * 틀린 것). 기대값이 정당하게 바뀐 경우에만 소유자가 ./gradlew goldenLock 으로 승인한다.
 *
 * <p>이 테스트는 Jackson에 의존하므로 ..domain.. 밖(golden 패키지)에 둔다 — 도메인 순수성
 * ArchUnit 규칙이 테스트 클래스까지 스캔하기 때문(Waterfall/Maturity GoldenTest와 동일 배치).
 */
class PaydayGoldenTest {

    private static final Path FIXTURE = Path.of("src/test/resources/golden/payday-cases.json");

    @TestFactory
    List<DynamicTest> paydayMatchesGoldenFixture() throws Exception {
        JsonNode root = new ObjectMapper().readTree(FIXTURE.toFile());
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            String name = c.get("name").asText();
            JsonNode input = c.get("input");

            YearMonth month = YearMonth.parse(input.get("month").asText());
            int payday = input.get("payday").asInt();
            PaydayAdjustment adjustment =
                    PaydayAdjustment.valueOf(input.get("adjustment").asText());
            Set<LocalDate> holidays = new HashSet<>();
            for (JsonNode h : input.get("holidays")) {
                holidays.add(LocalDate.parse(h.asText()));
            }
            LocalDate expected = LocalDate.parse(c.get("expected").asText());

            tests.add(DynamicTest.dynamicTest(name, () -> {
                LocalDate actual = PaydayResolver.resolve(month, payday, adjustment, holidays);
                assertThat(actual).as("실제 지급일이 골든 기대값과 일치해야 한다: %s", name).isEqualTo(expected);
            }));
        }
        assertThat(tests).as("골든 케이스가 비어 있으면 안 된다").isNotEmpty();
        return tests;
    }
}
