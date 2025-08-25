# OBP-Trading Architecture

## Overview

OBP-Trading is a Scala-based trading engine built for the Open Bank Project ecosystem. It focuses on offer management, real-time market data, and seamless integration with OBP services.

## Technology Stack

### HTTP Layer
- **http4s-ember-server** - Fast, lightweight HTTP server
- **http4s-dsl** - Functional route DSL for API endpoints  
- **http4s-circe** - JSON support and serialization

### OBP Integration
- **obp-commons** - Shared models and utilities from OBP ecosystem
- **lift-json** - JSON compatibility with existing OBP patterns
- **lift-mapper** - Database patterns and ORM (if needed for compatibility)

### Functional Programming
- **cats-effect** - Effect system for pure functional programming
- **cats-core** - Core functional abstractions and type classes

### Data Layer
- **slick** - Functional relational mapping for database queries
- **circe** - High-performance JSON processing and codec derivation

### Supporting Libraries
- **postgresql** - Primary database driver
- **redis** - Caching and real-time data storage
- **jwt-scala** - JWT token handling for authentication
- **refined** - Compile-time validation and type refinement
- **logback** - Logging framework
- **scalatest** - Testing framework

## Architecture Principles

### 1. Functional First
- Pure functions with referential transparency
- Effect management through cats-effect IO
- Immutable data structures throughout

### 2. Type Safety
- Leverages Scala's type system for compile-time safety
- Refined types for domain validation
- JSON codec derivation with circe

### 3. OBP Ecosystem Integration
- Uses obp-commons for shared models (User, Bank, Account, etc.)
- Compatible with OBP authentication patterns
- Follows OBP API conventions and error handling

### 4. Performance & Scalability
- Non-blocking HTTP server with http4s-ember
- Functional stream processing for real-time data
- Redis caching for hot data access
- Connection pooling for database operations

## System Components

### Core Services
- **Offer Management** - Create, update, cancel trading offers
- **Order Book** - Real-time market depth aggregation
- **Market Data** - Price feeds, trading statistics
- **Authentication** - JWT validation with OBP-OIDC integration
- **User Management** - Profile, preferences, trading history
- **Connector Layer** - Pluggable storage and messaging backends

### Data Storage
- **PostgreSQL** - Persistent storage for offers, trades, users
- **Redis** - Hot data cache, real-time market data, sessions

### Data Storage
- **PostgreSQL** - Persistent storage for trades, audit logs, users
- **Redis** - Default storage for offers, hot data cache, sessions

### External Integrations
- **OBP-API-II** - Account access, transaction execution
- **OBP-OIDC** - Authentication and authorization
- **OBP-Commons** - Shared data models and utilities

## Connector Architecture

### Overview
The connector layer provides pluggable backends for different data storage and messaging patterns, similar to OBP-API but simplified for trading data.

### Supported Connectors

#### Offer Storage Connectors
- **redis** (default) - Fast in-memory storage for active offers
- **rabbitmq** - Send offers to RabbitMQ queues for processing
- **kafka** - Publish offers to Kafka topics for streaming
- **postgres** - Store offers directly in PostgreSQL

#### Trade Storage Connectors  
- **postgres** (default) - Persistent storage for executed trades
- **kafka** - Stream trade events to Kafka topics
- **rabbitmq** - Send trade notifications to RabbitMQ

### Configuration
```hocon
connector {
  offers = "redis"        # redis, rabbitmq, kafka, postgres
  trades = "postgres"     # postgres, kafka, rabbitmq
  
  redis {
    host = "localhost"
    port = 6379
    database = 0
  }
  
  rabbitmq {
    host = "localhost"
    port = 5672
    username = "guest"
    password = "guest"
    offers_exchange = "trading.offers"
    trades_exchange = "trading.trades"
  }
  
  kafka {
    bootstrap_servers = "localhost:9092"
    offers_topic = "trading-offers"
    trades_topic = "trading-trades"
  }
  
  postgres {
    url = "jdbc:postgresql://localhost:5432/obp_trading"
    username = "obp_trading"
    password = "password"
  }
}
```

## Design Patterns

### 1. Connector Pattern
```scala
trait OfferConnector[F[_]] {
  def createOffer(offer: Offer): F[Offer]
  def updateOffer(offer: Offer): F[Offer]
  def getOffer(id: OfferId): F[Option[Offer]]
  def getUserOffers(userId: UserId): F[List[Offer]]
  def cancelOffer(id: OfferId): F[Unit]
}

trait TradeConnector[F[_]] {
  def recordTrade(trade: Trade): F[Trade]
  def getTradeHistory(userId: UserId, limit: Int): F[List[Trade]]
  def getTradeById(id: TradeId): F[Option[Trade]]
}
```

### 2. Connector Factory
```scala
object ConnectorFactory {
  def createOfferConnector[F[_]: Async](config: ConnectorConfig): OfferConnector[F] = {
    config.offerConnector match {
      case "redis" => new RedisOfferConnector[F]
      case "rabbitmq" => new RabbitMQOfferConnector[F]
      case "kafka" => new KafkaOfferConnector[F] 
      case "postgres" => new PostgresOfferConnector[F]
    }
  }
  
  def createTradeConnector[F[_]: Async](config: ConnectorConfig): TradeConnector[F] = {
    config.tradeConnector match {
      case "postgres" => new PostgresTradeConnector[F]
      case "kafka" => new KafkaTradeConnector[F]
      case "rabbitmq" => new RabbitMQTradeConnector[F]
    }
  }
}
```

### 3. Repository Pattern
```scala
trait OfferRepository[F[_]] {
  def create(offer: Offer): F[Offer]
  def findById(id: OfferId): F[Option[Offer]]
  def findByUser(userId: UserId): F[List[Offer]]
}

// Repository delegates to connector
class OfferRepositoryImpl[F[_]](connector: OfferConnector[F]) extends OfferRepository[F] {
  def create(offer: Offer): F[Offer] = connector.createOffer(offer)
  def findById(id: OfferId): F[Option[Offer]] = connector.getOffer(id)
  def findByUser(userId: UserId): F[List[Offer]] = connector.getUserOffers(userId)
}
```

### 4. Service Layer
```scala
trait OfferService[F[_]] {
  def createOffer(request: CreateOfferRequest): F[Either[TradingError, Offer]]
  def cancelOffer(offerId: OfferId, userId: UserId): F[Either[TradingError, Unit]]
}
```

### 5. HTTP Routes
```scala
object OfferRoutes {
  def routes[F[_]: Async](service: OfferService[F]): HttpRoutes[F] = {
    HttpRoutes.of[F] {
      case req @ POST -> Root / "offers" => 
        // Handle offer creation
    }
  }
}
```

## Error Handling

### Functional Error Types
```scala
sealed trait TradingError extends Exception
case class InsufficientFunds(required: Amount, available: Amount) extends TradingError
case class InvalidTradingPair(symbol: String) extends TradingError
case class OfferNotFound(id: OfferId) extends TradingError
```

### HTTP Error Mapping
```scala
def mapToHttpError[F[_]: Applicative](error: TradingError): F[Response[F]] = {
  error match {
    case InsufficientFunds(_, _) => BadRequest("Insufficient funds")
    case InvalidTradingPair(_) => BadRequest("Invalid trading pair")
    case OfferNotFound(_) => NotFound("Offer not found")
  }
}
```

## Configuration

### Application Configuration
- **Typesafe Config** for environment-specific settings
- **PureConfig** for type-safe configuration loading
- Environment variable overrides for deployment

### Database Configuration
- Connection pooling with HikariCP
- Migration management with Flyway
- Read/write splitting capability

## Security

### Authentication Flow
1. Client obtains JWT from OBP-OIDC
2. JWT included in Authorization header
3. Trading service validates JWT with OBP-OIDC JWKS
4. User permissions extracted from JWT claims
5. Role-based access control applied

### Data Protection
- SQL injection prevention through parameterized queries
- Input validation with refined types
- Rate limiting per user/endpoint
- Audit logging for all trading operations

## Deployment

### Containerization
- Docker images with OpenJDK base
- Multi-stage builds for optimized images
- Health checks and readiness probes

### Environment Support
- Development, staging, production configurations
- Database migrations on startup
- Graceful shutdown handling

## Monitoring & Observability

### Metrics
- HTTP request metrics (latency, throughput, errors)
- Database connection pool metrics
- Trading-specific metrics (offers created, trades executed)

### Logging
- Structured logging with logback
- Request correlation IDs
- Performance metrics logging

### Health Checks
- Database connectivity
- Redis connectivity  
- OBP service dependencies

### Connector Implementation Examples

#### Redis Offer Connector
```scala
class RedisOfferConnector[F[_]: Async](redis: RedisCommands[F, String, String]) 
  extends OfferConnector[F] {
  
  def createOffer(offer: Offer): F[Offer] = {
    for {
      json <- Sync[F].delay(offer.asJson.noSpaces)
      _ <- redis.set(s"offer:${offer.id}", json)
      _ <- redis.zadd(s"user:${offer.userId}:offers", offer.createdAt.toEpochMilli, offer.id.toString)
    } yield offer
  }
}
```

#### RabbitMQ Offer Connector  
```scala
class RabbitMQOfferConnector[F[_]: Async](channel: Channel) 
  extends OfferConnector[F] {
  
  def createOffer(offer: Offer): F[Offer] = {
    for {
      json <- Sync[F].delay(offer.asJson.noSpaces)
      _ <- Sync[F].delay {
        channel.basicPublish(
          "trading.offers",
          s"offer.${offer.symbol}",
          null,
          json.getBytes()
        )
      }
    } yield offer
  }
}
```

#### Kafka Offer Connector
```scala
class KafkaOfferConnector[F[_]: Async](producer: KafkaProducer[String, String]) 
  extends OfferConnector[F] {
  
  def createOffer(offer: Offer): F[Offer] = {
    for {
      json <- Sync[F].delay(offer.asJson.noSpaces)
      record = new ProducerRecord("trading-offers", offer.id.toString, json)
      _ <- Async[F].fromFuture(Sync[F].delay(producer.send(record)))
    } yield offer
  }
}
```

### Connector Switching Benefits

1. **Development Flexibility**
   - Use Redis for fast local development
   - Switch to PostgreSQL for data persistence testing
   - Use message queues for integration testing

2. **Deployment Options**
   - High-frequency trading: Redis for offers, Kafka for trades
   - Regulatory compliance: PostgreSQL for both
   - Event-driven architecture: RabbitMQ/Kafka for all data

3. **Performance Tuning**
   - Hot data in Redis, cold data in PostgreSQL
   - Real-time streams via Kafka
   - Async processing via RabbitMQ

### Future Enhancements

### Planned Features
- WebSocket real-time feeds
- Advanced order types (stop-loss, limit orders)  
- Market making capabilities
- Multi-currency trading support
- Advanced analytics and reporting

### Additional Connectors
- **ElasticSearch** - Full-text search and analytics
- **MongoDB** - Document-based offer storage
- **AWS SQS/SNS** - Cloud-based messaging
- **Apache Pulsar** - Advanced streaming platform

### Scalability Improvements
- Event sourcing for audit trails
- CQRS for read/write optimization  
- Distributed caching with Redis Cluster
- Microservice decomposition
- Connector load balancing and failover