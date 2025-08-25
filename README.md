# OBP-Trading

Scala-based trading engine for the Open Bank Project (OBP) ecosystem.

## Status

**Current Version: 0.1.0-SNAPSHOT**  
**Development Phase: Model Architecture Complete (85%)**

### WIP
- ✅ Scala project structure with SBT
- ✅ Core trading models with account integration
- ✅ Connector architecture (Redis, PostgreSQL, Kafka, RabbitMQ)
- ✅ RedisOfferConnector implementation
- ✅ Comprehensive validation framework
- ✅ OBP Account Holds integration design

### What's Not Working Yet
- 🚧 HTTP API endpoints
- 🚧 Authentication middleware
- 🚧 Database migrations
- 🚧 OBP-API client integration
- 🚧 WebSocket streaming

## Architecture

```
Trading Engine (Scala + http4s)
├── Models: Offer, Trade, AccountReservation
├── Connectors: Redis, PostgreSQL, Kafka, RabbitMQ
├── Validation: Account, Permission, Business Rules
└── OBP Integration: Account Holds + Transaction Requests
```

## Key Design Decisions

1. **Account-Linked Trading**: Every offer/trade linked to OBP bank accounts
2. **OBP Account Holds**: Use OBP-API holds for fund reservation (no separate accounts)
3. **Two-Layer Money**: FIAT via OBP Transactions, trading via account holds
4. **Audit Trail**: `user_id` and `consent_id` on all operations
5. **Fully Qualified IDs**: `offerId`, `tradeId`, etc. (no generic "id" fields)

## Prerequisites

- Scala 2.13.15+
- SBT 1.9+
- Java 11+
- PostgreSQL 14+
- Redis 6+
- OBP-API instance

## Quick Start

```bash
# Clone and build
git clone https://github.com/OpenBankProject/OBP-Trading
cd OBP-Trading
sbt compile

# Configure
cp src/main/resources/application.conf.example src/main/resources/application.conf
# Edit database and OBP-API settings

# Run (when ready)
sbt run
```

## Configuration

```hocon
# OBP Integration
obp.api.baseUrl = "https://api.openbankproject.com"
obp.auth.jwtPublicKeyUrl = "https://api.openbankproject.com/.well-known/jwks.json"

# Connectors
connectors {
  offer.type = "redis"     # Fast offer storage
  trade.type = "postgres"  # Persistent trade records  
  user.type = "obp-api"    # OBP user integration
}
```

## Development

See `ai.log` for detailed development history and architectural decisions.

## License

AGPL-3.0  
Copyright (c) TESOBE 2025. All rights reserved.