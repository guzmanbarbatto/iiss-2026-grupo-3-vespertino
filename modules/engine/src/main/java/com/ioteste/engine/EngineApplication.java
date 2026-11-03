package com.ioteste.engine;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
public class EngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(EngineApplication.class, args);
    }

    /** Reloj del sistema, inyectable para poder fijar el tiempo en los tests. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Dos hilos: el ciclo de control y el mantenimiento del registro corren sin bloquearse entre sí. */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("engine-");
        return scheduler;
    }
}
