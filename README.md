
# TradePilot — Agentic Trading Platform

A multi-module Spring Boot platform that runs **autonomous AI trading agents** backed by Claude or Grok LLMs, with full multi-exchange support for cryptocurrency futures markets.

**Safe defaults are enabled out of the box**: the default exchange provider is **paper**, autonomous order placement stays in **dry-run**, and **mainnet trading is disabled** until explicitly enabled.

## Key Features

### Agentic AI Core
- **Autonomous Agents**: `LLMTradingAgent` and `TechnicalTradingAgent` implement a reactive decision loop (`onKlineClosed → AgentDecision → OrderExecutionGateway`).
- **Agent Lifecycle Management**: Agents transition through IDLE → RUNNING → PAUSED → STOPPED via `AgentManager`; state is persisted and reloaded on restart.
- **Dual LLM Support**: **Claude (Anthropic)** is the default provider; **Grok (xAI)** is available as an alternative. Provider is switchable via `agent.llm.provider`.
- **RAG Architecture**: Trade memories are embedded and stored in **Pinecone** vector DB; relevant context is retrieved per decision cycle.
- **LangChain4j Integration**: Tool-use and chat-memory plumbing via `LangChain4jStrategy`.

### Multi-Exchange Support
- **Bybit Futures** (✅ Recommended for Testing): Full V5 API, real execution, testnet-ready.
- **Binance Futures**: Mainnet support.
- **dYdX v4**: Market data; order execution is mocked (safe for navigation).
- **Hyperliquid** (✅ New): Full REST + WebSocket market data and **EIP-712 order signing** for live DEX execution. Testnet flag: `exchange.hyperliquid.use-testnet=true`.
- **Paper Trading**: Fully simulated fills, margin, and position changes — the default.

### Architecture
- **Multi-Module**: `gateway/` (Spring Cloud Gateway, :8080) + `backend-core/` (Spring Boot, :8081 / gRPC :9090).
- **Event-Driven**: Apache Kafka for signals, executions, risk events, and market data.
- **Resilience**: Circuit breakers and rate limiters via Resilience4j on all exchange interactions.
- **Auth**: JWT authentication and refresh-token rotation; rate limiting via Bucket4j + Redis.

---

## Project Status

| Component | Status | Notes |
|-----------|--------|-------|
| **Bybit Integration** | 🟢 Ready | Mainnet & Testnet V5 API. |
| **Binance Integration** | 🟡 Mainnet Only | Hardcoded to Mainnet URLs. |
| **dYdX Integration** | 🟠 Partial | Market data only; trade execution is mocked. |
| **Hyperliquid Integration** | 🟢 Ready | REST + WebSocket + EIP-712 signing. Defaults to testnet. |
| **LLM Agents (Claude)** | 🟢 Default | Claude Sonnet is the active provider. |
| **LLM Agents (Grok)** | 🟡 Disabled | Available; set `agent.llm.provider=grok` and supply `GROK_API_KEY`. |
| **RAG / Memory** | 🟢 Beta | Pinecone vector DB integration implemented. |
| **Backtesting** | 🟢 Ready | CSV-driven backtest loop with metrics and equity curve export. |

---

## Quick Start (Safe Local Mode)

The default runtime is intentionally conservative:

- `server.port=8081`
- `trading.execution.mode=paper`
- `trading.exchange.provider=paper`
- `rag.order.dry-run=true`
- `trading.live.enabled=false`

### 1. Prerequisites
- Java 21 LTS
- Docker & Docker Compose

### 2. Configuration
Copy `.env.example` to `.env`:

```properties
SERVER_PORT=8081
GATEWAY_PORT=8080
TRADING_EXECUTION_MODE=paper
TRADING_EXCHANGE_PROVIDER=paper
TRADING_LIVE_ENABLED=false
JWT_SECRET=your-secret-here
ANTHROPIC_API_KEY=your-claude-key
```

### 3. Run Dependencies + Services
```bash
docker-compose up -d
```

This starts: `tradepilot-gateway` (:8080), `tradepilot-core` (:8081), PostgreSQL (:5432), Redis (:6379), Kafka (:9092), Zookeeper (:2181).

### 4. Start the Backend Standalone (alternative)
```bash
./gradlew :backend-core:bootRun
```

### 5. Access

- API Gateway: `http://localhost:8080`
- Backend direct: `http://localhost:8081`
- OpenAPI / Swagger UI: `http://localhost:8081/swagger-ui.html`
- Agent API: `/api/v1/agents`
- Bot operational API: `/api/v1/bots`

### Bybit Testnet Execution

```properties
SPRING_PROFILES_ACTIVE=live
TRADING_EXECUTION_MODE=live
TRADING_EXCHANGE_PROVIDER=bybit
TRADING_BYBIT_DOMAIN=TESTNET_DOMAIN
TRADING_BYBIT_API_KEY=YOUR_TESTNET_KEY
TRADING_BYBIT_API_SECRET=YOUR_TESTNET_SECRET
```

### Mainnet Execution (Explicit Opt-In Only)

All of the following must be set:

```properties
SPRING_PROFILES_ACTIVE=prod,live
TRADING_EXECUTION_MODE=live
TRADING_EXCHANGE_PROVIDER=binance   # or bybit
TRADING_BYBIT_DOMAIN=MAINNET_DOMAIN # only when provider=bybit
TRADING_LIVE_ENABLED=true
TRADING_BINANCE_API_KEY=...
TRADING_BINANCE_API_SECRET=...
```

If `TRADING_LIVE_ENABLED=true` is not set, mainnet providers are rejected during startup.

---

## API Reference

### Agent API (`/api/v1/agents`)

Agents are the primary abstraction. Create, configure, and lifecycle-manage autonomous trading agents.

```http
POST   /api/v1/agents              # Create a new agent
GET    /api/v1/agents              # List all agents (paginated)
GET    /api/v1/agents/{id}         # Get agent details
GET    /api/v1/agents/{id}/performance  # Get performance metrics
POST   /api/v1/agents/{id}/activate    # Start the agent (IDLE → RUNNING)
POST   /api/v1/agents/{id}/pause       # Pause the agent (RUNNING → PAUSED)
DELETE /api/v1/agents/{id}             # Delete the agent
```

### Bot Operational API (`/api/v1/bots`)

Legacy API surface for lower-level bot operations (start, stop, configure, leverage, sentiment). Routes through to agent-layer internals.

```http
POST   /api/v1/bots                        # Create
POST   /api/v1/bots/{botId}/start          # Start
PUT    /api/v1/bots/{botId}/stop           # Stop
GET    /api/v1/bots/{botId}/status         # Status
POST   /api/v1/bots/{botId}/configure      # Configure
POST   /api/v1/bots/{botId}/leverage       # Set leverage
POST   /api/v1/bots/{botId}/sentiment      # Set sentiment
GET    /api/v1/bots                        # List all
DELETE /api/v1/bots/{botId}               # Delete
```

### Orders API (`/api/v1/orders`)

```http
POST   /api/v1/orders              # Create order
GET    /api/v1/orders              # List orders
GET    /api/v1/orders/{id}         # Get order
PUT    /api/v1/orders/{id}         # Update order
DELETE /api/v1/orders/{id}         # Cancel order
```

---

## Testing

### Unit & Integration Tests
```bash
./gradlew :backend-core:test
```

### Gateway Tests
```bash
./gradlew :gateway:test
```

### HTTP Scripts
The `api-tests/` directory contains HTTP scripts for direct endpoint testing:
- `api-tests/backend-api-test.sh`
- `api-tests/agent-api-tests.http`

---

## Architecture Overview

### Package & Component Mind Map

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#764ABC', 'primaryTextColor': '#ffffff', 'primaryBorderColor': '#5a3a8a', 'lineColor': '#9b6fd4', 'secondaryColor': '#9b6fd4', 'tertiaryColor': '#f0e6ff', 'mainBkg': '#764ABC', 'nodeBorder': '#5a3a8a', 'titleColor': '#764ABC'}}}%%
mindmap
  root((TradePilot))
    agent
      domain
        Agent aggregate
        AgentDecision record
        OrderExecutionGateway
        RiskGuard
        AgentRepository
      application
        AgentOrchestrator
        AgentService
        TradeExecutionService
        PerformanceTrackingService
        AgentManager
        DeadLetterConsumer
        TradeReflectionListener
      strategy
        AgentStrategy
        LangChain4jStrategy
      impl
        LLMTradingAgent
        TechnicalTradingAgent
        LiveOrderGateway
        PaperTradingOrderGateway
        BacktestOrderGateway
        DefaultRiskGuard
      infrastructure
        AgentRepositoryImpl
        GrokClient
        ClaudeClient
        CachedGrokService
        CachedMemoryStore
        PineconeMemoryStore
      api
        AgentController
        OrderController
        MapStruct Mappers
      config
        OrderExecutionGatewayRegistry
        ExchangeServiceRegistry
        LangChain4jConfig
        AgentProperties
    bot
      service
        FuturesExchangeService
        BinanceFuturesService
        BybitFuturesService
        DydxFuturesService
        HyperliquidFuturesService
        PaperFuturesExchangeService
        BotCacheService
      backtest
        BacktestService
        CsvBacktestAgentExecutionService
        BacktestExchangeService
        HistoricalDataLoader
        StandardBacktestMetricsCalculator
        EquityCurveExportService
        BacktestRunRegistry
      strategy
        RSI Indicator
        MACD Indicator
        BollingerBands Indicator
        RSIExit
        MACDExit
        TrailingStop
        SentimentAnalyzer
      messaging
        EventPublisher
        EventConsumer
        EventPersistenceService
        RiskAssessmentService
        EventDrivenTrailingStopTracker
      grpc
        BotManagementServiceImpl
      metrics
        TradingMetrics
    security
      controller
        AuthController
      service
        AuthService
        JwtService
        RefreshTokenService
        CustomUserDetailsService
      filter
        JwtAuthenticationFilter
        AuthRateLimitFilter
      entity
        User
        RefreshToken
    domain
      KlineClosedEvent
      StreamMarketDataEvent
      BookTickerPayload
      MarketEvent
    infrastructure
      BinanceWebSocketAdapter
      BybitWebSocketAdapter
      HyperliquidWebSocketAdapter
      WebSocketMarketDataService
      MarketDataSanitizer
    config
      AsyncConfig
      KafkaConfig
      RedisConfig
      ResilienceConfig
      Bucket4jConfig
      ExchangeServiceConfig
      OpenApiConfig
```

### Module Layout

```
tradepilot/
├── gateway/          Spring Cloud Gateway — JWT auth, CORS, routing (:8080)
└── backend-core/     Trading platform — agents, orders, exchange, events (:8081 / gRPC :9090)
```

### High-Level Architecture

```mermaid
graph TB
    subgraph External
        BROWSER[Browser / REST Client]
        BINE[Binance API]
        BYBE[Bybit API]
        DYDE[dYdX API]
        HLE[Hyperliquid API]
        CLAUDE[Claude API]
        GROK[Grok API]
        PINECONE[Pinecone Vector DB]
    end

    subgraph Platform["TradePilot Platform"]
        GW["Spring Cloud Gateway\n:8080\nJWT validation + routing"]

        subgraph Core["backend-core :8081"]
            subgraph Agents["Agent Layer"]
                AM[AgentManager\nlifecycle]
                AO[AgentOrchestrator\ndecision loop]
                LTA[LLMTradingAgent]
                TTA[TechnicalTradingAgent]
            end

            subgraph Execution["Execution Layer"]
                LOG[LiveOrderGateway]
                POG[PaperTradingOrderGateway]
                BOG[BacktestOrderGateway]
            end

            subgraph Exchange["Exchange Layer"]
                BIN[BinanceFuturesService]
                BYB[BybitFuturesService]
                HL[HyperliquidFuturesService\nEIP-712 signing]
                PAPER[PaperFuturesExchangeService]
            end

            subgraph Infra["Infrastructure"]
                PG[(PostgreSQL)]
                REDIS[(Redis)]
                KAFKA[Kafka]
                CC[ClaudeClient]
                GC[GrokClient]
                PIN[PineconeMemoryStore]
            end
        end
    end

    BROWSER -->|HTTPS| GW
    GW -->|JWT validated| Core

    AM --> AO
    AO --> LTA & TTA
    LTA & TTA --> LOG & POG & BOG
    LOG --> BIN & BYB & HL
    POG --> PAPER

    LTA -->|reasoning| CC & GC
    CC -->|HTTPS| CLAUDE
    GC -->|HTTPS| GROK
    LTA -->|memory| PIN
    PIN -->|HTTPS| PINECONE

    BIN -->|REST+WS| BINE
    BYB -->|REST+WS| BYBE
    HL -->|REST+WS| HLE

    Core --> PG & REDIS & KAFKA
```

### Agent Lifecycle

```mermaid
stateDiagram-v2
    [*] --> IDLE : create
    IDLE --> RUNNING : activate
    RUNNING --> PAUSED : pause
    PAUSED --> RUNNING : activate
    RUNNING --> STOPPED : stop / delete
    PAUSED --> STOPPED : stop / delete
    STOPPED --> [*]
```

---

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8081` | Backend HTTP port |
| `trading.execution.mode` | `paper` | Execution gateway (`paper`, `live`) |
| `trading.exchange.provider` | `paper` | Exchange adapter (`paper`, `bybit`, `binance`, `hyperliquid`) |
| `trading.live.enabled` | `false` | Required before any mainnet exchange access |
| `trading.bybit.domain` | `TESTNET_DOMAIN` | Bybit environment |
| `agent.llm.provider` | `claude` | Active LLM (`claude`, `grok`) |
| `agent.llm.claude.model` | `claude-sonnet-4-6` | Claude model ID |
| `agent.llm.claude.enabled` | `true` | Enable Claude provider |
| `agent.llm.grok.enabled` | `false` | Enable Grok provider |
| `agent.llm.cache.enabled` | `false` | Cache LLM responses (dev/backtest) |
| `agent.orchestrator.enabled` | `true` | Enable decision loop |
| `agent.orchestrator.loop-interval` | `30000` | Loop interval ms |
| `agent.strategy` | `none` | Strategy override (`none`, `langchain4j`) |
| `exchange.hyperliquid.use-testnet` | `true` | Hyperliquid testnet flag |
| `rag.enabled` | `true` | Enable RAG memory pipeline |
| `rag.order.dry-run` | `true` | Log AI orders without sending to exchange |
| `rag.order.max-position-size-percent` | `10` | Max LLM-driven position size % |
| `rag.order.default-leverage` | `1` | Default leverage for AI orders |
| `rag.embedding.provider` | `openai` | Embedding provider (`openai`, `grok`, `local`) |
| `rag.vector-db.provider` | `pinecone` | Vector DB provider |

---

## Hard Risk Limits

- **Paper-first default**: new agents run in paper mode unless explicitly switched.
- **Dry-run AI execution**: `rag.order.dry-run=true` by default.
- **Position-size cap**: `rag.order.max-position-size-percent=10`.
- **Default leverage**: `rag.order.default-leverage=1`.
- **Live gateway guardrails**: minimum margin balance enforced; bracket exits auto-placed (2% SL / 5% TP).
- **Mainnet lock**: mainnet providers are blocked unless `trading.live.enabled=true`.

### Restart Behavior

Agent definitions are persisted. Agents previously `RUNNING` are restarted by `AgentManager` on startup. Exchange-side position reconciliation is still manual — verify open positions before re-enabling non-paper execution after an unclean shutdown.

---

## Deployment

### Docker Compose

```bash
docker-compose up -d
```

Starts all services: gateway (:8080), backend-core (:8081 / gRPC :9090), PostgreSQL, Redis, Kafka, Zookeeper.

### Kubernetes

```bash
kubectl apply -f redis-deployment.yaml
kubectl apply -f redis-service.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

```bash
kubectl port-forward service/simple-trading-bot 8081:8081
```

### Docker Image Build (Multi-Stage)

```bash
docker build -t tradepilot-core ./backend-core
```

---

## Documentation

<details>
<summary><strong>1. High-Level System Overview</strong></summary>

```mermaid
graph TB
    classDef backtest fill:#fff3e0,stroke:#e65100,color:#3e2723

    subgraph Clients["External Clients"]
        UI[Web / Mobile UI]
        GRPC[gRPC Client]
    end

    subgraph TradePilot["TradePilot — backend-core"]
        subgraph API["API Layer"]
            AC[AgentController]
            OC[OrderController]
            AUTH[AuthController]
            EDC[EventDrivenTradingController]
            BTC[BacktestController]:::backtest
        end

        subgraph Core["Agent Core"]
            AO[AgentOrchestrator]
            AS[AgentService]
            TES[TradeExecutionService]
            PTS[PerformanceTrackingService]
            AM[AgentManager]
            CMS[CachedMemoryStore]
        end

        subgraph Execution["Execution Layer"]
            GR[OrderExecutionGatewayRegistry]
            LGW[LiveOrderGateway]
            PGW[PaperTradingOrderGateway]
            BGW[BacktestOrderGateway]:::backtest
        end

        subgraph Strategy["Strategy & LLM"]
            LC4J[LangChain4jStrategy]
            LLM["LLMProvider\nGrok or Claude"]
            CGS["CachedGrokService\nLLM response cache"]:::backtest
        end

        subgraph BotDomain["Bot Domain"]
            subgraph MarketData["Market Data"]
                WS[WebSocketMarketDataService]
                BWS[BinanceWebSocketAdapter]
                BYWWS[BybitWebSocketAdapter]
                HLWS[HyperliquidWebSocketAdapter]
            end

            subgraph ExchangeServices["Exchange Services"]
                ESR[ExchangeServiceRegistry]
                BIN[BinanceFuturesService]
                BYB[BybitFuturesService]
                DYD[DydxFuturesService]
                HL[HyperliquidFuturesService]
                PAP[PaperFuturesExchangeService]
                BEXS[BacktestExchangeService]:::backtest
            end

            subgraph Messaging["Event Bus — Kafka"]
                EP[EventPublisher]
                EC[EventConsumer]
                EPS[EventPersistenceService]
            end

            subgraph BacktestDomain["Backtest"]
                BS[BacktestService]:::backtest
                CBAES[CsvBacktestAgentExecutionService]:::backtest
                HDL[HistoricalDataLoader]:::backtest
                BRR[BacktestRunRegistry]:::backtest
                SBMC[StandardBacktestMetricsCalculator]:::backtest
                ECES[EquityCurveExportService]:::backtest
            end

            BCS[BotCacheService]
            GRPCSVC["BotManagementServiceImpl\ngRPC"]
        end

        subgraph Security["Security"]
            JF[JwtAuthenticationFilter]
            JS[JwtService]
            AUTHS[AuthService]
        end

        subgraph Persistence["Persistence"]
            DB[(PostgreSQL)]
            CACHE[(Redis)]
            AGENTREPO[AgentRepository]
            ORDERREPO[OrderRepository]
        end

        subgraph Infra["Infrastructure"]
            KAFKA[(Kafka)]
            TM["TradingMetrics\nPrometheus/Micrometer"]
        end
    end

    subgraph Exchanges["External Exchanges"]
        BINE[Binance Futures]
        BYBE[Bybit Futures]
        DYDE[dYdX v4]
        HLE[Hyperliquid]
        CSV[(CSV Files)]:::backtest
    end

    UI -->|REST / JWT| API
    GRPC -->|gRPC| GRPCSVC

    AUTH --> AUTHS --> JS
    JF --> JS

    AC --> AS --> AGENTREPO --> DB
    AC --> AO
    OC --> ORDERREPO
    AM -->|loads on startup| AO
    AM --> AGENTREPO

    AO -->|sense| WS
    BWS & BYWWS & HLWS --> WS
    AO -->|think| LC4J --> LLM
    AO -->|act| GR
    GR --> LGW & PGW & BGW

    LGW --> ESR --> BIN & BYB & DYD & HL & PAP
    PGW --> PAP
    BIN --> BINE
    BYB --> BYBE
    DYD --> DYDE
    HL --> HLE

    BGW --> BEXS

    BTC --> BS
    BTC -->|query runs| BRR
    BTC -->|download CSV| ECES
    BS --> HDL
    HDL --> CSV & DB
    BS --> CBAES
    BS -->|create with latency/slippage/fee| BEXS
    CBAES -->|per candle via LLMTradingAgent| CGS
    CBAES -->|setMarketContext per candle| BEXS
    CBAES --> BGW
    BGW -->|place order| BEXS
    BEXS -->|OrderResult| BGW
    CBAES -->|ExecutionResult| SBMC
    SBMC -->|BacktestMetrics| BS
    BS -->|save| BRR

    AO --> TES --> ORDERREPO --> DB
    TES --> PTS --> DB
    GRPCSVC --> BCS
    TES --> EP --> KAFKA
    KAFKA --> EC
    EC --> EPS --> DB

    AS -->|cache agentsByOwner| CACHE
    BCS -->|BotState| CACHE
    CGS -->|LLM response cache| CACHE
    CMS -->|trade memory| CACHE
    PTS --> DB
    AO -->|record order latency| TM
```

</details>

<details>
<summary><strong>2. Agent Domain — DDD Layers</strong></summary>

```mermaid
graph TB
    subgraph API["API Layer — agent.api"]
        AC[AgentController]
        OC[OrderController]
        DTO["CreateAgentRequest\nAgentResponse\nPerformanceResponse"]
        MAP["AgentMapper\nMapStruct"]
        AC & OC --> DTO
        DTO --> MAP
    end

    subgraph AppLayer["Application Layer — agent.application"]
        AS[AgentService]
        AO[AgentOrchestrator]
        TES[TradeExecutionService]
        OS[OrderService]
        PTS[PerformanceTrackingService]
        TRL[TradeReflectionListener]
        DLC[DeadLetterConsumer]
        EVT["Domain Events\nAgentStarted/Paused/Stopped\nTradeCompleted"]
        AS & AO & TES --> EVT
    end

    subgraph Strategy["Strategy — agent.application.strategy"]
        STRAT["AgentStrategy\ninterface"]
        LC4J[LangChain4jStrategy]
        STRAT --> LC4J
    end

    subgraph Domain["Domain Layer — agent.domain"]
        AG["Agent\nAggregate Root"]
        AID["AgentId\nValue Object"]
        AGS["AgentStatus / AgentGoal"]
        OEG["OrderExecutionGateway\ninterface"]
        RG["RiskGuard\ninterface"]
        AGENTREPO_I["AgentRepository\ninterface"]
        AG --> AID & AGS
    end

    subgraph Impl["Implementation — agent.impl"]
        RTA["ReactiveTradingAgent\ninterface"]
        LTA[LLMTradingAgent]
        TTA[TechnicalTradingAgent]
        DRG[DefaultRiskGuard]
        LGW[LiveOrderGateway]
        PGW[PaperTradingOrderGateway]
        BGW[BacktestOrderGateway]
        LTA & TTA -.->|implements| RTA
        DRG -.->|implements| RG
        LGW & PGW & BGW -.->|implements| OEG
    end

    subgraph Infra["Infrastructure — agent.infrastructure"]
        AGENTREPO_IMPL[AgentRepositoryImpl]
        JPA["JpaAgentRepository\nSpring Data"]
        LLMPROV["LLMProvider\ninterface"]
        GROK[GrokClient]
        CLAUDE[ClaudeClient]
        CGROK["CachedGrokService\nbacktest cache"]
        AGENTREPO_IMPL --> JPA
        GROK & CLAUDE & CGROK -.->|implements| LLMPROV
    end

    subgraph Config["Config — agent.config"]
        ESR[ExchangeServiceRegistry]
        OGR[OrderExecutionGatewayRegistry]
        FACTORIES["Exchange Factories\nBinance/Bybit/Dydx/Paper/HL"]
        ESR --> FACTORIES
        OGR --> LGW & PGW & BGW
    end

    API --> AppLayer
    AppLayer --> Domain
    AppLayer --> Strategy
    Strategy --> Impl
    Impl --> Infra
    Infra -.->|implements| AGENTREPO_I
    Config --> AppLayer
```

</details>

<details>
<summary><strong>3. Order Execution Pipeline</strong></summary>

```mermaid
graph LR
    classDef backtest fill:#fff3e0,stroke:#e65100,color:#3e2723

    AO[AgentOrchestrator] -->|1 resolve| OGR[OrderExecutionGatewayRegistry]
    OGR -->|2 returns| GW

    subgraph GW["OrderExecutionGateway (interface)"]
        execute["execute(decision, symbol, price)"]
    end

    subgraph Gateways["Concrete Gateways"]
        LGW["LiveOrderGateway\nLive exchange"]
        PGW["PaperTradingOrderGateway\nSimulated fills"]
        BGW["BacktestOrderGateway\nHistorical replay"]:::backtest
    end

    GW -->|3 dispatch| LGW & PGW & BGW

    subgraph PositionLogic["Position Logic"]
        HOLD["HOLD: no-op"]
        BUY_FLAT["BUY + flat: enterLong"]
        BUY_SHORT["BUY + SHORT: closeShort"]
        SELL_FLAT["SELL + flat: enterShort"]
        SELL_LONG["SELL + LONG: closeLong"]
    end

    LGW --> PositionLogic

    subgraph ExchangeLayer["FuturesExchangeService (interface)"]
        ELP[enterLongPosition]
        ESP[enterShortPosition]
        ELP2[exitLongPosition]
        ESP2[exitShortPosition]
    end

    PositionLogic -->|4 place order| ExchangeLayer

    subgraph Exchanges["Exchange Implementations"]
        BIN[BinanceFuturesService]
        BYB[BybitFuturesService]
        DYD[DydxFuturesService]
        HL[HyperliquidFuturesService]
        PAP[PaperFuturesExchangeService]
        BEXS["BacktestExchangeService\nno real orders"]:::backtest
    end

    ExchangeLayer --> BIN & BYB & DYD & HL & PAP
    BGW -->|backtest only| BEXS

    BIN & BYB & DYD & HL & PAP -->|5 OrderResult| ExchangeLayer
    ExchangeLayer -->|5 OrderResult| Gateways
    Gateways -->|6 ExecutionResult| AO
    AO -->|7 record| TES[TradeExecutionService]
```

</details>

<details>
<summary><strong>4. Reactive Sense–Think–Act Loop</strong></summary>

```mermaid
sequenceDiagram
    participant WS as ExchangeWebSocketClient
    participant AO as AgentOrchestrator
    participant BH as Bulkhead
    participant ST as LangChain4jStrategy
    participant LLM as LLMProvider
    participant TI as TechnicalIndicators
    participant GW as OrderExecutionGateway
    participant EX as FuturesExchangeService
    participant TES as TradeExecutionService
    participant EP as EventPublisher

    WS-->>AO: Flux of KlineClosedEvent (per symbol)
    AO->>AO: throttle check (last-seen timestamp)
    AO->>BH: tryAcquirePermission(agentId)
    BH-->>AO: permit granted
    AO->>ST: executeIteration(agent, event)
    ST->>TI: compute RSI / MACD / BB
    TI-->>ST: IndicatorValues
    ST->>LLM: reason(context, indicators, memory)
    LLM-->>ST: AgentDecision (BUY/SELL/HOLD + confidence)
    ST-->>AO: AgentDecision
    alt decision.isEntry()
        AO->>GW: execute(decision, symbol, price)
        GW->>EX: enterLong/Short or exit
        EX-->>GW: OrderResult
        GW-->>AO: ExecutionResult
        AO->>TES: record(AgentRef, symbol, decision, result)
        TES->>EP: publish(TradeCompletedEvent) [on exits]
    else HOLD
        AO->>AO: skip
    end
```

</details>

<details>
<summary><strong>5. Event-Driven Architecture — Kafka</strong></summary>

```mermaid
graph TB
    subgraph Producers["Event Producers"]
        TES[TradeExecutionService]
        RAS[RiskAssessmentService]
        EDTES[EventDrivenTradeExecutionService]
        APP["AgenticTradingApplication\napp startup / shutdown"]
    end

    subgraph Topics["Kafka Topics"]
        T1[trading.signals]
        T2[trading.executions]
        T3[trading.risk]
        T4[trading.market-data]
        T5[trading.bot-status]
        T_DLQ[DLQ topics]
    end

    subgraph Consumers["Kafka Consumers"]
        EC["EventConsumer\nKafkaListener x5"]
        DLC["DeadLetterConsumer\nKafkaListener"]
        EDTSL["EventDrivenTrailingStopTracker"]
    end

    subgraph SpringListeners["Spring Event Listeners"]
        TRLC["TradeReflectionListener\nEventListener"]
        AO2["AgentOrchestrator\nEventListener"]
    end

    subgraph Persistence["Event Persistence"]
        EPS[EventPersistenceService]
        TEE["TradingEventEntity\nSINGLE_TABLE"]
        DLE["DeadLetterEntity\ndead_letter_events"]
        DB5[(PostgreSQL)]
        EPS --> TEE --> DB5
        DLE --> DB5
    end

    TES -->|TradeCompletedEvent| T2
    APP -->|BotStatusEvent| T5
    RAS -->|RiskEvent| T3
    EDTES -->|TradeExecutionEvent| T2

    T1 & T2 & T3 & T4 & T5 --> EC
    T_DLQ --> DLC

    EC -->|persist| EPS
    EC -->|trailing stop update| EDTSL
    DLC -->|persist dead letters| DLE

    TES -->|TradeCompletedEvent — Spring| TRLC
    TES -->|TradeCompletedEvent — Spring| AO2
```

</details>

<details>
<summary><strong>6. Security Architecture</strong></summary>

```mermaid
graph LR
    subgraph Client["HTTP Client"]
        REQ["Request\nAuthorization: Bearer JWT"]
    end

    subgraph Filters["Spring Security Filter Chain"]
        ARF["AuthRateLimitFilter\nBucket4j"]
        JAF["JwtAuthenticationFilter"]
    end

    subgraph Security["Security Services"]
        JS["JwtService"]
        CUDS["CustomUserDetailsService"]
        AUTHS["AuthService"]
        RTS["RefreshTokenService"]
    end

    subgraph Persistence["Persistence"]
        UR["UserRepository"]
        RTR["RefreshTokenRepository"]
        DB[(PostgreSQL)]
        UR & RTR --> DB
    end

    subgraph Controllers["Auth Endpoints — /api/auth"]
        AC[AuthController]
        POST_LOGIN[POST /login]
        POST_REG[POST /register]
        POST_REFRESH[POST /refresh-token]
        POST_LOGOUT[POST /logout]
        AC --> POST_LOGIN & POST_REG & POST_REFRESH & POST_LOGOUT
    end

    REQ --> ARF --> JAF --> JS
    JS -->|load user| CUDS --> UR
    JS -->|valid| JAF
    JAF -->|authenticated| AC
    AC --> AUTHS --> JS & RTS
    RTS --> RTR
    AUTHS --> UR
```

</details>

<details>
<summary><strong>7. Backtest Architecture</strong></summary>

```mermaid
graph TB
    classDef backtest fill:#fff3e0,stroke:#e65100,color:#3e2723

    subgraph API["Entry Point"]
        BC["BacktestController\n/api/backtest"]
    end

    subgraph BacktestCore["Backtest Core"]
        BS[BacktestService]:::backtest
        BRR[BacktestRunRegistry]:::backtest
        HDL[HistoricalDataLoader]:::backtest
        CBAES[CsvBacktestAgentExecutionService]:::backtest
    end

    subgraph Simulation["Simulation"]
        BGW[BacktestOrderGateway]:::backtest
        BEXS[BacktestExchangeService]:::backtest
        CGS["CachedGrokService\nLLM response cache"]:::backtest
    end

    subgraph Strategy["Agent — same as live"]
        LTA[LLMTradingAgent]
    end

    subgraph Metrics["Performance Metrics"]
        SBMC[StandardBacktestMetricsCalculator]:::backtest
        ECES[EquityCurveExportService]:::backtest
    end

    subgraph Data["Historical Data"]
        CSV[CSV Files]
        DB[(PostgreSQL)]
    end

    BC --> BS
    BC -->|query runs| BRR
    BRR -->|BacktestMetrics| BC
    BC -->|download CSV| ECES
    BS --> HDL --> CSV & DB
    BS --> CBAES
    BS -->|create with latency/slippage/fee| BEXS
    CBAES -->|per candle| LTA --> CGS -->|reasoning| LTA
    CBAES -->|setMarketContext per candle| BEXS
    CBAES --> BGW -->|place order| BEXS
    BEXS -->|OrderResult| BGW
    CBAES -->|ExecutionResult| SBMC -->|BacktestMetrics| BS
    BS -->|save| BRR
    ECES -->|equityCurveToCsv| CURVE["Equity Curve export"]
```

</details>

<details>
<summary><strong>8. Market Data Ingestion</strong></summary>

```mermaid
graph LR
    subgraph Exchanges["Exchanges — WebSocket"]
        BIN_WS["Binance Futures\nWebSocket"]
        BYB_WS["Bybit\nWebSocket"]
        HL_WS["Hyperliquid\nWebSocket"]
    end

    subgraph Adapters["WebSocket Adapters"]
        BWA[BinanceWebSocketAdapter]
        BYWA[BybitWebSocketAdapter]
        HLWA[HyperliquidWebSocketAdapter]
    end

    subgraph Core["Market Data Core"]
        WSMDS[WebSocketMarketDataService]
        MDS["MarketDataSanitizer\nvalidates and normalises"]
    end

    subgraph Events["Domain Events"]
        SCE["KlineClosedEvent\nexchange, symbol, OHLCV"]
        STE["StreamMarketDataEvent\ntype, symbol, price, qty"]
        BTP["BookTickerPayload\nbid / ask"]
    end

    subgraph Consumers["Event Consumers"]
        AO[AgentOrchestrator]
        EP["EventPublisher to Kafka\ntrading.market-data"]
    end

    BIN_WS --> BWA
    BYB_WS --> BYWA
    HL_WS --> HLWA

    BWA & BYWA & HLWA -.->|implements ExchangeWebSocketClient| WSMDS
    WSMDS --> MDS
    MDS --> SCE & STE & BTP
    SCE & STE --> AO
    STE --> EP
```

</details>

<details>
<summary><strong>9. Agent Lifecycle — State Machine</strong></summary>

```mermaid
stateDiagram-v2
    [*] --> CREATED : create

    CREATED --> INITIALIZING : activate
    INITIALIZING --> ACTIVE : registered with Orchestrator

    ACTIVE --> PAUSED : pause
    PAUSED --> ACTIVE : activate

    ACTIVE --> ERROR : exception in strategy or exchange
    ERROR --> ACTIVE : re-activate

    ACTIVE --> STOPPED : stop or delete
    PAUSED --> STOPPED : stop or delete
    ERROR --> STOPPED : stop or delete

    STOPPED --> [*]

    note right of ACTIVE
        Receives KlineClosedEvents via WebSocket
        Bulkhead isolates per-agent concurrency
        LangChain4jStrategy calls LLMProvider
        OrderExecutionGateway routes to exchange
        TradeExecutionService records result
    end note
```

</details>

<details>
<summary><strong>10. Component Dependency Summary</strong></summary>

```mermaid
graph TB
    classDef backtest fill:#fff3e0,stroke:#e65100,color:#3e2723

    subgraph ExternalIO["External I/O"]
        KAFKA_EXT[(Kafka)]
        PG[(PostgreSQL)]
        REDIS[(Redis)]
        BINANCE_EXT[Binance API]
        BYBIT_EXT[Bybit API]
        DYDX_EXT[dYdX API]
        HL_EXT[Hyperliquid API]
        GROK_EXT[Grok API]
        CLAUDE_EXT["Claude API"]
        CSV_EXT[(CSV Files)]:::backtest
    end

    subgraph SpringBoot["Spring Boot Application"]
        subgraph HTTP["HTTP — REST + Security"]
            CTRL["Controllers\nAgent, Order, Auth, Events"]
            BTC2[BacktestController]:::backtest
            SEC["Security\nJWT Filter, Auth Service"]
        end

        subgraph AgentCore["Agent Core"]
            AO2["AgentOrchestrator"]
            AS2["AgentService"]
            TES2["TradeExecutionService"]
            PTS2[PerformanceTrackingService]
        end

        subgraph StrategyLLM["Strategy & LLM"]
            LC4J2[LangChain4jStrategy]
            LTA2[LLMTradingAgent]
            LLM2["GrokClient / ClaudeClient"]
            CGS2[CachedGrokService]:::backtest
        end

        subgraph ExecutionGW["Execution"]
            OGR2[OrderExecutionGatewayRegistry]
            GWIMPL["LiveGateway / PaperGateway"]
            BGW2[BacktestGateway]:::backtest
        end

        subgraph ExchangeSvc["Exchange Services"]
            ESR2[ExchangeServiceRegistry]
            EXIMPLS["Binance/Bybit/Dydx/HL/Paper"]
            BEXS2[BacktestExchangeService]:::backtest
        end

        subgraph BacktestSvc["Backtest"]
            BS2[BacktestService]:::backtest
            CBAES2[CsvBacktestAgentExecutionService]:::backtest
            SBMC2[StandardBacktestMetricsCalculator]:::backtest
        end

        subgraph MarketDataSvc["Market Data"]
            WSMDS2[WebSocketMarketDataService]
            ADAPTERS["Binance/Bybit/HL Adapters"]
        end

        subgraph Messaging["Messaging"]
            EP2[EventPublisher]
            EC2[EventConsumer]
            EPS2[EventPersistenceService]
        end

        subgraph Persistence["Persistence"]
            REPOS["AgentRepository\nOrderRepository\nPositionRepository"]
        end

        subgraph Resilience["Resilience"]
            RES["Resilience4j\nBulkhead, CircuitBreaker, Retry"]
            BUCKET["Bucket4j Rate Limiting"]
        end
    end

    CTRL --> AS2 & AO2
    BTC2 --> BS2
    BS2 --> CBAES2
    CBAES2 -->|per candle| LTA2 --> CGS2
    CGS2 -->|cache| REDIS
    CBAES2 --> BGW2 --> BEXS2
    CBAES2 -->|ExecutionResult| SBMC2

    SEC --> PG
    AS2 --> REPOS --> PG
    AS2 -->|Spring Cache| REDIS
    AO2 --> LC4J2 --> LTA2 --> LLM2
    LLM2 --> GROK_EXT & CLAUDE_EXT
    AO2 --> OGR2 --> GWIMPL --> ESR2 --> EXIMPLS
    EXIMPLS --> BINANCE_EXT & BYBIT_EXT & DYDX_EXT & HL_EXT
    WSMDS2 --> ADAPTERS --> BINANCE_EXT & BYBIT_EXT & HL_EXT
    AO2 --> WSMDS2
    AO2 --> TES2 --> REPOS
    TES2 --> EP2 --> KAFKA_EXT
    EC2 --> KAFKA_EXT
    EC2 --> EPS2 --> PG
    AO2 --> RES
    CTRL --> BUCKET
```

</details>

### Trade Execution Dataflow

```mermaid
sequenceDiagram
    participant AO as AgentOrchestrator
    participant GR as OrderExecutionGatewayRegistry
    participant GW as OrderExecutionGateway
    participant EX as Exchange/Sim
    participant TES as TradeExecutionService

    AO->>AO: Receives KlineClosedEvent (Kafka)
    AO->>AO: AgentStrategy produces AgentDecision
    AO->>GR: resolve(agentId, exchange)
    GR-->>AO: OrderExecutionGateway
    alt decision.isEntry() — BUY or SELL
        AO->>GW: execute(decision, symbol, price)
        GW->>GW: Position logic (open/close/NOOP)
        GW->>EX: Place/cancel order (if needed)
        EX-->>GW: Execution result (fill price, etc.)
        GW-->>AO: ExecutionResult
        AO->>TES: record(ExecutorRef, symbol, decision, result)
        Note right of TES: void return — exceptions caught internally
    else HOLD
        AO->>AO: Skip execution and recording
    end
```

### System Context

```mermaid
graph TB
    TRADER(["Trader\nbrowser / REST client"])

    subgraph TradePilot["TradePilot"]
        GW["API Gateway\n:8080\nJWT + routing"]
        CORE["Backend Core\n:8081 / gRPC :9090\nagent orchestration\norder execution\nevent streaming"]
    end

    subgraph Exchanges["Exchanges"]
        BINE[Binance Futures]
        BYBE[Bybit Futures]
        DYDE[dYdX v4]
        HLE["Hyperliquid\nEIP-712 signing"]
    end

    subgraph AI["AI / Memory"]
        CLAUDE["Claude API\nAnthropic"]
        GROK["Grok API\nxAI"]
        PINECONE["Pinecone\nVector DB"]
    end

    subgraph Infra["Infrastructure"]
        KAFKA["Kafka\nevent streaming"]
        PG["PostgreSQL\npersistence"]
        REDIS["Redis\ncache"]
    end

    TRADER -->|"REST / JWT"| GW
    GW -->|"HTTP proxy"| CORE
    CORE -->|"REST + WebSocket"| BINE & BYBE & DYDE & HLE
    CORE -->|"HTTPS"| CLAUDE & GROK & PINECONE
    CORE -->|"TCP 9092"| KAFKA
    CORE -->|"JDBC 5432"| PG
    CORE -->|"TCP 6379"| REDIS
```

### Deployment Topology

```mermaid
graph TB
    subgraph Internet["External"]
        BROWSER[Browser :3000]
        BINE[Binance API]
        BYBE[Bybit API]
        DYDE[dYdX API]
        HLE[Hyperliquid API]
        GROK[Grok API]
        CLAUDE[Claude API]
        PINECONE["Pinecone\nVector DB"]
    end

    subgraph DockerNetwork["Docker — trading-network"]
        subgraph AppTier["Application Tier"]
            GW["API Gateway\ngateway:8080\n(reverse proxy + JWT)"]
            CORE["Backend Core\ntradepilot-core:8081\ngRPC :9090\nJava 21 / Spring Boot"]
        end

        subgraph DataTier["Data Tier"]
            PG[("PostgreSQL 16\npostgres:5432")]
            REDIS[("Redis 7\nredis:6379")]
        end

        subgraph MsgTier["Messaging Tier"]
            ZK["Zookeeper\nzookeeper:2181"]
            KAFKA["Kafka\nkafka:9092"]
        end
    end

    BROWSER -->|HTTPS| GW
    GW -->|HTTP proxy| CORE

    CORE -->|JDBC| PG
    CORE -->|TCP| REDIS
    CORE -->|produce / consume| KAFKA
    KAFKA -->|coordination| ZK

    CORE -->|REST + WebSocket| BINE & BYBE & DYDE & HLE
    CORE -->|HTTPS| GROK & CLAUDE & PINECONE
```

### Persistence Schema (ERD)

```mermaid
erDiagram
    USERS {
        string id PK
        string username UK
        string email UK
        string password
        string account_tier
        int max_bots
        int max_leverage
        string oauth_provider
        instant created_at
    }
    AGENTS {
        string id PK
        string owner_id FK
        string name UK
        string trading_symbol
        double capital
        string status
        string execution_mode
        string exchange_name
        string goal_type
        instant created_at
        instant last_active_at
    }
    ORDERS {
        string id PK
        string executor_id FK
        string executor_type
        string symbol
        string direction
        string status
        double price
        double quantity
        double realized_pnl
        instant created_at
        instant executed_at
    }
    POSITIONS {
        string id PK
        string agent_id FK
        string main_order_id FK
        string symbol
        string direction
        double entry_price
        double quantity
        double exit_price
        double realized_pnl
        string status
        instant opened_at
        instant closed_at
    }
    AGENT_PERFORMANCE {
        string agent_id PK_FK
        int total_trades
        int winning_trades
        double total_pnl
        double win_rate
        double sharpe_ratio
        double max_drawdown
        instant last_updated
    }
    TRADING_EVENTS {
        string id PK
        string event_id UK
        string event_type
        string bot_id
        string symbol
        instant timestamp
    }
    DEAD_LETTER_EVENTS {
        string id PK
        string original_topic
        int partition
        long kafka_offset
        string exception_type
        boolean resolved
        instant received_at
    }

    USERS ||--o{ AGENTS : "owns"
    AGENTS ||--o{ ORDERS : "places"
    AGENTS ||--o{ POSITIONS : "holds"
    AGENTS ||--|| AGENT_PERFORMANCE : "tracked by"
    TRADING_EVENTS ||--o{ TRADING_EVENTS : "carries metadata"
```

### Class Hierarchy (Interfaces & Implementations)

```mermaid
classDiagram
    class TradingAgent {
        <<interface>>
        +getId() String
        +start()
        +stop()
        +isRunning() boolean
        +onEvent(Object)
    }
    class ReactiveTradingAgent {
        <<interface>>
        +getSymbol() String
        +getExchange() String
        +onKlineClosed(KlineClosedEvent) Mono
        +pause()
        +resume()
    }
    class LLMTradingAgent {
        -LLMProvider llmProvider
        +onKlineClosed(KlineClosedEvent) Mono
    }
    class TechnicalTradingAgent {
        +onKlineClosed(KlineClosedEvent) Mono
    }
    TradingAgent <|-- ReactiveTradingAgent
    ReactiveTradingAgent <|.. LLMTradingAgent
    ReactiveTradingAgent <|.. TechnicalTradingAgent

    class OrderExecutionGateway {
        <<interface>>
        +execute(AgentDecision, String, double) ExecutionResult
        +hasOpenPosition(String) boolean
        +getPositionSide(String) String
    }
    class LiveOrderGateway
    class PaperTradingOrderGateway
    class BacktestOrderGateway
    OrderExecutionGateway <|.. LiveOrderGateway
    OrderExecutionGateway <|.. PaperTradingOrderGateway
    OrderExecutionGateway <|.. BacktestOrderGateway

    class FuturesExchangeService {
        <<interface>>
        +enterLongPosition(String, double) OrderResult
        +enterShortPosition(String, double) OrderResult
        +exitLongPosition(String, double) OrderResult
        +exitShortPosition(String, double) OrderResult
    }
    class BinanceFuturesService
    class BybitFuturesService
    class DydxFuturesService
    class HyperliquidFuturesService
    class PaperFuturesExchangeService
    FuturesExchangeService <|.. BinanceFuturesService
    FuturesExchangeService <|.. BybitFuturesService
    FuturesExchangeService <|.. DydxFuturesService
    FuturesExchangeService <|.. HyperliquidFuturesService
    FuturesExchangeService <|.. PaperFuturesExchangeService

    class LLMProvider {
        <<interface>>
        +generateReasoning(ReasoningContext) String
    }
    class GrokClient
    class ClaudeClient
    class CachedGrokService
    LLMProvider <|.. GrokClient
    LLMProvider <|.. ClaudeClient
    LLMProvider <|.. CachedGrokService
```

### PRD & Background

See [docs/FuturesTradingBotPRD.md](./docs/FuturesTradingBotPRD.md) for the requirements baseline and agent architecture details.

---

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes
4. Push and open a Pull Request

---

## Disclaimer

This project is for educational purposes only. Leveraged trading can result in total loss of capital. Validate all strategies in paper mode or exchange testnet environments before considering any live deployment.
