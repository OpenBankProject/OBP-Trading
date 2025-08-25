# OBP-Trading

A high-performance Scala-based trading engine for the Open Bank Project (OBP) ecosystem, focusing on offer management and real-time market data.

## 🚀 Overview

OBP-Trading is a modern trading engine built with Scala and the cats-effect ecosystem that integrates seamlessly with the OBP platform. It manages buy/sell offers, provides real-time order books, and connects with OBP-API for trade execution and authentication.

### Key Features

- **High-Performance Scala Engine** - Built with cats-effect for functional async programming
- **Offer Management** - Complete lifecycle management of buy/sell trading offers
- **Real-Time Order Books** - Live market depth and price discovery
- **Redis Caching** - Hot data storage for sub-millisecond response times
- **PostgreSQL Storage** - Persistent storage with ACID compliance
- **JWT Authentication** - Secure integration with OBP authentication
- **WebSocket Streaming** - Real-time market data and offer updates
- **RESTful API** - Complete HTTP API with http4s
- **Role-Based Access** - Admin, trader, and user permission levels
- **Connector Architecture** - Pluggable storage and messaging backends

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    OBP-Trading Engine                       │
├─────────────────┬─────────────────┬─────────────────────────┤
│   HTTP API      │   WebSocket     │    Integration          │
│   (http4s)      │   (Real-time)   │    Layer               │
├─────────────────┼─────────────────┼─────────────────────────┤
│                 │                 │                         │
│ Offer Mgmt      │ Market Data     │ OBP-API                │
│ Order Books     │ User Streams    │ (Trade Execution)       │
│ User Mgmt       │ Price Feeds     │                         │
│                 │                 │ OBP Authentication      │
│                 │                 │ (JWT Validation)        │
├─────────────────┴─────────────────┴─────────────────────────┤
│               Business Logic (Scala)                        │
│       (Trading Services, Connectors, Validation)            │
├─────────────────┬─────────────────┬─────────────────────────┤
│      Redis      │   PostgreSQL    │    Message Brokers      │
│   (Hot Data)    │ (Persistence)   │  (Kafka/RabbitMQ)      │
│   Connector     │   Connector     │    Connectors          │
└─────────────────┴─────────────────┴─────────────────────────┘
```

## 🛠️ Technology Stack

- **Language**: Scala 2.13.15
- **Build Tool**: SBT
- **HTTP Server**: http4s with Ember server
- **Async Runtime**: cats-effect 3.x
- **Database**: PostgreSQL with Slick
- **Caching**: Redis with redis4cats
- **JSON**: Circe for JSON handling
- **Authentication**: JWT with jwt-scala
- **Message Brokers**: Kafka (zio-kafka) and RabbitMQ (fs2-rabbit)
- **Configuration**: PureConfig with Typesafe Config
- **Logging**: Logback with scala-logging
- **Testing**: ScalaTest with Testcontainers

## 📋 Prerequisites

- Scala 2.13.15+
- SBT 1.9+
- Java 11+ (OpenJDK recommended)
- PostgreSQL 14+
- Redis 6+
- OBP-API instance (for authentication and trade execution)

## 🚀 Quick Start

### 1. Clone and Setup

```bash
git clone https://github.com/OpenBankProject/OBP-Trading
cd OBP-Trading
```

### 2. Configuration

```bash
cp src/main/resources/application.conf.example src/main/resources/application.conf
# Edit application.conf with your database and service URLs
```

### 3. Database Setup

```bash
# Create database
createdb obp_trading

# Update application.conf with your database settings
```

### 4. Start Services

```bash
# Start Redis
redis-server

# Start PostgreSQL (if not running)
pg_ctl start

# Compile and run
sbt run
```

### 5. Verify Installation

```bash
# Health check
curl http://localhost:8080/health

# System information
curl http://localhost:8080/api/v1/system/info
```

## 🔧 Configuration

### Application Configuration

Key configuration in `application.conf`:

```hocon
# Server configuration
server {
  host = "0.0.0.0"
  port = 8080
}

# Database configuration
database {
  url = "jdbc:postgresql://localhost:5432/obp_trading"
  username = "username"
  password = "password"
  driver = "org.postgresql.Driver"
  
  # Connection pool settings
  maxConnections = 20
  minConnections = 5
}

# Redis configuration
redis {
  host = "localhost"
  port = 6379
  database = 0
  # password = "optional_password"
}

# OBP Integration
obp {
  api {
    baseUrl = "https://api.openbankproject.com"
    version = "v5.1.0"
  }
  
  auth {
    jwtPublicKeyUrl = "https://api.openbankproject.com/.well-known/jwks.json"
    issuer = "https://api.openbankproject.com"
  }
}

# Trading configuration
trading {
  maxOffersPerUser = 100
  defaultOfferExpiryHours = 24
  rateLimitOffersPerMinute = 10
  
  # Supported trading symbols
  supportedSymbols = ["EUR/USD", "GBP/USD", "USD/JPY", "BTC/USD"]
}

# Connector configuration
connectors {
  offer {
    type = "redis"
    host = "localhost"
    port = 6379
    database = 1
  }
  
  trade {
    type = "postgres"
    url = ${database.url}
    username = ${database.username}
    password = ${database.password}
  }
  
  user {
    type = "obp-api"
    baseUrl = ${obp.api.baseUrl}
  }
}
```

### Environment Variables

Override configuration with environment variables:

```bash
export SERVER_PORT=9090
export DATABASE_URL="jdbc:postgresql://localhost:5432/obp_trading"
export REDIS_HOST="redis.example.com"
export OBP_API_BASE_URL="https://your-obp-api.com"
```

## 📚 API Documentation

### Core Endpoints

#### Offers Management
- `POST /api/v1/offers` - Create new offer
- `GET /api/v1/offers/{id}` - Get offer details
- `PUT /api/v1/offers/{id}` - Update offer
- `DELETE /api/v1/offers/{id}/cancel` - Cancel offer
- `GET /api/v1/users/{userId}/offers` - Get user offers

#### Market Data
- `GET /api/v1/markets` - List trading pairs
- `GET /api/v1/markets/{symbol}/ticker` - Get ticker data
- `GET /api/v1/orderbook/{symbol}` - Get order book
- `GET /api/v1/markets/{symbol}/trades` - Recent trades
- `GET /api/v1/markets/{symbol}/depth` - Market depth

#### System
- `GET /health` - Health check
- `GET /api/v1/system/info` - System information
- `GET /api/v1/system/stats` - Trading statistics

### Authentication

All protected endpoints require JWT bearer token from OBP:

```bash
curl -H "Authorization: Bearer <jwt-token>" \
     -H "Content-Type: application/json" \
     http://localhost:8080/api/v1/offers
```

### Example API Usage

#### Create Offer

```bash
curl -X POST http://localhost:8080/api/v1/offers \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "EUR/USD",
    "offerType": "buy",
    "price": "1.0850",
    "quantity": "1000.00",
    "expiresAt": "2025-01-15T10:00:00Z"
  }'
```

#### Get Order Book

```bash
curl http://localhost:8080/api/v1/orderbook/EUR/USD?depth=10
```

## 🧪 Testing

### Unit Tests

```bash
sbt test
```

### Integration Tests

```bash
# Start test dependencies
docker-compose -f docker-compose.test.yml up -d

# Run integration tests
sbt it:test
```

### Test with Coverage

```bash
sbt coverage test coverageReport
```

## 🐳 Docker Deployment

### Build Docker Image

```bash
sbt docker:publishLocal
```

### Development Environment

```bash
docker-compose up -d
```

### Production Environment

```bash
docker-compose -f docker-compose.prod.yml up -d
```

## 📊 Connector Architecture

The system uses a pluggable connector architecture for different storage and messaging backends:

### Available Connectors

#### Offer Connectors
- **RedisOfferConnector** - High-speed in-memory storage for active offers
- **PostgresOfferConnector** - Persistent storage with full ACID properties
- **KafkaOfferConnector** - Event-driven offer management
- **RabbitMQOfferConnector** - Message queue based offer handling

#### Trade Connectors  
- **PostgresTradeConnector** - Persistent trade record storage
- **KafkaTradeConnector** - Stream processing for trade events
- **ElasticsearchTradeConnector** - Search and analytics

#### User Connectors
- **ObpApiUserConnector** - Integration with OBP user management
- **PostgresUserConnector** - Local user and profile storage

### Connector Configuration

```scala
// Example: Creating a Redis offer connector
val redisConfig = ConnectorConfig(
  connectorType = "redis",
  properties = Map(
    "host" -> "localhost",
    "port" -> "6379",
    "database" -> "1"
  )
)

val connectorFactory = ConnectorFactory[IO]
connectorFactory.createOfferConnector(redisConfig)
```

## 🔒 Security

### Authentication Flow

1. User authenticates via OBP-API
2. OBP issues JWT token with user permissions
3. Client includes token in Authorization header
4. Trading engine validates JWT signature and claims
5. Request processed based on user roles

### Security Features

- JWT token validation with public key verification
- Role-based access control (Admin, Trader, User)
- Rate limiting per user and endpoint
- Input validation with refined types
- SQL injection prevention with parameterized queries
- HTTPS/TLS support in production

## 🚀 Building and Running

### Development

```bash
# Compile
sbt compile

# Run with hot reload
sbt ~reStart

# Run tests continuously
sbt ~test

# Format code
sbt scalafmt

# Check for updates
sbt dependencyUpdates
```

### Production

```bash
# Create production JAR
sbt assembly

# Run production JAR
java -Xmx2G -Dconfig.resource=production.conf \
     -jar target/scala-2.13/obp-trading-assembly-0.1.0-SNAPSHOT.jar

# Or use Docker
sbt docker:publish
docker run -p 8080:8080 openbankproject/obp-trading:latest
```

## 📈 Performance

### Design Goals

- **Low Latency**: Sub-10ms API response times
- **High Throughput**: 10,000+ requests/second
- **Concurrent Users**: 1,000+ simultaneous connections
- **Real-time Updates**: WebSocket latency <100ms

### Optimization Features

- Cats-effect for efficient async processing
- Redis caching for hot data paths
- Connection pooling for databases
- Functional programming for correctness
- Immutable data structures for safety

## 🔍 Monitoring and Observability

### Health Checks

- Application health endpoint
- Database connectivity checks
- Redis availability checks  
- External service health

### Logging

Structured logging with MDC context:

```bash
# Set log levels
export SCALA_OPTS="-Dlogback.configurationFile=logback-dev.xml"

# Enable debug logging
export LOG_LEVEL=DEBUG
```

### Metrics

Ready for integration with:
- Prometheus/Grafana dashboards
- Micrometer metrics
- Custom business metrics

## 🛣️ Roadmap

### Current Version (v0.1.0)
- ✅ Core connector architecture
- ✅ Redis offer connector
- ✅ Basic API endpoints
- ✅ JWT authentication
- 🚧 WebSocket streaming
- 🚧 Order book generation
- 🚧 Market depth calculations

### Upcoming Features
- **v0.2.0**: Complete WebSocket API, additional connectors
- **v0.3.0**: Advanced order types, market making features
- **v0.4.0**: Analytics dashboard, reporting API
- **v1.0.0**: Production hardening, performance optimization

## 📄 License

This project is licensed under the AGPL-3.0 License - see the [LICENSE](LICENSE) file for details.

**Copyright (c) TESOBE 2025. All rights reserved.**

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Follow Scala style guidelines
4. Add tests for new features
5. Update documentation
6. Submit pull request

### Code Style

- Use `scalafmt` for formatting
- Follow functional programming principles
- Prefer immutable data structures
- Use cats-effect for async operations
- Add ScalaDoc for public APIs

### Testing Guidelines

- Write unit tests for business logic
- Integration tests for connectors
- Property-based tests where applicable
- Use Testcontainers for database tests

## 👥 Support

- **Issues**: GitHub Issues for bug reports and feature requests
- **Discussions**: GitHub Discussions for questions
- **Documentation**: Check `/docs` directory for detailed guides
- **OBP Community**: [OpenBankProject.com](https://openbankproject.com)

## 🙏 Acknowledgments

- Open Bank Project community
- Typelevel ecosystem (cats-effect, http4s, circe)
- Scala community
- All contributors and testers

---

**Note**: This is an active development version. For production deployment, ensure proper security review, load testing, and monitoring setup.