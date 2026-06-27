package com.jinhyoung.salary.common;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 운영 조정 파라미터 바인딩(구현규칙 6장) — app.policy.*. */
@Configuration
@EnableConfigurationProperties(PolicyProperties.class)
public class PolicyConfig {}
