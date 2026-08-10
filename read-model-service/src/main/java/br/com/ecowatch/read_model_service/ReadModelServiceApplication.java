package br.com.ecowatch.read_model_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ReadModelServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(ReadModelServiceApplication.class, args);
	}
}