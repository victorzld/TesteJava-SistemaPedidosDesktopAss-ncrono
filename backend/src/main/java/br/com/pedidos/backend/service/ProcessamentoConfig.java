package br.com.pedidos.backend.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.random.RandomGenerator;

@Configuration
public class ProcessamentoConfig {

    @Bean
    public RandomGenerator randomGenerator() {
        return RandomGenerator.getDefault();
    }

    @Bean
    public EsperaProcessamento esperaProcessamento() {
        return duracao -> Thread.sleep(duracao.toMillis());
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
