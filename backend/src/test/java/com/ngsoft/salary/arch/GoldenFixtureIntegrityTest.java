package com.ngsoft.salary.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 골든 fixture 무결성 — "fixture는 절대 수정 금지" 규칙의 기계 강제.
 * 훅이나 권한 deny가 아니라 verify 안의 테스트라서, 어떤 경로(에디터·스크립트·
 * 에이전트)로 fixture가 바뀌어도 100% 잡힌다.
 *
 * 기대값 자체가 정당하게 바뀐 경우에만: ./gradlew goldenLock 으로 manifest를
 * 재생성하고, 커밋 메시지에 이유를 명시한다.
 */
class GoldenFixtureIntegrityTest {

    private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden");
    private static final Path MANIFEST = Path.of("src/test/resources/golden.sha256");

    @Test
    void goldenFixturesAreUnchanged() throws Exception {
        assertTrue(Files.exists(MANIFEST), "golden.sha256 manifest가 없음 — fixture 작성 후 ./gradlew goldenLock 으로 최초 생성");

        Map<String, String> expected = new TreeMap<>();
        for (String line : Files.readAllLines(MANIFEST)) {
            if (line.isBlank()) continue;
            // 형식: "<sha256(64자)>  <상대경로>"
            expected.put(line.substring(66), line.substring(0, 64));
        }

        Map<String, String> actual = new TreeMap<>();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (Stream<Path> files = Files.walk(GOLDEN_DIR)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                md.reset();
                byte[] hash = md.digest(Files.readAllBytes(f));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) sb.append(String.format("%02x", b));
                actual.put(GOLDEN_DIR.relativize(f).toString().replace('\\', '/'), sb.toString());
            }
        }

        assertEquals(
                expected,
                actual,
                "골든 fixture가 변경되었다! 골든이 깨지면 코드가 틀린 것 — fixture를 되돌릴 것. "
                        + "기대값이 정당하게 바뀐 경우에만 ./gradlew goldenLock 으로 의도적 승인.");
    }
}
