package com.ioteste.engine.config;

import com.ioteste.core.service.EvaluadorCore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfig {

    @Bean
    public EvaluadorCore evaluadorCore() {
        return new EvaluadorCore();
    }
}