package br.com.ecowatch.analytics_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Para jobs agendados (ex: gerar previsões diárias)
public class AnalyticsServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(AnalyticsServiceApplication.class, args);
	}
}