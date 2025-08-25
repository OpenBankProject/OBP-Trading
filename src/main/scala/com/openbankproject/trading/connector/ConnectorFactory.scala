/**
 * Copyright (c) TESOBE 2025. All rights reserved.
 * 
 * This file is part of the Open Bank Project.
 * 
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 * You may obtain a copy of the License at https://www.gnu.org/licenses/agpl-3.0.html
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 */

package com.openbankproject.trading.connector

import cats.effect.kernel.Async
import cats.syntax.all._
import com.typesafe.config.Config
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import dev.profunktor.redis4cats.effect.Log.Stdout._
import slick.jdbc.JdbcBackend.Database
import scala.concurrent.duration._

/**
 * Factory for creating connector instances based on configuration
 * Supports Redis, PostgreSQL, RabbitMQ, and Kafka connectors
 */
class ConnectorFactory[F[_]: Async] {

  /**
   * Creates an offer connector based on configuration
   */
  def createOfferConnector(config: ConnectorConfig): F[Either[ConnectorError, OfferConnector[F]]] = {
    config.connectorType.toLowerCase match {
      case Connector.REDIS =>
        createRedisOfferConnector(config)
      
      case Connector.POSTGRES =>
        createPostgresOfferConnector(config)
      
      case Connector.RABBITMQ =>
        createRabbitMQOfferConnector(config)
        
      case Connector.KAFKA =>
        createKafkaOfferConnector(config)
        
      case unknown =>
        Async[F].pure(Left(ConfigurationError(s"Unknown offer connector type: $unknown")))
    }
  }

  /**
   * Creates a trade connector based on configuration
   */
  def createTradeConnector(config: ConnectorConfig): F[Either[ConnectorError, TradeConnector[F]]] = {
    config.connectorType.toLowerCase match {
      case Connector.POSTGRES =>
        createPostgresTradeConnector(config)
        
      case Connector.KAFKA =>
        createKafkaTradeConnector(config)
        
      case Connector.RABBITMQ =>
        createRabbitMQTradeConnector(config)
        
      case unknown =>
        Async[F].pure(Left(ConfigurationError(s"Unknown trade connector type: $unknown")))
    }
  }

  /**
   * Creates a user connector based on configuration
   */
  def createUserConnector(config: ConnectorConfig): F[Either[ConnectorError, UserConnector[F]]] = {
    config.connectorType.toLowerCase match {
      case Connector.POSTGRES =>
        createPostgresUserConnector(config)
        
      case "obp-api" =>
        createObpApiUserConnector(config)
        
      case unknown =>
        Async[F].pure(Left(ConfigurationError(s"Unknown user connector type: $unknown")))
    }
  }

  // Redis connector factory methods
  private def createRedisOfferConnector(config: ConnectorConfig): F[Either[ConnectorError, OfferConnector[F]]] = {
    for {
      host <- Async[F].fromEither(Connector.getRequiredProperty(config, "host"))
      port <- Async[F].fromEither(parsePort(config.properties.getOrElse("port", "6379")))
      database = config.properties.getOrElse("database", "0").toInt
      password = config.properties.get("password")
      
      redisUri = buildRedisUri(host, port, database, password)
      
      result <- Redis[F].utf8(redisUri).use { redis =>
        val connector = new RedisOfferConnector[F](redis)
        connector.healthCheck.map { status =>
          if (status.healthy) Right(connector)
          else Left(ConnectionError("Redis connection unhealthy"))
        }
      }.handleError(error => Left(ConnectionError(s"Failed to create Redis connector: ${error.getMessage}", Some(error))))
      
    } yield result
  }

  // PostgreSQL connector factory methods
  private def createPostgresOfferConnector(config: ConnectorConfig): F[Either[ConnectorError, OfferConnector[F]]] = {
    for {
      url <- Async[F].fromEither(Connector.getRequiredProperty(config, "url"))
      username <- Async[F].fromEither(Connector.getRequiredProperty(config, "username"))
      password <- Async[F].fromEither(Connector.getRequiredProperty(config, "password"))
      
      result <- Async[F].delay {
        val database = Database.forURL(url, username, password)
        val connector = new PostgresOfferConnector[F](database)
        Right(connector)
      }.handleError(error => Left(ConnectionError(s"Failed to create PostgreSQL connector: ${error.getMessage}", Some(error))))
      
    } yield result
  }

  private def createPostgresTradeConnector(config: ConnectorConfig): F[Either[ConnectorError, TradeConnector[F]]] = {
    for {
      url <- Async[F].fromEither(Connector.getRequiredProperty(config, "url"))
      username <- Async[F].fromEither(Connector.getRequiredProperty(config, "username"))
      password <- Async[F].fromEither(Connector.getRequiredProperty(config, "password"))
      
      result <- Async[F].delay {
        val database = Database.forURL(url, username, password)
        val connector = new PostgresTradeConnector[F](database)
        Right(connector)
      }.handleError(error => Left(ConnectionError(s"Failed to create PostgreSQL trade connector: ${error.getMessage}", Some(error))))
      
    } yield result
  }

  private def createPostgresUserConnector(config: ConnectorConfig): F[Either[ConnectorError, UserConnector[F]]] = {
    for {
      url <- Async[F].fromEither(Connector.getRequiredProperty(config, "url"))
      username <- Async[F].fromEither(Connector.getRequiredProperty(config, "username"))
      password <- Async[F].fromEither(Connector.getRequiredProperty(config, "password"))
      
      result <- Async[F].delay {
        val database = Database.forURL(url, username, password)
        val connector = new PostgresUserConnector[F](database)
        Right(connector)
      }.handleError(error => Left(ConnectionError(s"Failed to create PostgreSQL user connector: ${error.getMessage}", Some(error))))
      
    } yield result
  }

  // RabbitMQ connector factory methods
  private def createRabbitMQOfferConnector(config: ConnectorConfig): F[Either[ConnectorError, OfferConnector[F]]] = {
    for {
      host <- Async[F].fromEither(Connector.getRequiredProperty(config, "host"))
      port <- Async[F].fromEither(parsePort(config.properties.getOrElse("port", "5672")))
      username <- Async[F].fromEither(Connector.getRequiredProperty(config, "username"))
      password <- Async[F].fromEither(Connector.getRequiredProperty(config, "password"))
      exchange = config.properties.getOrElse("offers_exchange", "trading.offers")
      
      result <- Async[F].delay {
        val connector = new RabbitMQOfferConnector[F](host, port, username, password, exchange)
        Right(connector)
      }.handleError(error => Left(ConnectionError(s"Failed to create RabbitMQ connector: ${error.getMessage}", Some(error))))
      
    } yield result
  }

  private def createRabbitMQTradeConnector(config: ConnectorConfig): F[Either[ConnectorError, TradeConnector[F]]] = {
    for {
      host <- Async[F].fromEither(Connector.getRequiredProperty(config, "host"))
      port <- Async[F].fromEither(parsePort(config.properties.getOrElse("port", "5672")))
      username <- Async[F].fromEither(Connector.getRequiredProperty(config, "username"))
      password <- Async[F].fromEither(Connector.getRequiredProperty(config, "password"))
      exchange = config.properties.getOrElse("trades_exchange", "trading.trades")
      
      result <- Async[F].delay {
        val connector = new RabbitMQTradeConnector[F](host, port, username, password, exchange)
        Right(connector)
      }.handleError(error => Left(ConnectionError(s"Failed to create RabbitMQ trade connector: ${error.getMessage}", Some(error))))
      
    } yield result
  }

  // Kafka connector factory methods
  private def createKafkaOfferConnector(config: ConnectorConfig): F[Either[ConnectorError, OfferConnector[F]]] = {
    for {
      bootstrapServers <- Async[F].fromEither(Connector.getRequiredProperty(config, "bootstrap_servers"))
      topic = config.properties.getOrElse("offers_topic", "trading-offers")
      
      result <- Async[F].delay {
        val connector = new KafkaOfferConnector[F](bootstrapServers, topic)
        Right(connector)
      }.handleError(error => Left(ConnectionError(s"Failed to create Kafka connector: ${error.getMessage}", Some(error))))
      
    } yield result
  }

  private def createKafkaTradeConnector(config: ConnectorConfig): F[Either[ConnectorError, TradeConnector[F]]] = {
    for {
      bootstrapServers <- Async[F].fromEither(Connector.getRequiredProperty(config, "bootstrap_servers"))
      topic = config.properties.getOrElse("trades_topic", "trading-trades")
      
      result <- Async[F].delay {
        val connector = new KafkaTradeConnector[F](bootstrapServers, topic)
        Right(connector)
      }.handleError(error => Left(ConnectionError(s"Failed to create Kafka trade connector: ${error.getMessage}", Some(error))))
      
    } yield result
  }

  // OBP API connector factory methods
  private def createObpApiUserConnector(config: ConnectorConfig): F[Either[ConnectorError, UserConnector[F]]] = {
    for {
      baseUrl <- Async[F].fromEither(Connector.getRequiredProperty(config, "base_url"))
      clientId <- Async[F].fromEither(Connector.getRequiredProperty(config, "client_id"))
      clientSecret <- Async[F].fromEither(Connector.getRequiredProperty(config, "client_secret"))
      
      result <- Async[F].delay {
        val connector = new ObpApiUserConnector[F](baseUrl, clientId, clientSecret)
        Right(connector)
      }.handleError(error => Left(ConnectionError(s"Failed to create OBP API connector: ${error.getMessage}", Some(error))))
      
    } yield result
  }

  // Helper methods
  private def parsePort(portStr: String): Either[ConnectorError, Int] = {
    try {
      Right(portStr.toInt)
    } catch {
      case _: NumberFormatException => 
        Left(ConfigurationError(s"Invalid port number: $portStr"))
    }
  }

  private def buildRedisUri(host: String, port: Int, database: Int, password: Option[String]): String = {
    val auth = password.map(p => s":$p@").getOrElse("")
    s"redis://$auth$host:$port/$database"
  }
}

object ConnectorFactory {
  
  /**
   * Creates a connector factory instance
   */
  def apply[F[_]: Async]: ConnectorFactory[F] = new ConnectorFactory[F]
  
  /**
   * Creates connector configuration from Typesafe Config
   */
  def configFromTypesafeConfig(config: Config, section: String): Either[ConnectorError, ConnectorConfig] = {
    try {
      val sectionConfig = config.getConfig(section)
      val connectorType = sectionConfig.getString("type")
      
      import scala.jdk.CollectionConverters._
      val properties = sectionConfig.entrySet().asScala.map { entry =>
        entry.getKey -> entry.getValue.unwrapped().toString
      }.toMap
      
      Right(ConnectorConfig(connectorType, properties))
      
    } catch {
      case ex: Exception => 
        Left(ConfigurationError(s"Failed to parse configuration section '$section': ${ex.getMessage}"))
    }
  }
  
  /**
   * Creates default configurations for common setups
   */
  object Defaults {
    
    def redisOfferConnector(host: String = "localhost", port: Int = 6379, database: Int = 0): ConnectorConfig = {
      ConnectorConfig(
        connectorType = Connector.REDIS,
        properties = Map(
          "host" -> host,
          "port" -> port.toString,
          "database" -> database.toString
        )
      )
    }
    
    def postgresTradeConnector(url: String, username: String, password: String): ConnectorConfig = {
      ConnectorConfig(
        connectorType = Connector.POSTGRES,
        properties = Map(
          "url" -> url,
          "username" -> username,
          "password" -> password
        )
      )
    }
    
    def kafkaConnector(bootstrapServers: String, topic: String): ConnectorConfig = {
      ConnectorConfig(
        connectorType = Connector.KAFKA,
        properties = Map(
          "bootstrap_servers" -> bootstrapServers,
          "topic" -> topic
        )
      )
    }
  }
}