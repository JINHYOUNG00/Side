package com.jinhyoung.salary.dataexport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhyoung.salary.budgetitem.domain.Category;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 내보내기 직렬화 단위 테스트(DATA-02) — 마크다운 표·CSV 출력 포맷을 검증한다. 순수 클래스라 의존성 없이
 * 입력→출력만 본다. 라운드트립(직렬화→임포트 파싱 동일성)은 {@code DataExportRoundtripTest}가 별도로 본다.
 */
class DataExportSerializerTest {

    private static final List<ExportItem> ITEMS =
            List.of(new ExportItem("월세", Category.FIXED, 500_000), new ExportItem("OO적금", Category.SAVING, 300_000));

    @Test
    void 마크다운_헤더와_구분선과_데이터행을_출력한다() {
        String md = DataExportSerializer.serialize(ITEMS, ExportFormat.MARKDOWN);

        assertThat(md)
                .isEqualTo(
                        """
                        | name | category | amount |
                        | --- | --- | --- |
                        | 월세 | FIXED | 500000 |
                        | OO적금 | SAVING | 300000 |
                        """);
    }

    @Test
    void CSV_헤더와_데이터행을_출력한다() {
        String csv = DataExportSerializer.serialize(ITEMS, ExportFormat.CSV);

        assertThat(csv)
                .isEqualTo(
                        """
                        name,category,amount
                        월세,FIXED,500000
                        OO적금,SAVING,300000
                        """);
    }

    @Test
    void 금액은_천단위_구분없는_정수로_출력한다() {
        String md = DataExportSerializer.serialize(
                List.of(new ExportItem("큰항목", Category.SAVING, 1_234_567)), ExportFormat.MARKDOWN);

        assertThat(md).contains("| 1234567 |").doesNotContain("1,234,567");
    }

    @Test
    void CSV는_쉼표_따옴표가_든_이름을_RFC4180으로_인용한다() {
        List<ExportItem> tricky = List.of(
                new ExportItem("월세, 관리비", Category.FIXED, 500_000),
                new ExportItem("적금\"우대\"", Category.SAVING, 300_000));

        String csv = DataExportSerializer.serialize(tricky, ExportFormat.CSV);

        assertThat(csv).contains("\"월세, 관리비\",FIXED,500000");
        assertThat(csv).contains("\"적금\"\"우대\"\"\",SAVING,300000");
    }

    @Test
    void 항목이_없으면_헤더만_남긴다() {
        assertThat(DataExportSerializer.serialize(List.of(), ExportFormat.MARKDOWN))
                .isEqualTo("| name | category | amount |\n| --- | --- | --- |\n");
        assertThat(DataExportSerializer.serialize(List.of(), ExportFormat.CSV)).isEqualTo("name,category,amount\n");
    }
}
