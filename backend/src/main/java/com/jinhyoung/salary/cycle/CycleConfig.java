package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.cycle.infra.RestClientHolidayApiClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** 사이클 빈 배선(CYCLE-01) — 공휴일 특일 API 클라이언트. */
@Configuration
@EnableConfigurationProperties(HolidayProperties.class)
public class CycleConfig {

    @Bean
    public RestClient holidayRestClient() {
        return RestClient.create();
    }

    @Bean
    public HolidayApiClient holidayApiClient(RestClient holidayRestClient, HolidayProperties holidayProperties) {
        return new RestClientHolidayApiClient(holidayRestClient, holidayProperties);
    }
}
