package com.jinhyoung.salary.dataexport.domain;

/**
 * 데이터 내보내기 포맷(DATA-02, API명세 7장). 마크다운 표(노션 임포트 호환)와 CSV(스프레드시트용)를 지원한다.
 * 순수 enum — 프레임워크 의존 없음(ArchUnit 규칙 9). HTTP 콘텐츠 타입·파일명 매핑은 컨트롤러가 맡는다.
 */
public enum ExportFormat {
    MARKDOWN,
    CSV
}
