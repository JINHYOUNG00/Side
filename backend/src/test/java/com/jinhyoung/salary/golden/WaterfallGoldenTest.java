package com.jinhyoung.salary.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.cycle.domain.WaterfallCalculator;
import com.jinhyoung.salary.cycle.domain.WaterfallGroup;
import com.jinhyoung.salary.cycle.domain.WaterfallLine;
import com.jinhyoung.salary.cycle.domain.WaterfallResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 폭포 골든 테스트 — 검증된 노션 실데이터 fixture(waterfall-cases.json)를 WaterfallCalculator가
 * 정확히 재현하는지 강제한다(FLOW-01). fixture는 절대 수정 금지(깨지면 코드가 틀린 것).
 * 기대값이 정당하게 바뀐 경우에만 소유자가 ./gradlew goldenLock 으로 승인한다.
 *
 * <p>이 테스트는 Jackson에 의존하므로 ..domain.. 밖(golden 패키지)에 둔다 — 도메인 순수성
 * ArchUnit 규칙이 테스트 클래스까지 스캔하기 때문(MaturityGoldenTest와 동일 배치).
 */
class WaterfallGoldenTest {

    private static final Path FIXTURE = Path.of("src/test/resources/golden/waterfall-cases.json");

    @TestFactory
    List<DynamicTest> waterfallMatchesGoldenFixture() throws Exception {
        JsonNode root = new ObjectMapper().readTree(FIXTURE.toFile());
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            String name = c.get("name").asText();
            JsonNode input = c.get("input");
            JsonNode expected = c.get("expected");

            long income = input.get("income").asLong();
            long envelopeContribution = input.get("envelopeContribution").asLong();
            List<WaterfallLine> lines = new ArrayList<>();
            for (JsonNode l : input.get("lines")) {
                lines.add(new WaterfallLine(
                        l.get("budgetItemId").asLong(),
                        Category.valueOf(l.get("category").asText()),
                        l.get("amount").asLong()));
            }

            long expectedRemaining = expected.get("remaining").asLong();
            long expectedEmergency = expected.get("emergencyTotal").asLong();
            JsonNode expectedGroups = expected.get("groups");

            tests.add(DynamicTest.dynamicTest(name, () -> {
                WaterfallResult result = WaterfallCalculator.calculate(income, lines, envelopeContribution);

                assertThat(result.remaining())
                        .as("남는 돈이 골든 기대값과 일치해야 한다: %s", name)
                        .isEqualTo(expectedRemaining);
                assertThat(result.emergencyTotal())
                        .as("EMERGENCY 합계가 골든 기대값과 일치: %s", name)
                        .isEqualTo(expectedEmergency);

                // 그룹: 표시 순서·카테고리·소계가 모두 골든과 일치해야 한다.
                assertThat(result.groups()).as("그룹 개수: %s", name).hasSize(expectedGroups.size());
                for (int i = 0; i < expectedGroups.size(); i++) {
                    WaterfallGroup g = result.groups().get(i);
                    JsonNode eg = expectedGroups.get(i);
                    assertThat(g.category().name())
                            .as("그룹 %d 카테고리·순서: %s", i, name)
                            .isEqualTo(eg.get("category").asText());
                    assertThat(g.subtotal())
                            .as("%s 소계: %s", eg.get("category").asText(), name)
                            .isEqualTo(eg.get("subtotal").asLong());
                }

                // 정의 재확인: remaining = income − Σ(그룹 소계) − envelopeContribution.
                long subtotalSum = result.groups().stream()
                        .mapToLong(WaterfallGroup::subtotal)
                        .sum();
                assertThat(income - subtotalSum - result.envelopeContribution())
                        .as("remaining 정의(income−Σ소계−봉투)와 일치: %s", name)
                        .isEqualTo(result.remaining());
            }));
        }
        assertThat(tests).as("골든 케이스가 비어 있으면 안 된다").isNotEmpty();
        return tests;
    }
}
