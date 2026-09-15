<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white" />
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white" />
  <img src="https://img.shields.io/badge/Azure-Container%20Apps-0078D4?style=for-the-badge&logo=microsoft-azure&logoColor=white" />
  <img src="https://img.shields.io/badge/Supabase-PostgreSQL-3FCF8E?style=for-the-badge&logo=supabase&logoColor=white" />
  <img src="https://img.shields.io/badge/Upstash-Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white" />
</p>

# 🌿 EcoWatch Brasil

**Plataforma de monitoramento ambiental em tempo real para cidades brasileiras**, baseada em microsserviços event-driven com Java 21, Spring Boot 3 e deploy serverless no Azure.

O sistema coleta dados climáticos, qualidade do ar, focos de incêndio e nível de rios de **10 capitais brasileiras**, gera alertas automáticos quando condições críticas são detectadas e notifica usuários por e-mail e push notification.

---

## 📑 Índice

- [Arquitetura](#-arquitetura)
- [Microsserviços](#-microsserviços)
- [Fluxo de Dados](#-fluxo-de-dados)
- [Padrões de Projeto](#-padrões-de-projeto)
- [Stack Tecnológica](#-stack-tecnológica)
- [Por que essas Tecnologias?](#-por-que-essas-tecnologias)
- [Pré-requisitos](#-pré-requisitos)
- [Como Executar Localmente](#-como-executar-localmente)
- [APIs e Endpoints](#-apis-e-endpoints)
- [Deploy no Azure](#-deploy-no-azure)
- [Infraestrutura Cloud](#-infraestrutura-cloud)
- [Segurança](#-segurança)
- [Cidades Monitoradas](#-cidades-monitoradas)
- [Estrutura do Projeto](#-estrutura-do-projeto)
- [Licença](#-licença)

---

## 🏗 Arquitetura

O EcoWatch Brasil segue uma **arquitetura de microsserviços event-driven** com separação clara entre escrita e leitura (CQRS).

```
                        ┌──────────────────────┐
                        │   APIs Externas       │
                        │ NASA · Open-Meteo     │
                        │ INPE · CEMADEN · ANA  │
                        └──────────┬───────────┘
                                   │
                                   ▼
                   ┌───────────────────────────────┐
                   │     data-ingestion-service     │
                   │  (WebFlux/Netty · Reativo)     │
                   │  Coleta a cada hora (cron)     │
                   └───────────────┬───────────────┘
                                   │ Kafka
                        ┌──────────┴──────────┐
                        ▼                     ▼
           ┌────────────────────┐  ┌────────────────────┐
           │   alert-service    │  │ analytics-service   │
           │ (Avaliação de      │  │ (Previsões e        │
           │  regras de alerta) │  │  scores de risco)   │
           └──────┬─────┬──────┘  └──────────┬──────────┘
                  │     │                     │
           Redis  │     │ RabbitMQ            │ JPA
           (cache)│     ▼                     ▼
                  │  ┌──────────────────┐  ┌──────────────┐
                  │  │ notification-    │  │  PostgreSQL   │
                  │  │ service          │  │  (Supabase)   │
                  │  │ (Gmail SMTP +    │  └──────┬───────┘
                  │  │  Firebase Push)  │         │
                  │  └──────────────────┘         │ JPA (read-only)
                  │                               ▼
                  │                    ┌──────────────────┐
                  └──────────────────► │ read-model-      │
                                      │ service           │
                                      │ (Dashboard API)   │
                                      └──────────────────┘
```

---

## 🔧 Microsserviços

| Serviço | Porta | Framework | Responsabilidade |
|---------|-------|-----------|-----------------|
| **data-ingestion-service** | 8083 | WebFlux/Netty (Reativo) | Coleta dados de APIs externas (NASA, Open-Meteo, INPE) e publica no Kafka |
| **alert-service** | 8081 | Tomcat (Servlet) | Consome do Kafka, avalia regras de alerta, persiste no PostgreSQL, publica no RabbitMQ, cacheia no Redis |
| **analytics-service** | 8085 | Tomcat (Servlet) | Consome do Kafka, gera previsões meteorológicas e scores de risco |
| **notification-service** | 8084 | Tomcat (Servlet) | Consome do RabbitMQ, envia notificações por Gmail SMTP e Firebase Push |
| **read-model-service** | 8086 | Tomcat (Servlet) | Lê do PostgreSQL, serve a API REST do Dashboard (única com ingress externo) |

---

## 🔄 Fluxo de Dados

```
1. COLETA     → data-ingestion busca dados de 10 cidades via APIs externas
2. STREAMING  → dados publicados no tópico Kafka "weather-readings"
3. AVALIAÇÃO  → alert-service consome e aplica regras de alerta
4. CACHE      → leituras recentes cacheadas no Redis (TTL: 5 min)
5. ALERTAS    → alertas publicados no RabbitMQ para notificação
6. PREVISÃO   → analytics-service gera previsões e persiste no PostgreSQL
7. NOTIFICAÇÃO→ notification-service envia e-mails/push para alertas críticos
8. DASHBOARD  → read-model-service expõe dados consolidados via REST
```

---

## 🧩 Padrões de Projeto

### Event-Driven Architecture (EDA)
Os microsserviços se comunicam **exclusivamente por eventos**, desacoplando produtores de consumidores. Se o `notification-service` ficar fora do ar, os alertas ficam na fila do RabbitMQ e são processados quando ele voltar.

### CQRS (Command Query Responsibility Segregation)
- **Escrita (Command)**: `alert-service` e `analytics-service` escrevem no PostgreSQL
- **Leitura (Query)**: `read-model-service` lê do PostgreSQL — separação permite otimizar cada lado independentemente

### Dual-Messaging (Kafka + RabbitMQ)
- **Apache Kafka**: streaming de alta vazão para dados de sensores (`weather-readings`). Escolhido por durabilidade, replay e capacidade de múltiplos consumidores no mesmo tópico
- **RabbitMQ**: entrega confiável de alertas para notificação. Escolhido por roteamento flexível, confirmação de entrega (ack) e padrão publish/subscribe com dead-letter queues

### Repository + JPA
Camada de persistência desacoplada com Spring Data JPA, permitindo trocar o banco sem alterar regras de negócio.

### Builder Pattern (Records)
`WeatherReading` é um Java Record imutável com builder, garantindo thread-safety e clareza.

---

## 🛠 Stack Tecnológica

| Camada | Tecnologia | Versão |
|--------|-----------|--------|
| **Linguagem** | Java (OpenJDK) | 21 LTS |
| **Framework** | Spring Boot | 3.4.x |
| **Servidor (ingestão)** | Netty (WebFlux reativo) | — |
| **Servidor (demais)** | Apache Tomcat (servlet) | — |
| **Streaming** | Apache Kafka / Azure Event Hubs | 7.5 / Basic |
| **Mensageria** | RabbitMQ | 3.x |
| **Banco de Dados** | PostgreSQL (Supabase) | 15 |
| **Cache** | Redis (Upstash) | 7.x |
| **E-mail** | Gmail SMTP | — |
| **Push** | Firebase Cloud Messaging (FCM) | — |
| **Contêiner** | Docker + Docker Compose | — |
| **Cloud** | Azure Container Apps | Serverless |
| **Registry** | Azure Container Registry (ACR) | — |
| **Observabilidade** | Micrometer + Prometheus | — |
| **Documentação API** | Swagger / OpenAPI 3 | — |

---

## 💡 Por que essas Tecnologias?

### Java 21 + Virtual Threads
Java 21 é a **LTS mais recente** com suporte a Virtual Threads (Project Loom). Em vez de alocar 1 thread do OS por requisição (≈1 MB cada), virtual threads custam **poucos KB** — ideal para microsserviços com consumo limitado de memória em containers.

### Spring Boot 3.4 + WebFlux
- **Spring Boot**: ecossistema maduro com auto-configuração, atualizações de segurança e integração nativa com Kafka, RabbitMQ, JPA, Redis
- **WebFlux (data-ingestion)**: modelo reativo não-bloqueante para chamadas HTTP externas — uma única thread atende múltiplas requisições simultâneas a APIs como NASA/Open-Meteo

### Supabase (PostgreSQL)
- **PostgreSQL gerenciado** com painel web, backup automático e autenticação integrada
- **Session Pooler**: pool de conexões via PgBouncer, compatível com IPv4 (necessário para Azure Container Apps)
- **Free tier**: 500 MB de armazenamento, suficiente para dados ambientais

### Upstash (Redis)
- **Redis serverless** com TLS nativo e free tier generoso (10.000 comandos/dia)
- **Compatível** com `spring.data.redis.url` usando protocolo `rediss://` (TLS)
- Elimina a necessidade de provisionar e gerenciar um container Redis em produção

### Azure Container Apps
- **Serverless com escala zero**: o container desliga quando não há tráfego, reduzindo custos
- **Azure for Students**: $100 de crédito gratuito
- **Região `brazilsouth`**: latência mínima para o Brasil
- **Azure Event Hubs**: substituto gerenciado do Kafka com protocolo compatível (SASL_SSL)

### Kafka vs RabbitMQ — Por que ambos?
| Critério | Kafka | RabbitMQ |
|----------|-------|----------|
| **Caso de uso** | Streaming de dados (sensor → processamento) | Alertas para notificação |
| **Múltiplos consumidores** | ✅ Sim (alert + analytics leem o mesmo tópico) | ❌ Tipicamente 1 consumidor por fila |
| **Replay** | ✅ Sim (log persistente) | ❌ Mensagem removida após ack |
| **Entrega garantida** | At-least-once | At-least-once + dead-letter |
| **Ordem** | ✅ Por partição | ❌ Sem garantia |

---

## 📋 Pré-requisitos

- **Java 21** (JDK)
- **Docker** + **Docker Compose**
- **Maven** (wrapper `mvnw` incluído em cada serviço)

---

## 🚀 Como Executar Localmente

### 1. Clone o repositório

```bash
git clone https://github.com/ErickLevy/ecowatch-brasil.git
cd ecowatch-brasil
```

### 2. Configure as variáveis de ambiente

```bash
cp .env.example .env
# Edite o .env com suas credenciais (Gmail, Supabase, etc.)
```

> **Nota**: Para desenvolvimento local, o `docker-compose.yml` sobe um PostgreSQL, Redis, Kafka e RabbitMQ locais. As variáveis de `SPRING_DATASOURCE_*` do `.env` são sobrescritas pelo `docker-compose.yml` automaticamente.

### 3. Crie o volume Docker (primeira vez)

```bash
docker volume create ecowatch-brasil_postgres_data
```

### 4. Suba todos os serviços

```bash
docker compose up --build -d
```

### 5. Verifique os containers

```bash
docker ps
```

Espere todos os 10 containers ficarem `healthy` (≈1-2 minutos):
- `ecowatch-zookeeper`
- `ecowatch-kafka`
- `ecowatch-rabbitmq`
- `ecowatch-redis`
- `ecowatch-postgres`
- `ecowatch-data-ingestion`
- `ecowatch-alert`
- `ecowatch-read-model`
- `ecowatch-notification`
- `ecowatch-analytics`

### 6. Teste os endpoints

```bash
# Status do agendador (10 cidades)
curl http://localhost:8083/api/scheduler/status

# Disparar coleta manual
curl -X POST http://localhost:8083/api/scheduler/trigger

# Dados cacheados de uma cidade
curl http://localhost:8081/api/cache/brasilia

# Dashboard de uma cidade
curl http://localhost:8086/api/dashboard/brasilia

# RabbitMQ Management UI
# http://localhost:15672 (guest/guest)
```

---

## 📡 APIs e Endpoints

### data-ingestion-service `:8083`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/scheduler/status` | Status da coleta (cidades, último ciclo, próximo) |
| `POST` | `/api/scheduler/trigger` | Dispara coleta manual (retorna 429 se já em andamento) |
| `GET` | `/v3/api-docs` | Documentação OpenAPI |

### alert-service `:8081`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/weather` | Simula envio de dados de sensor para o Kafka |
| `GET` | `/api/cache/{municipio}` | Última leitura cacheada no Redis (TTL: 5 min) |
| `GET` | `/swagger-ui/index.html` | Swagger UI |
| `GET` | `/v3/api-docs` | Documentação OpenAPI |

### read-model-service `:8086`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/dashboard/{municipio}` | Dados consolidados (alertas + previsões) |
| `GET` | `/swagger-ui/index.html` | Swagger UI |
| `GET` | `/v3/api-docs` | Documentação OpenAPI |

> **Nota**: A busca por município é case-insensitive. Para cidades com espaço, use o nome completo: `rio de janeiro`

### notification-service `:8084`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| — | — | Não expõe APIs REST — consome alertas do RabbitMQ e envia e-mails/push |

### analytics-service `:8085`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| — | — | Não expõe APIs REST — consome do Kafka e persiste previsões no PostgreSQL |

---

## ☁ Deploy no Azure

### Recursos provisionados

| Recurso | Nome | Tipo |
|---------|------|------|
| Resource Group | `ecowatch-rg` | — |
| Container Registry | `ecowatchacr4705` | Basic |
| Event Hubs | `ecowatchhub4705` | Basic (Kafka-enabled) |
| Container Apps Environment | `ecowatch-env` | Serverless |
| RabbitMQ | `rabbitmq` | Container App (interno) |

### Variáveis de ambiente em produção

Na Azure, as credenciais são injetadas como **secrets** no Container App:

| Variável | Valor em Produção |
|----------|-------------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:5432/postgres` |
| `SPRING_DATASOURCE_USERNAME` | `postgres.{PROJECT_REF}` |
| `SPRING_DATASOURCE_PASSWORD` | `(secret)` |
| `SPRING_REDIS_URL` | `rediss://default:{PASSWORD}@willing-amoeba-77167.upstash.io:6379` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `ecowatchhub4705.servicebus.windows.net:9093` |
| `SPRING_RABBITMQ_HOST` | `rabbitmq` (DNS interno do Container Apps) |

### Ingress

| Serviço | Ingress | Justificativa |
|---------|---------|---------------|
| `read-model-service` | **externo** | Única API exposta ao público (Dashboard) |
| `alert-service` | interno | Comunicação interna apenas |
| `analytics-service` | interno | Comunicação interna apenas |
| `notification-service` | interno | Comunicação interna apenas |
| `data-ingestion-service` | interno | Comunicação interna apenas |

---

## 🛡 Infraestrutura Cloud

```
┌─────────────────────────────────────────────────────────┐
│                  Azure Container Apps                    │
│                  (brazilsouth, serverless)               │
│                                                          │
│  ┌─────────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │ data-ingestion  │  │alert-service │  │ analytics  │ │
│  │ (interno)       │  │ (interno)    │  │ (interno)  │ │
│  └────────┬────────┘  └──────┬───────┘  └─────┬──────┘ │
│           │                  │                 │        │
│  ┌────────┴──────────────────┴─────────────────┘        │
│  │ Azure Event Hubs (Kafka protocol, SASL_SSL)          │
│  └──────────────────────────────────────────────        │
│                                                          │
│  ┌─────────────────┐  ┌──────────────────────────┐      │
│  │ notification    │  │ read-model-service       │      │
│  │ (interno)       │  │ (EXTERNO — Dashboard API)│      │
│  └────────┬────────┘  └──────────────────────────┘      │
│           │                                              │
│  ┌────────┴──────┐                                      │
│  │   RabbitMQ    │                                      │
│  │  (interno)    │                                      │
│  └───────────────┘                                      │
└─────────────────────────────────────────────────────────┘
         │                    │                  │
         ▼                    ▼                  ▼
   ┌──────────┐       ┌────────────┐     ┌────────────┐
   │ Supabase │       │  Upstash   │     │   Gmail    │
   │PostgreSQL│       │   Redis    │     │   SMTP     │
   │ (AWS SP) │       │  (TLS)     │     │            │
   └──────────┘       └────────────┘     └────────────┘
```

---

## 🔒 Segurança

### Implementado
- **CORS**: `@CrossOrigin(origins = "*")` no `DashboardController` (único endpoint público)
- **Validação de input**: payload JSON validado contra o schema `WeatherReading` antes de enviar ao Kafka — retorna 400 para dados malformados
- **Guarda de concorrência**: endpoint `/api/scheduler/trigger` usa `AtomicBoolean` para impedir execuções duplicadas — retorna 429 se coleta já estiver em andamento
- **Segredos fora do código**: todas as credenciais via variáveis de ambiente (`.env` no `.gitignore`)
- **Ingress restrito**: apenas `read-model-service` com acesso externo; demais serviços são internos

### Planejado
- **Rate Limiting**: Bucket4j ou Spring Cloud Gateway para limitar requisições por IP
- **Autenticação JWT**: Spring Security + Supabase Auth para proteger endpoints do Dashboard
- **TLS end-to-end**: HTTPS no Azure Container Apps (automático via domínio `azurecontainerapps.io`)

---

## 🌍 Cidades Monitoradas

O sistema monitora **10 capitais brasileiras** representando todas as regiões do país:

| Cidade | Estado | Região | Bioma Principal |
|--------|--------|--------|-----------------|
| Brasília | DF | Centro-Oeste | Cerrado |
| Rio de Janeiro | RJ | Sudeste | Mata Atlântica |
| São Paulo | SP | Sudeste | Mata Atlântica |
| Salvador | BA | Nordeste | Mata Atlântica |
| Recife | PE | Nordeste | Mata Atlântica |
| Porto Alegre | RS | Sul | Pampa |
| Curitiba | PR | Sul | Mata Atlântica |
| Belo Horizonte | MG | Sudeste | Cerrado / Mata Atlântica |
| Fortaleza | CE | Nordeste | Caatinga |
| Manaus | AM | Norte | Amazônia |

### Dados coletados por cidade

| Dado | Fonte | Descrição |
|------|-------|-----------|
| 🌡️ Temperatura | NASA Power / Open-Meteo | Temperatura do ar em °C |
| 💧 Umidade | NASA Power / Open-Meteo | Umidade relativa do ar em % |
| 🌧️ Precipitação | NASA Power / Open-Meteo | Volume de chuva em mm |
| 🌫️ PM2.5 | Open-Meteo Air Quality | Material particulado fino (µg/m³) |
| 🌫️ PM10 | Open-Meteo Air Quality | Material particulado grosso (µg/m³) |
| 🔥 Focos de Incêndio | INPE BDQueimadas | Contagem de focos ativos |
| 🌊 Nível do Rio | ANA HidroWeb | Nível hidrométrico em metros |
| ⚠️ Alerta Enxurrada | CEMADEN | Flag booleano de risco |

---

## 📂 Estrutura do Projeto

```
ecowatch-brasil/
├── .env.example                    # Template de variáveis de ambiente
├── .gitignore                      # Ignora .env, chaves, firebase, etc.
├── docker-compose.yml              # Orquestração local (10 containers)
├── README.md                       # Este arquivo
│
├── data-ingestion-service/         # Coleta de dados (WebFlux/Netty)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── controller/
│       │   └── SchedulerStatusController.java
│       ├── model/
│       │   └── WeatherReading.java
│       ├── producer/
│       │   └── WeatherKafkaProducer.java
│       ├── scheduler/
│       │   └── WeatherScheduler.java
│       └── service/
│           └── NasaPowerService.java (+ outros)
│
├── alert-service/                  # Avaliação de alertas
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── consumer/
│       │   └── WeatherReadingConsumer.java
│       ├── controller/
│       │   ├── CacheController.java
│       │   └── WeatherReadingController.java
│       ├── model/
│       │   ├── AlertaGerado.java
│       │   └── Previsao.java
│       └── service/
│           └── WeatherCacheService.java
│
├── analytics-service/              # Previsões e scores de risco
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/.../
│
├── notification-service/           # E-mail e push notifications
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/.../
│       └── consumer/
│           └── AlertNotificationConsumer.java
│
├── read-model-service/             # API do Dashboard (CQRS - leitura)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/.../
│       └── controller/
│           └── DashboardController.java
│
└── azure/                          # Scripts de deploy Azure
    └── deploy-azure.ps1
```

---

## 📊 Modelo de Dados

### Tabela `alertas_gerados`

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | `BIGINT` (PK) | Identificador auto-incremento |
| `municipio` | `VARCHAR` | Nome da cidade |
| `estado` | `VARCHAR` | Sigla do estado |
| `tipo_alerta` | `VARCHAR` | Ex: "TEMPERATURA_ALTA", "PM25_CRITICO" |
| `mensagem` | `TEXT` | Descrição detalhada do alerta |
| `timestamp` | `TIMESTAMP` | Data/hora da leitura |
| `created_at` | `TIMESTAMP` | Data/hora de criação do alerta |

### Tabela `previsoes`

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | `BIGINT` (PK) | Identificador auto-incremento |
| `municipio` | `VARCHAR` | Nome da cidade |
| `temperatura` | `DOUBLE` | Temperatura em °C |
| `umidade` | `DOUBLE` | Umidade relativa em % |
| `precipitacao` | `DOUBLE` | Precipitação em mm |
| `risk_score` | `DOUBLE` | Score de risco calculado (0-100) |
| `timestamp` | `TIMESTAMP` | Data/hora da previsão |

---

## 🧪 Testes

```bash
# Compilar e rodar testes de todos os serviços
cd alert-service && ./mvnw test
cd analytics-service && ./mvnw test
cd read-model-service && ./mvnw test
cd notification-service && ./mvnw test
cd data-ingestion-service && ./mvnw test
```

Os testes usam **H2 in-memory** (perfil `test`) para não depender de banco externo.

---

## 🤝 Contribuindo

1. Fork o projeto
2. Crie uma branch: `git checkout -b feature/minha-feature`
3. Commit suas mudanças: `git commit -m 'feat: minha feature'`
4. Push para a branch: `git push origin feature/minha-feature`
5. Abra um Pull Request

---

## 📝 Licença

Este projeto é desenvolvido como parte de trabalho acadêmico na **Universidade de Brasília (UnB)**.

---

<p align="center">
  Feito com ☕ e ❤️ por <a href="https://github.com/ErickLevy">Erick Levy</a>
</p>
