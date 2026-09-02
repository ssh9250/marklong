package com.example.marklong.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 / Spring 7 부터 자동설정은 Jackson 3(tools.jackson) 기반의 JsonMapper 만 등록하고
 * 구(舊) com.fasterxml.jackson.databind.ObjectMapper 빈은 더 이상 만들어주지 않는다.
 * 아직 Jackson 2 ObjectMapper 를 직접 주입받는 코드가 있어 명시적으로 등록한다.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }
}
