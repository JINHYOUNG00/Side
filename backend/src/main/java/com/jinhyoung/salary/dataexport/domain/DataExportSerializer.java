package com.jinhyoung.salary.dataexport.domain;

import java.util.List;

/**
 * 배분 항목을 마크다운 표/CSV 텍스트로 직렬화한다(DATA-02). 순수 클래스 — 의존성·부수효과 없음(규칙 9), 단위
 * 테스트 필수. 문장을 만들지 않고(규칙 7) 구조화된 표만 출력한다 — 헤더는 언어 중립 필드명(name·category·amount).
 *
 * <p>포맷은 임포트(DATA-01 {@code parseImportTable})와 스펙을 공유해 라운드트립(내보내기→임포트 동일성)을
 * 보장한다: 금액은 천 단위 구분 없는 정수로 출력해 구분자(쉼표)와 충돌하지 않게 하고, 헤더·구분선 행은 임포트가
 * 자연히 걸러낸다(금액 칸이 없음). 분류 칸은 알파벳 enum 이름이라 임포트의 "금액=최댓값 숫자" 판정에 끼지 않는다.
 *
 * <p>마크다운은 노션·엑셀 복사 스타일의 파이프 표라 셀에 파이프·줄바꿈이 든 이름은 표를 깨므로(임포트도 동일)
 * 지원하지 않는다. CSV는 RFC 4180 인용으로 쉼표·따옴표가 든 이름까지 안전하게 왕복한다.
 */
public final class DataExportSerializer {

    private DataExportSerializer() {}

    /** 포맷에 맞춰 직렬화한다. */
    public static String serialize(List<ExportItem> items, ExportFormat format) {
        return switch (format) {
            case MARKDOWN -> toMarkdown(items);
            case CSV -> toCsv(items);
        };
    }

    /** 마크다운 파이프 표: 헤더 + 정렬 구분선 + 데이터 행. 항목이 없으면 헤더·구분선만 남는다. */
    static String toMarkdown(List<ExportItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("| name | category | amount |\n");
        sb.append("| --- | --- | --- |\n");
        for (ExportItem item : items) {
            sb.append("| ")
                    .append(item.name())
                    .append(" | ")
                    .append(item.category().name())
                    .append(" | ")
                    .append(item.amount())
                    .append(" |\n");
        }
        return sb.toString();
    }

    /** CSV: 헤더 + 데이터 행(RFC 4180 인용). 항목이 없으면 헤더 행만 남는다. */
    static String toCsv(List<ExportItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("name,category,amount\n");
        for (ExportItem item : items) {
            sb.append(csvCell(item.name()))
                    .append(',')
                    .append(item.category().name())
                    .append(',')
                    .append(item.amount())
                    .append('\n');
        }
        return sb.toString();
    }

    /** RFC 4180: 쉼표·따옴표·개행이 들어 있으면 큰따옴표로 감싸고 내부 따옴표는 두 번으로 이스케이프한다. */
    private static String csvCell(String value) {
        if (value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
