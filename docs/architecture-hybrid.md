# 🏗️ Arquitetura Híbrida — Edge-First + FinOps

## Visão Geral

O **EcoWatch Brasil** adota uma arquitetura híbrida que combina processamento **Edge-First** no frontend com um **pipeline assíncrono** no backend. Essa estratégia otimiza custos na nuvem (FinOps) enquanto garante alta performance e dados em tempo real.

```
┌─────────────────────────────────────────────────────────────────┐
│                        FRONTEND (Edge)                         │
│                                                                │
│  Next.js / React  ─── Open-Meteo API (direto, sem backend)    │
│       │                  ↑ Telemetria em tempo real            │
│       │                  ↑ Mapas, gráficos, camadas visuais   │
│       │                                                        │
│       └─── Backend API ─── Alertas, Dashboard, Relatórios     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  BACKEND (Autoridade Central)                   │
│                                                                │
│  ┌──────────────┐    Kafka     ┌───────────────┐               │
│  │   data-      │──────────>  │  alert-       │               │
│  │   ingestion  │  weather-   │  service      │               │
│  │   service    │  readings   │               │               │
│  └──────────────┘             └───┬───┬───┬───┘               │
│                                   │   │   │                    │
│                        ┌──────────┘   │   └──────────┐        │
│                        ▼              ▼              ▼        │
│                   PostgreSQL    Upstash Redis    RabbitMQ      │
│                   (Supabase)    (Cache 3h TTL)   (Eventos)    │
│                                                      │        │
│                                                      ▼        │
│                                            notification-      │
│                                            service            │
│                                            (Email/Push)       │
└─────────────────────────────────────────────────────────────────┘
```

## Princípios

### 1. Edge-First — Frontend como consumidor direto
O frontend consome a **API Open-Meteo** diretamente para:
- Temperatura, umidade e precipitação em tempo real
- Previsão horária e diária (7 dias)
- Qualidade do ar (PM2.5, PM10, O₃, NO₂)
- Camadas de mapa e visualizações interativas

**Vantagem:** Zero custo de backend para visualização de telemetria. O Open-Meteo é gratuito e não requer API key.

### 2. Backend como Autoridade Central de Negócio
O backend é responsável exclusivamente por:
- **Processamento de regras de risco** — 9 políticas de alerta avaliadas via pipeline Kafka
- **Persistência histórica** — alertas e previsões no PostgreSQL (Supabase)
- **Cache de alta performance** — última leitura por cidade no Upstash Redis (TTL 3h)
- **Auditoria** — registro completo de todos os eventos processados
- **Notificações** — alertas críticos via email (SMTP) e push (Firebase)

### 3. FinOps — Consumo inteligente de créditos
- **Modo 100% sob demanda** — scheduler desativado por padrão (`SCHEDULER_ENABLED=false`)
- **Scale-to-zero** — Container Apps configurados com `min-replicas: 0`
- **Coleta seletiva** — `POST /api/scheduler/trigger/{cidade}` coleta apenas uma cidade
- **Cache agressivo** — TTL de 3 horas no Redis reduz queries ao PostgreSQL
- **Rate limiting** — 30 req/min por IP previne abusos e custos inesperados

## Pipeline de Dados

### Fluxo sob demanda (trigger manual)

```
1. Frontend/Operador chama POST /api/scheduler/trigger/{cidade}
          │
          ▼
2. data-ingestion-service:
   ├── Coleta NASA Power API (radiação solar, vento)
   ├── Fallback: Open-Meteo (temperatura, umidade, precipitação)
   ├── Enriquece com INPE (focos de incêndio ativos)
   ├── Enriquece com ANA (nível de rios)
   ├── Enriquece com CEMADEN (riscos de enchente)
   └── Publica JSON no tópico Kafka "weather-readings"
          │
          ▼
3. alert-service (@KafkaListener):
   ├── Deserializa WeatherReading
   ├── Normaliza nome da cidade
   ├── Salva previsão na tabela "previsoes" (PostgreSQL)
   ├── Avalia 9 políticas de alerta:
   │   ├── ExtremeHeatPolicy (>40°C ou >35°C + umidade <20%)
   │   ├── ExtremeRainPolicy (>50mm)
   │   ├── HeavyRainPolicy (>30mm)
   │   ├── DroughtRiskPolicy (precipitação <1mm por período)
   │   ├── FireRiskPolicy (temp >30°C + umidade <30%)
   │   ├── ForestFirePolicy (focos INPE ativos)
   │   ├── AirQualityPolicy (PM2.5 ou PM10 acima do limiar)
   │   ├── CemadenFloodPolicy (alerta CEMADEN ativo)
   │   └── RiverFloodPolicy (nível do rio acima do limiar ANA)
   ├── Salva alertas na tabela "alertas_gerados" (PostgreSQL)
   ├── Publica alertas no RabbitMQ (exchange: alert-exchange)
   └── Salva última leitura no Redis (chave: weather:{cidade}, TTL: 3h)
          │
          ▼
4. notification-service (RabbitMQ listener):
   ├── Envia email via SMTP (Gmail)
   └── Push notification via Firebase (opcional, desativado por padrão)
```

## Endpoints da API

### read-model-service (porta 8086) — ÚNICO serviço com ingress externo

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/dashboard/{municipio}` | Dashboard consolidado (10 alertas + 3 dias de previsão) |
| `GET` | `/api/dashboard/alerts/all` | Últimos 50 alertas nacionais (todas as cidades) |

### data-ingestion-service (porta 8083) — ingress interno

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/scheduler/status` | Status do agendador e contadores |
| `POST` | `/api/scheduler/trigger` | Coleta manual de todas as cidades |
| `POST` | `/api/scheduler/trigger/{cidade}` | Coleta sob demanda de uma cidade |

### alert-service (porta 8081) — ingress interno

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/cache/{municipio}` | Última leitura do cache Redis |

## Infraestrutura

| Componente | Serviço | Localização |
|------------|---------|-------------|
| Compute | Azure Container Apps | Brazil South |
| Banco de Dados | Supabase (PostgreSQL) | SA-East-1 (AWS) |
| Cache | Upstash Redis (TLS) | Global Edge |
| Mensageria (Streaming) | Azure Event Hubs (Kafka) | Brazil South |
| Mensageria (Queue) | RabbitMQ (Container App) | Brazil South |
| Container Registry | Azure Container Registry | Brazil South |

## Capitais Monitoradas (27)

| # | Capital | UF | Região |
|---|---------|----|----|
| 1 | Brasília | DF | Centro-Oeste |
| 2 | Rio de Janeiro | RJ | Sudeste |
| 3 | São Paulo | SP | Sudeste |
| 4 | Salvador | BA | Nordeste |
| 5 | Recife | PE | Nordeste |
| 6 | Porto Alegre | RS | Sul |
| 7 | Curitiba | PR | Sul |
| 8 | Belo Horizonte | MG | Sudeste |
| 9 | Fortaleza | CE | Nordeste |
| 10 | Manaus | AM | Norte |
| 11 | Belém | PA | Norte |
| 12 | Goiânia | GO | Centro-Oeste |
| 13 | São Luís | MA | Nordeste |
| 14 | Maceió | AL | Nordeste |
| 15 | Teresina | PI | Nordeste |
| 16 | João Pessoa | PB | Nordeste |
| 17 | Natal | RN | Nordeste |
| 18 | Aracaju | SE | Nordeste |
| 19 | Campo Grande | MS | Centro-Oeste |
| 20 | Cuiabá | MT | Centro-Oeste |
| 21 | Florianópolis | SC | Sul |
| 22 | Vitória | ES | Sudeste |
| 23 | Palmas | TO | Norte |
| 24 | Macapá | AP | Norte |
| 25 | Boa Vista | RR | Norte |
| 26 | Rio Branco | AC | Norte |
| 27 | Porto Velho | RO | Norte |
