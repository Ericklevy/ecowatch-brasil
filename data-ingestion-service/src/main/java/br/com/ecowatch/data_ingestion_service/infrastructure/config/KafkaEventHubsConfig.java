package br.com.ecowatch.data_ingestion_service.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaEventHubsConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${eventhubs.connection-string:}")
    private String connectionString;

    @Value("${kafka.username:}")
    private String kafkaUsername;

    @Value("${kafka.password:}")
    private String kafkaPassword;

    @Value("${spring.kafka.properties.sasl.jaas.config:}")
    private String explicitJaas;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Caso 1: Aiven ou Kafka padrão com username/password
        if (kafkaUsername != null && !kafkaUsername.isBlank()) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
            props.put(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-256");
            props.put(SaslConfigs.SASL_JAAS_CONFIG, String.format(
                    "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
                    kafkaUsername.trim(), kafkaPassword.trim()
            ));
            loadCaCertIfAvailable(props);
            log.info("🔐 [Kafka] Producer configurado com SASL_SSL / SCRAM-SHA-256 para usuário: {}", kafkaUsername);
        }
        // Caso 2: Azure Event Hubs
        else if (connectionString != null && !connectionString.isBlank()) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
            props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
            props.put(SaslConfigs.SASL_JAAS_CONFIG, String.format(
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$ConnectionString\" password=\"%s\";",
                    connectionString.trim()
            ));
            log.info("🔐 [Kafka] Producer configurado com SASL_SSL / PLAIN (Azure Event Hubs)");
        }
        // Caso 3: JAAS explícito
        else if (explicitJaas != null && !explicitJaas.isBlank()) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
            props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
            props.put(SaslConfigs.SASL_JAAS_CONFIG, explicitJaas);
        }

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    private void loadCaCertIfAvailable(Map<String, Object> props) {
        try (InputStream is = getClass().getResourceAsStream("/certs/ca.pem")) {
            if (is != null) {
                String certPem = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
                props.put(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, certPem);
                log.info("📜 [Kafka] Certificado CA carregado com sucesso do classpath (/certs/ca.pem)!");
            }
        } catch (Exception e) {
            log.warn("⚠️ [Kafka] Não foi possível carregar certs/ca.pem: {}", e.getMessage());
        }
    }
}
