package br.com.ecowatch.alert_service.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaEventHubsConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:alert-service-group}")
    private String groupId;

    @Value("${eventhubs.connection-string:}")
    private String connectionString;

    @Value("${kafka.username:}")
    private String kafkaUsername;

    @Value("${kafka.password:}")
    private String kafkaPassword;

    @Value("${spring.kafka.properties.sasl.jaas.config:}")
    private String explicitJaas;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // Caso 1: Aiven ou Kafka padrão com username/password
        if (kafkaUsername != null && !kafkaUsername.isBlank()) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
            props.put(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-256");
            props.put(SaslConfigs.SASL_JAAS_CONFIG, String.format(
                    "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
                    kafkaUsername.trim(), kafkaPassword.trim()
            ));
            loadCaCertIfAvailable(props);
            log.info("🔐 [Kafka] Configurado com SASL_SSL / SCRAM-SHA-256 para usuário: {}", kafkaUsername);
        }
        // Caso 2: Azure Event Hubs
        else if (connectionString != null && !connectionString.isBlank()) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
            props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
            props.put(SaslConfigs.SASL_JAAS_CONFIG, String.format(
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$ConnectionString\" password=\"%s\";",
                    connectionString.trim()
            ));
            log.info("🔐 [Kafka] Configurado com SASL_SSL / PLAIN (Azure Event Hubs)");
        }
        // Caso 3: JAAS explícito
        else if (explicitJaas != null && !explicitJaas.isBlank()) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
            props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
            props.put(SaslConfigs.SASL_JAAS_CONFIG, explicitJaas);
        }

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
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
