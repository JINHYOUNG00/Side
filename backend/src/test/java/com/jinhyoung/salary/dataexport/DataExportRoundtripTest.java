package com.jinhyoung.salary.dataexport;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.dataexport.ImportSpecParser.ParsedRow;
import com.jinhyoung.salary.dataexport.domain.DataExportSerializer;
import com.jinhyoung.salary.dataexport.domain.ExportFormat;
import com.jinhyoung.salary.dataexport.domain.ExportItem;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 라운드트립 단위 테스트(DATA-02) — 내보내기 직렬화 출력을 임포트 스펙({@link ImportSpecParser}, 프론트
 * {@code parseImportTable} 미러)으로 되읽었을 때 이름·금액이 그대로 복원되는지(내보내기→임포트 동일성) 본다.
 * 분류는 임포트가 복원하지 않으므로(사용자 UI 재선택) 비교 대상이 아니다.
 */
class DataExportRoundtripTest {

    private static final List<ExportItem> ITEMS = List.of(
            new ExportItem("월세", Category.FIXED, 500_000),
            new ExportItem("OO적금", Category.SAVING, 300_000),
            new ExportItem("청약", Category.SAVING, 100_000));

    @Test
    void 마크다운_내보내기를_임포트로_되읽으면_이름금액이_순서대로_보존된다() {
        String md = DataExportSerializer.serialize(ITEMS, ExportFormat.MARKDOWN);

        List<ParsedRow> parsed = ImportSpecParser.parse(md, ExportFormat.MARKDOWN);

        assertThat(parsed).containsExactly(toRows(ITEMS));
    }

    @Test
    void CSV_내보내기를_임포트로_되읽으면_이름금액이_순서대로_보존된다() {
        String csv = DataExportSerializer.serialize(ITEMS, ExportFormat.CSV);

        List<ParsedRow> parsed = ImportSpecParser.parse(csv, ExportFormat.CSV);

        assertThat(parsed).containsExactly(toRows(ITEMS));
    }

    @Test
    void CSV_쉼표_따옴표가_든_이름도_라운드트립한다() {
        List<ExportItem> tricky = List.of(
                new ExportItem("월세, 관리비", Category.FIXED, 500_000),
                new ExportItem("적금\"우대\"", Category.SAVING, 300_000));

        String csv = DataExportSerializer.serialize(tricky, ExportFormat.CSV);

        assertThat(ImportSpecParser.parse(csv, ExportFormat.CSV)).containsExactly(toRows(tricky));
    }

    private static ParsedRow[] toRows(List<ExportItem> items) {
        return items.stream()
                .map(item -> new ParsedRow(item.name(), item.amount()))
                .toArray(ParsedRow[]::new);
    }
}
