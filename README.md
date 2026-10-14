<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white" />
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white" />
  <img src="https://img.shields.io/badge/Azure-Container%20Apps-0078D4?style=for-the-badge&logo=microsoft-azure&logoColor=white" />
  <img src="https://img.shields.io/badge/Supabase-PostgreSQL-3FCF8E?style=for-the-badge&logo=supabase&logoColor=white" />
  <img src="https://img.shields.io/badge/Upstash-Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white" />
  <img src="https://img.shields.io/badge/Apache%20Kafka-Aiven%20Cloud-231F20?style=for-the-badge&logo=apache-kafka&logoColor=white" />
  <img src="https://img.shields.io/badge/Cloud%20Cost-R$%200,00-success?style=for-the-badge&logo=azure-pipelines&logoColor=white" />
</p>

# 🌿 EcoWatch Brasil

**Plataforma de monitoramento ambiental em tempo real para capitais brasileiras**, baseada em microsserviços event-driven com Java 21, Spring Boot 3 e deploy serverless com **arquitetura híbrida de custo zero (R$ 0,00/mês)**.

O sistema coleta dados climáticos, qualidade do ar, focos de incêndio e nível de rios de **todas as 27 capitais brasileiras**, gera alertas automáticos quando condições críticas são detectadas, agrega e sanitiza notícias oficiais de desastres e notifica usuários por e-mail e push notification.

---

## 📑 Índice

- [Arquitetura](#-arquitetura)
- [Microsserviços](#-microsserviços)
- [Fluxo de Dados](#-fluxo-de-dados)
- [Padrões de Projeto](#-padrões-de-projeto)
- [Stack Tecnológica](#-stack-tecnológica)
- [Evolução da Arquitetura e Otimização de Custos (R$ 0,00)](#-evolução-da-arquitetura-e-otimização-de-custos-r-000)
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
| **data-ingestion-service** | 8083 | WebFlux/Netty (Reativo) | Coleta dados climáticos e ambientais de APIs externas (NASA, Open-Meteo, INPE, CEMADEN) e publica no Kafka |
| **alert-service** | 8081 | Tomcat (Servlet) | Consome do Kafka, avalia regras de criticidade, persiste no PostgreSQL, publica no RabbitMQ, cacheia no Redis |
| **analytics-service** | 8085 | Tomcat (Servlet) | Consome do Kafka, gera previsões meteorológicas e calcula scores de risco ambiental |
| **notification-service** | 8084 | Tomcat (Servlet) | Consome do RabbitMQ, envia notificações por Gmail SMTP e Firebase Push |
| **read-model-service** | 8086 | Tomcat (Servlet) | Lê do PostgreSQL, serve a API REST do Dashboard e API de Notícias Ambientais Oficiais com sanitização semântica (único com ingress externo) |

---

## 🔄 Fluxo de Dados

```
1. COLETA     → data-ingestion busca dados de 27 capitais via APIs públicas
2. STREAMING  → telemetria publicada no tópico Kafka "weather-readings" (Aiven/Azure)
3. AVALIAÇÃO  → alert-service consome e aplica matriz de regras de criticidade
4. CACHE      → leituras recentes cacheadas no Redis Upstash (TTL: 5 min)
5. ALERTAS    → alertas publicados na fila RabbitMQ para notificação
6. PREVISÃO   → analytics-service gera previsões e persiste no Supabase PostgreSQL
7. NOTIFICAÇÃO→ notification-service envia e-mails/push para eventos críticos
8. NOTÍCIAS   → read-model-service ingere feeds RSS oficiais, sanitiza ruídos e categoriza
9. DASHBOARD  → read-model-service expõe telemetria, alertas e notícias consolidadas via REST
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

| Camada | Tecnologia | Versão / Tipo |
|--------|-----------|---------------|
| **Linguagem** | Java (OpenJDK) | 21 LTS |
| **Framework** | Spring Boot | 3.4.x |
| **Servidor (ingestão)** | Netty (WebFlux reativo) | — |
| **Servidor (demais)** | Apache Tomcat (servlet) | — |
| **Streaming** | Apache Kafka (Aiven Cloud / Azure Event Hubs) | SASL_SSL (SCRAM-SHA-256 / PLAIN) |
| **Mensageria** | RabbitMQ | 3.x |
| **Banco de Dados** | PostgreSQL (Supabase) | 15 (PgBouncer IPv4) |
| **Cache** | Redis (Upstash) | 7.x (Serverless TLS) |
| **Agendador** | GitHub Actions (Cron Serverless) | 3x ao dia / Sob demanda |
| **Notícias** | Feeds RSS Oficiais (Gov/EBC/G1) | Filtro Sanitizador Semântico Java |
| **E-mail** | Gmail SMTP | — |
| **Push** | Firebase Cloud Messaging (FCM) | — |
| **Contêiner** | Docker + Docker Compose | — |
| **Cloud** | Azure Container Apps | Serverless (Scale-to-Zero) |
| **Registry** | Azure Container Registry (ACR) | Basic |
| **Observabilidade** | Micrometer + Prometheus | — |
| **Documentação API** | Swagger / OpenAPI 3 | — |

---

## 💰 Evolução da Arquitetura e Otimização de Custos (R$ 0,00)

O EcoWatch Brasil foi originalmente desenhado para operar com serviços gerenciados de ponta a ponta na Microsoft Azure. Contudo, a utilização de recursos de nuvem com provisionamento contínuo gerava custos fixos elevados (na faixa de **R$ 600 a R$ 900 mensais**), incompatíveis com o orçamento de projetos acadêmicos e com a cota limitada de $100 do programa *Azure for Students*.

Para contornar essa restrição sem sacrificar a robustez arquitetural (Event-Driven, CQRS, Apache Kafka, Redis, PostgreSQL e microsserviços reativos), foi implementada uma **Arquitetura Híbrida Cloud-Native Zero-Cost**, migrando serviços proprietários pagos para soluções gerenciadas em camadas gratuitas (Free Tier perpétuo) e orquestração sob demanda. O resultado foi a **redução dos custos operacionais para exatamente R$ 0,00/mês**.

### 1. A Troca do Apache Kafka: Azure Event Hubs ➔ Aiven Cloud (ou Kafka Gerenciado Gratuito)

* **O Cenário Anterior (Azure Event Hubs):**
  - No início, a telemetria climática (`weather-readings`) era transmitida via **Azure Event Hubs** habilitado com endpoint compatível com Kafka.
  - **O Problema de Custo:** Mesmo na camada mais baixa (*Basic/Standard*), o Event Hubs cobra por hora pelo provisionamento de **Throughput Units (TUs)** contínuas (~US$ 0,015 a US$ 0,03/hora = **~US$ 11 a US$ 22/mês por TU**, ou cerca de **R$ 60 a R$ 130/mês**). Essa cobrança é ininterrupta, ocorrendo mesmo na madrugada ou em momentos em que nenhuma mensagem está sendo trafegada.
* **A Solução Gratuita Adotada (Aiven for Apache Kafka):**
  - O barramento de eventos foi flexibilizado para suportar o **Aiven Cloud** (ou qualquer broker Kafka gerenciado gratuito com suporte a TLS).
  - O Aiven disponibiliza clusters gerenciados na nuvem com autenticação segura via **SASL_SSL** com mecanismo **SCRAM-SHA-256** e certificado de Autoridade Certificadora (`ca.pem`).
  - **Custo:** **R$ 0,00**.
* **Como o Código Suporta Ambos (`KafkaEventHubsConfig.java`):**
  - Os serviços `data-ingestion-service`, `alert-service` e `analytics-service` foram implementados com configuração inteligente e dinâmica:
    1. **Se `KAFKA_USERNAME` e `KAFKA_PASSWORD` estiverem presentes:** Ativa automaticamente o protocolo `SASL_SSL` com mecanismo `SCRAM-SHA-256` e carrega o certificado de autoridade `/certs/ca.pem` do classpath.
    2. **Se `EVENT_HUBS_CONNECTION_STRING` for configurada:** Opera em `SASL_SSL` com mecanismo `PLAIN` autenticando no Azure Event Hubs via `$ConnectionString`.
    3. **Se nenhuma credencial for informada (Ambiente Local):** Conecta via plaintext no Kafka local provisionado pelo Docker Compose.

---

### 2. A Troca do Banco de Dados: Azure Database for PostgreSQL ➔ Supabase

* **O Problema no Azure:** Provisionar uma instância gerenciada de Azure Database for PostgreSQL (Flexible Server, camada Burstable B1ms) custa entre **US$ 25 e US$ 45/mês (~R$ 140 a R$ 250/mês)**, além de exigir configuração de gateway ou IPs públicos autorizados.
* **A Solução Gratuita:** Migração para o **Supabase PostgreSQL 15**:
  - Armazenamento de 500 MB no Free Tier (mais que suficiente para históricos tabulares de alertas, previsões e notícias).
  - Pool de conexões embutido via **PgBouncer (Session Pooler)** na porta 5432 / 6543, fornecendo conectividade IPv4 estável diretamente aos containers da Azure.
  - Backups automáticos diários e painel de controle intuitivo.
  - **Custo:** **R$ 0,00**.

---

### 3. A Troca do Cache: Azure Cache for Redis ➔ Upstash Redis Serverless

* **O Problema no Azure:** O Azure Cache for Redis (Basic C0 de 250 MB) custa em torno de **US$ 16/mês (~R$ 90/mês)**, mesmo que seja feita apenas 1 leitura de cache por hora.
* **A Solução Gratuita:** Utilização do **Upstash Redis Serverless**:
  - Conexão TLS nativa suportada pelo Spring Data Redis através da URI `rediss://`.
  - Cota de **10.000 requisições diárias gratuitas**, suficiente para cachear as últimas leituras das 27 capitais com TTL de 5 minutos.
  - Elimina a necessidade de gerenciar nós, patches de segurança ou containers de Redis em produção.
  - **Custo:** **R$ 0,00**.

---

### 4. A Troca da Ingestão Contínua: Cron 24/7 em Container ➔ GitHub Actions + Scale-to-Zero

* **O Problema no Azure:** Inicialmente, o `data-ingestion-service` possuía uma anotação `@Scheduled` para rodar a cada hora. Para que o agendador funcionasse, o container no Azure Container Apps precisava permanecer com réplica mínima ligada (`minReplicas = 1`), consumindo vCPU e memória ininterruptamente (720 horas/mês).
* **A Solução Serverless:**
  - Foi criado o workflow `.github/workflows/scheduled-ingestion.yml` no **GitHub Actions**, disparado automaticamente 3 vezes ao dia (ou via `workflow_dispatch` sob demanda) de forma 100% gratuita nos runners do GitHub.
  - O workflow realiza uma chamada segura ao endpoint HTTP `POST /api/scheduler/trigger` do serviço de ingestão.
  - Isso permitiu habilitar a **Escala a Zero (`minReplicas = 0`)** nos Azure Container Apps: os containers só inicializam durante os segundos em que estão processando requisições ou ciclos de telemetria, entrando em hibernação logo em seguida.
  - O consumo mensal fica bem abaixo da cota gratuita da Azure (que concede 180.000 vCPU-segundos e 360.000 GiB-segundos gratuitos todo mês).
  - **Custo:** **R$ 0,00**.

---

### 5. Ingestão de Notícias de Desastres: RSS Governamentais Gratuitos com Sanitização Local

* **O Problema de Mercado:** Serviços comerciais de notícias (como NewsAPI ou feeds premium de agências) chegam a custar mais de **US$ 449/mês**, além de introduzirem ruído jornalístico irrelevante (política, celebridades, finanças).
* **A Solução Implementada:**
  - Ingestão direta e gratuita de **Feeds RSS públicos e oficiais** (Agência Brasil / EBC, G1 Meio Ambiente, Agência Gov).
  - Desenvolvimento de um **Filtro Sanitizador Semântico (`NewsClassifier`)** em Java puro no backend:
    - **Blacklist:** Rejeição estrita de matérias sobre eleições, urnas, 2º turno, candidatos, STF, mercado financeiro ou curiosidades de zoológico.
    - **Whitelist:** Exigência de termos específicos de desastres (queimada, fogo, cheia, enchente, temporal, dengue, desmatamento, seca).
    - **Classificação semântica automática:** categorização em 6 frentes: `FIRE`, `FLOOD`, `RAIN`, `DENGUE`, `DEFORESTATION` e `GENERAL`.
  - **Custo:** **R$ 0,00**.

---

### 📊 Comparativo Detalhado de Custos: Infraestrutura Azure vs Arquitetura Otimizada

| Componente | Abordagem Azure Convencional | Solução Híbrida Adotada no EcoWatch | Custo Estimado (Antes) | Custo Atual | Economia Mensal |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **Streaming (Kafka)** | Azure Event Hubs (1 TU contínua) | **Aiven Kafka Free Tier** (SASL_SSL SCRAM-SHA-256) | ~US$ 15,00 (~R$ 85,00) | **R$ 0,00** | 100% |
| **Banco Relacional** | Azure Database for PostgreSQL (B1ms) | **Supabase PostgreSQL 15** (PgBouncer IPv4) | ~US$ 30,00 (~R$ 170,00) | **R$ 0,00** | 100% |
| **Cache em Memória** | Azure Cache for Redis (Basic C0) | **Upstash Redis Serverless** (TLS nativo) | ~US$ 16,00 (~R$ 90,00) | **R$ 0,00** | 100% |
| **Agendamento Cron** | Container 24/7 ligado (minReplicas = 1) | **GitHub Actions Scheduler** + Scale-to-Zero | ~US$ 12,00 (~R$ 68,00) | **R$ 0,00** | 100% |
| **API de Notícias** | NewsAPI Developer / Pro Plan | **RSS Feeds Oficiais Gov/EBC** + Sanitizador Semântico | ~US$ 50,00 (~R$ 280,00) | **R$ 0,00** | 100% |
| **Computação App** | Azure Container Apps (Always-on) | **Azure Container Apps** (Scale-to-Zero dentro do Free Tier) | ~US$ 15,00 (~R$ 85,00) | **R$ 0,00** | 100% |
| **TOTAL ESTIMADO** | Infraestrutura Proprietária Contínua | **Arquitetura Híbrida Zero-Cost** | **~US$ 138,00 (~R$ 778,00/mês)** | **R$ 0,00 / mês** | **100% de Economia** |

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
| `GET` | `/api/dashboard/{municipio}` | Dados consolidados (alertas + previsões) da cidade |
| `GET` | `/api/v1/news` | Feed paginado de notícias oficiais de desastres (`page`, `size`, `categoria`, `estado`, `municipio`) |
| `POST` | `/api/v1/news/cleanup` | Dispara expurgo imediato de ruídos e reclassificação de notícias |
| `GET` | `/swagger-ui/index.html` | Swagger UI |
| `GET` | `/v3/api-docs` | Documentação OpenAPI |

> **Nota**: A busca por município é case-insensitive. Para cidades com espaço, use o nome completo: `rio de janeiro`. As categorias de notícias disponíveis são: `FIRE`, `FLOOD`, `RAIN`, `DENGUE`, `DEFORESTATION` e `GENERAL`.

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

| Recurso | Nome | Tipo / Provedor |
|---------|------|-----------------|
| Resource Group | `ecowatch-rg` | Azure Resource Group (`brazilsouth`) |
| Container Registry | `ecowatchacr4705` | Azure Container Registry (Basic) |
| Streaming (Kafka) | Aiven Cloud / Azure Event Hubs | Broker Gerenciado (SASL_SSL / SCRAM-SHA-256) |
| Container Apps Environment | `ecowatch-env` | Azure ACA Serverless (Scale-to-Zero) |
| RabbitMQ | `rabbitmq` | Container App (interno) |

### Variáveis de ambiente em produção

Na Azure, as credenciais são injetadas como **secrets** no Container App:

| Variável | Valor em Produção | Descrição |
|----------|-------------------|-----------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:5432/postgres` | Supabase PgBouncer Pooler (IPv4) |
| `SPRING_DATASOURCE_USERNAME` | `postgres.{PROJECT_REF}` | Usuário do banco Supabase |
| `SPRING_DATASOURCE_PASSWORD` | `(secret)` | Senha do banco de dados |
| `SPRING_REDIS_URL` | `rediss://default:{PASSWORD}@willing-amoeba-77167.upstash.io:6379` | Upstash Redis Serverless com TLS |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka-xxxxx.aivencloud.com:xxxxx` ou `ecowatchhub4705.servicebus.windows.net:9093` | Host e porta do broker Kafka |
| `KAFKA_USERNAME` | `avnadmin` (Aiven) | Usuário SCRAM-SHA-256 (se Aiven) |
| `KAFKA_PASSWORD` | `(secret)` | Senha SCRAM-SHA-256 (se Aiven) |
| `EVENT_HUBS_CONNECTION_STRING` | `(opcional)` | Connection string SASL PLAIN (se Azure Event Hubs) |
| `SPRING_RABBITMQ_HOST` | `rabbitmq` | DNS interno no Container Apps |

### Ingress

| Serviço | Ingress | Justificativa |
|---------|---------|---------------|
| `read-model-service` | **externo** | Única API exposta ao público (Dashboard e Notícias Oficiais) |
| `alert-service` | interno | Comunicação interna orientada a eventos |
| `analytics-service` | interno | Comunicação interna orientada a eventos |
| `notification-service` | interno | Comunicação interna orientada a eventos |
| `data-ingestion-service` | interno | Comunicação interna / acionado por agendador |

---

## 🛡 Infraestrutura Cloud

```
┌────────────────────────────────────────────────────────────────────────┐
│                   GitHub Actions (Cron Serverless)                     │
│                   Disparo 3x/dia ou sob demanda (R$ 0,00)               │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP POST /api/scheduler/trigger
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    Azure Container Apps (Serverless)                   │
│                    brazilsouth · Auto-Scale to Zero                    │
│                                                                        │
│  ┌─────────────────┐       ┌──────────────┐       ┌────────────┐       │
│  │ data-ingestion  │       │alert-service │       │ analytics  │       │
│  │ (interno)       │       │ (interno)    │       │ (interno)  │       │
│  └────────┬────────┘       └──────┬───────┘       └─────┬──────┘       │
│           │                       │                     │              │
│           └───────────────────────┼─────────────────────┘              │
│                                   │                                    │
│  ┌────────────────────────────────┴─────────────────────────────────┐  │
│  │ Apache Kafka (Aiven Cloud Free / SASL_SSL SCRAM-SHA-256 TLS)     │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ┌─────────────────┐       ┌────────────────────────────────────────┐  │
│  │ notification    │       │ read-model-service                     │  │
│  │ (interno)       │       │ (EXTERNO — Dashboard & Notícias)       │  │
│  └────────┬────────┘       └──────────────────┬─────────────────────┘  │
│           │                                   │                        │
│  ┌────────┴──────┐                            │                        │
│  │   RabbitMQ    │                            │ Feeds RSS Oficiais     │
│  │  (interno)    │                            │ (EBC / Gov / G1)       │
│  └───────────────┘                            ▼                        │
└────────────────────────────────────────────────────────────────────────┘
         │                          │                       │
         ▼                          ▼                       ▼
   ┌──────────┐              ┌────────────┐          ┌────────────┐
   │ Supabase │              │  Upstash   │          │   Gmail    │
   │PostgreSQL│              │   Redis    │          │   SMTP     │
   │ (AWS SP) │              │  (TLS)     │          │            │
   └──────────┘              └────────────┘          └────────────┘
```

---

## 🔒 Segurança

### Implementado
- **Rate Limiting**: Algoritmo Token Bucket via **Bucket4j** (30 req/min por IP) protegendo todos os endpoints `/api/**` contra ataques de força bruta e DoS
- **CORS**: `@CrossOrigin(origins = "*")` no `DashboardController` (único endpoint público)
- **Validação de input**: payload JSON validado contra o schema `WeatherReading` antes de enviar ao Kafka — retorna 400 para dados malformados
- **Guarda de concorrência**: endpoint `/api/scheduler/trigger` usa `AtomicBoolean` para impedir execuções duplicadas — retorna 429 se coleta já estiver em andamento
- **Segredos fora do código**: todas as credenciais via variáveis de ambiente (`.env` no `.gitignore`)
- **Ingress restrito**: apenas `read-model-service` com acesso externo; demais serviços são internos

### Planejado
- **Autenticação JWT**: Spring Security + Supabase Auth para proteger rotas administrativas
- **TLS end-to-end**: HTTPS no Azure Container Apps (automático via domínio `azurecontainerapps.io`)

---

## 🌍 Cidades Monitoradas

O sistema monitora **todas as 27 capitais brasileiras** (26 estados + Distrito Federal), garantindo 100% de cobertura federativa em todos os 6 biomas do território nacional:

| Região | Capitais Monitoradas | Biomas Abrangidos |
|:---|:---|:---|
| **Norte** (7) | Manaus (AM), Belém (PA), Porto Velho (RO), Rio Branco (AC), Macapá (AP), Boa Vista (RR), Palmas (TO) | Amazônia, Cerrado |
| **Nordeste** (9) | Salvador (BA), Recife (PE), Fortaleza (CE), São Luís (MA), Natal (RN), João Pessoa (PB), Maceió (AL), Teresina (PI), Aracaju (SE) | Caatinga, Mata Atlântica, Cerrado |
| **Centro-Oeste** (4) | Brasília (DF), Goiânia (GO), Cuiabá (MT), Campo Grande (MS) | Cerrado, Pantanal |
| **Sudeste** (4) | São Paulo (SP), Rio de Janeiro (RJ), Belo Horizonte (MG), Vitória (ES) | Mata Atlântica, Cerrado |
| **Sul** (3) | Curitiba (PR), Porto Alegre (RS), Florianópolis (SC) | Mata Atlântica, Pampa |

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
├── .github/workflows/              # Automações CI/CD e Agendador Serverless
│   └── scheduled-ingestion.yml     # Cron 3x/dia sob demanda no GitHub Actions (R$ 0,00)
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
├── read-model-service/             # API do Dashboard e Notícias (CQRS - leitura)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── controller/
│       │   ├── DashboardController.java
│       │   └── NewsController.java
│       ├── model/
│       │   ├── NoticiaAmbiental.java
│       │   └── NewsCategory.java
│       └── service/
│           ├── NewsClassifier.java
│           └── NewsIngestionService.java
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

### Tabela `noticias_ambientais`

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | `BIGSERIAL` (PK) | Identificador auto-incremento |
| `titulo` | `VARCHAR(500)` | Título sanitizado da matéria |
| `resumo` | `TEXT` | Resumo cidadão da ocorrência (máx 2 linhas) |
| `fonte` | `VARCHAR(100)` | Agência oficial (ex: 'Agência Brasil', 'G1 Meio Ambiente') |
| `url` | `VARCHAR(1000)` (UNIQUE) | Link canônico para a matéria original |
| `data_hora` | `TIMESTAMP` | Data/hora de publicação oficial (UTC) |
| `categoria` | `VARCHAR(20)` | `FIRE`, `FLOOD`, `RAIN`, `DENGUE`, `DEFORESTATION`, `GENERAL` |
| `municipio` | `VARCHAR(100)` | Município identificado (ou null se regional) |
| `estado` | `VARCHAR(2)` | Sigla UF identificada (ou null se nacional) |
| `created_at` | `TIMESTAMP` | Data/hora da ingestão no sistema |

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
