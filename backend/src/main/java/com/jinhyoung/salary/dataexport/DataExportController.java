package com.jinhyoung.salary.dataexport;

import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.dataexport.domain.ExportFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데이터 내보내기(DATA-02, API명세 7장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로 본인 데이터만
 * 내보낸다(조회 전용). {@code GET /export?format=md|csv}로 활성 배분 항목을 표 텍스트로 내려준다 — 다운로드되도록
 * Content-Disposition을 첨부로 지정한다. 미지원 포맷은 400 VALIDATION_FAILED(문장 미생성, 코드만 — 규칙 7).
 */
@RestController
@RequestMapping("/api/v1/export")
public class DataExportController {

    private static final MediaType MARKDOWN_TYPE = MediaType.parseMediaType("text/markdown; charset=UTF-8");
    private static final MediaType CSV_TYPE = MediaType.parseMediaType("text/csv; charset=UTF-8");

    private final DataExportService dataExportService;

    public DataExportController(DataExportService dataExportService) {
        this.dataExportService = dataExportService;
    }

    @GetMapping
    public ResponseEntity<String> export(
            @AuthenticationPrincipal Long userId, @RequestParam(defaultValue = "md") String format) {
        ExportFormat resolved = resolve(format);
        String body = dataExportService.export(userId, resolved);
        return ResponseEntity.ok()
                .contentType(contentType(resolved))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename(resolved) + "\"")
                .body(body);
    }

    /** {@code format} 쿼리값을 포맷으로 해석한다 — md/markdown·csv만 허용, 그 외는 400 VALIDATION_FAILED. */
    private static ExportFormat resolve(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "md", "markdown" -> ExportFormat.MARKDOWN;
            case "csv" -> ExportFormat.CSV;
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("field", "format"));
        };
    }

    private static MediaType contentType(ExportFormat format) {
        return format == ExportFormat.CSV ? CSV_TYPE : MARKDOWN_TYPE;
    }

    private static String filename(ExportFormat format) {
        return format == ExportFormat.CSV ? "salary-export.csv" : "salary-export.md";
    }
}
