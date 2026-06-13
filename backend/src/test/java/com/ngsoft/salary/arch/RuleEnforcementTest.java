package com.ngsoft.salary.arch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 규칙 음성 테스트 — 위반을 "넣으면" verify가 실제로 잡는지 증명한다.
 * (HARNESS-archunit verify 기준: 위반 코드를 넣으면 verify가 실패함을 테스트로 확인)
 *
 * 위반 픽스처는 com.ngsoft.salary 밖의 archfixtures 패키지에 있어 전역
 * ArchitectureTest 스캔에는 안 잡히고, 여기서만 명시적으로 임포트해 평가한다.
 */
class RuleEnforcementTest {

    private static final JavaClasses VIOLATIONS = new ClassFileImporter().importPackages("archfixtures");

    /** 규칙 2 — double 필드를 가진 클래스에서 double/float 금지 규칙이 실패해야 한다. */
    @Test
    void doubleFloatRuleCatchesViolation() {
        assertThatThrownBy(() -> ArchitectureTest.noDoubleOrFloatFields.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("규칙 2");
    }

    /** 규칙 9 — domain 패키지가 스프링에 의존하면 순수성 규칙이 실패해야 한다. */
    @Test
    void domainPurityRuleCatchesViolation() {
        assertThatThrownBy(() -> ArchitectureTest.domainIsFrameworkFree.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("규칙 9");
    }

    /**
     * 규칙 3 — 배포된 checkstyle 정규식이 인자 없는 now()는 잡고 now(clock)는 통과시키는지,
     * 실제 config 파일에서 패턴을 읽어 증명한다.
     */
    @Test
    void checkstyleRegexCatchesNowWithoutClock() throws Exception {
        Pattern p = Pattern.compile(loadNowBanRegex());
        // 시간 클래스명을 런타임에 합성한다 — 이 테스트 소스 자체에 <클래스>.now() 리터럴을
        // 두면 checkstyle now() 규칙에 걸리므로(규칙이 작동한다는 방증) 문자열로 조립한다.
        String call = ".now()";
        String injected = ".now(clock)";
        for (String type : new String[] {"LocalDate", "LocalDateTime", "Instant", "ZonedDateTime"}) {
            assertThat(p.matcher(type + call).find())
                    .as("인자 없는 호출은 위반으로 잡혀야 한다: %s", type)
                    .isTrue();
            assertThat(p.matcher(type + injected).find())
                    .as("Clock 주입형은 통과해야 한다: %s", type)
                    .isFalse();
        }
        // 빈 인자에 공백이 섞여도 위반.
        assertThat(p.matcher("ZonedDateTime" + ".now(  )").find()).isTrue();
    }

    private static String loadNowBanRegex() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // 오프라인에서도 동작하도록 외부 DTD(checkstyle.org) 로드 비활성화.
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc = factory.newDocumentBuilder()
                .parse(Path.of("config/checkstyle/checkstyle.xml").toFile());
        NodeList props = doc.getElementsByTagName("property");
        for (int i = 0; i < props.getLength(); i++) {
            NamedNodeMap attrs = props.item(i).getAttributes();
            Node name = attrs.getNamedItem("name");
            if (name != null && "format".equals(name.getNodeValue())) {
                return attrs.getNamedItem("value").getNodeValue();
            }
        }
        throw new IllegalStateException("checkstyle.xml에 now() 금지 format 정규식이 없음");
    }
}
