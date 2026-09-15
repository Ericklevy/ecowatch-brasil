package br.com.ecowatch.analytics_service;

import br.com.ecowatch.analytics_service.application.service.PrevisaoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceApplicationTests {

	@MockitoBean
	private PrevisaoService previsaoService;

	@Test
	void contextLoads() {
	}

}
