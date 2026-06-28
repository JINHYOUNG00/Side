package com.jinhyoung.salary.dataexport;

import com.jinhyoung.salary.dataexport.domain.ExportFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트 전용 임포트 파서 — 프론트 {@code notionImport.ts}(DATA-01 {@code parseImportTable})의 규칙을 미러해
 * 내보내기 텍스트를 이름·금액 후보로 되돌린다. DATA-02의 "라운드트립(내보내기→임포트 동일성)"을 백엔드에서
 * 검증하는 데 쓴다(실 임포트는 프론트가 소유 — 그쪽은 동일 포맷을 {@code parseImportTable}로 재확인한다).
 *
 * <p>규칙(notionImport와 동일): 금액 = 유효 범위(1~10억) 안에서 값이 가장 큰 숫자 칸, 이름 = 금액 칸이 아니면서
 * 숫자 외 문자를 포함한 첫 칸. 헤더·구분선·금액 없는 행은 자연히 걸러진다. 구분자는 마크다운=파이프(또는 탭),
 * CSV=쉼표(RFC 4180 인용 해제). 내보내기 직렬화기와 짝을 이루는 역연산이다.
 */
final class ImportSpecParser {

    private static final int NAME_MAX = 50;
    private static final long AMOUNT_MIN = 1;
    private static final long AMOUNT_MAX = 1_000_000_000;

    private ImportSpecParser() {}

    record ParsedRow(String name, long amount) {}

    static List<ParsedRow> parse(String text, ExportFormat format) {
        List<ParsedRow> rows = new ArrayList<>();
        for (String line : text.split("\r?\n", -1)) {
            List<String> cells = splitCells(line, format);
            if (cells.size() < 2) {
                continue;
            }
            if (isSeparatorRow(cells)) {
                continue;
            }
            ParsedRow row = toRow(cells);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<String> splitCells(String line, ExportFormat format) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        List<String> raw = format == ExportFormat.CSV ? splitCsv(trimmed) : splitDelimited(trimmed);
        List<String> cells = new ArrayList<>();
        for (String cell : raw) {
            String c = cell.trim();
            if (!c.isEmpty()) {
                cells.add(c);
            }
        }
        return cells;
    }

    /** 마크다운/탭 구분: 탭이 있으면 탭, 없으면 파이프. 둘 다 없으면 표 행이 아니다. */
    private static List<String> splitDelimited(String trimmed) {
        if (trimmed.indexOf('\t') >= 0) {
            return List.of(trimmed.split("\t", -1));
        }
        if (trimmed.indexOf('|') >= 0) {
            return List.of(trimmed.split("\\|", -1));
        }
        return List.of();
    }

    /** RFC 4180 한 줄 분리 — 큰따옴표로 감싼 필드 안의 쉼표·이중따옴표를 해제한다. */
    private static List<String> splitCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(ch);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    private static boolean isSeparatorRow(List<String> cells) {
        return cells.stream().allMatch(c -> c.matches("^:?-+:?$"));
    }

    private static ParsedRow toRow(List<String> cells) {
        Long amount = null;
        int amountIdx = -1;
        for (int i = 0; i < cells.size(); i++) {
            Long n = parseAmount(cells.get(i));
            if (n != null && (amount == null || n > amount)) {
                amount = n;
                amountIdx = i;
            }
        }
        if (amount == null) {
            return null;
        }
        String name = null;
        for (int i = 0; i < cells.size(); i++) {
            if (i == amountIdx) {
                continue;
            }
            if (cells.get(i).matches(".*\\D.*")) {
                name = cells.get(i);
                break;
            }
        }
        if (name == null || name.length() > NAME_MAX) {
            return null;
        }
        return new ParsedRow(name, amount);
    }

    private static Long parseAmount(String cell) {
        String digits = cell.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            long n = Long.parseLong(digits);
            if (n < AMOUNT_MIN || n > AMOUNT_MAX) {
                return null;
            }
            return n;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
