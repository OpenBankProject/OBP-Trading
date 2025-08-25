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
import com.openbankproject.commons.model.{User, Bank, BankAccount}
import com.openbankproject.trading.model._
import io.circe.Json
import java.time.Instant
import java.util.UUID

/**
 * Base trait for all trading connectors
 * Provides common functionality and error handling patterns
 */
trait Connector[F[_]] {
  def healthCheck: F[ConnectorStatus]
  def getConnectorInfo: ConnectorInfo
}

/**
 * Connector for managing trading offers
 * Handles storage and retrieval of buy/sell offers
 */
trait OfferConnector[F[_]] extends Connector[F] {
  
  // Core offer operations
  def createOffer(offer: Offer): F[Either[ConnectorError, Offer]]
  def updateOffer(offer: Offer): F[Either[ConnectorError, Offer]]
  def getOffer(id: OfferId): F[Either[ConnectorError, Option[Offer]]]
  def cancelOffer(id: OfferId, userId: UserId): F[Either[ConnectorError, Unit]]
  
  // Query operations
  def getUserOffers(userId: UserId, limit: Option[Int] = None): F[Either[ConnectorError, List[Offer]]]
  def getActiveOffers(symbol: TradingSymbol, limit: Option[Int] = None): F[Either[ConnectorError, List[Offer]]]
  def getOffersBySymbol(symbol: TradingSymbol, limit: Option[Int] = None): F[Either[ConnectorError, List[Offer]]]
  
  // Market data operations
  def buildOrderBook(symbol: TradingSymbol, depth: Int): F[Either[ConnectorError, OrderBook]]
  def getMarketDepth(symbol: TradingSymbol): F[Either[ConnectorError, MarketDepth]]
  
  // Cleanup operations
  def expireOffers(before: Instant): F[Either[ConnectorError, Int]]
  def cleanupExpiredOffers(): F[Either[ConnectorError, Unit]]
}

/**
 * Connector for managing completed trades
 * Handles persistent storage of trade execution records
 */
trait TradeConnector[F[_]] extends Connector[F] {
  
  // Core trade operations
  def recordTrade(trade: Trade): F[Either[ConnectorError, Trade]]
  def getTrade(id: TradeId): F[Either[ConnectorError, Option[Trade]]]
  
  // Query operations
  def getUserTrades(userId: UserId, limit: Option[Int] = None): F[Either[ConnectorError, List[Trade]]]
  def getTradesBySymbol(symbol: TradingSymbol, limit: Option[Int] = None): F[Either[ConnectorError, List[Trade]]]
  def getRecentTrades(symbol: TradingSymbol, since: Option[Instant] = None, limit: Option[Int] = None): F[Either[ConnectorError, List[Trade]]]
  
  // Analytics operations
  def getTradingVolume(symbol: TradingSymbol, since: Instant): F[Either[ConnectorError, TradingVolume]]
  def getTradingStats(userId: UserId): F[Either[ConnectorError, UserTradingStats]]
}

/**
 * Connector for user and account management
 * Integrates with OBP user and account systems
 */
trait UserConnector[F[_]] extends Connector[F] {
  
  // User operations
  def getUser(userId: UserId): F[Either[ConnectorError, Option[User]]]
  def getUserByEmail(email: String): F[Either[ConnectorError, Option[User]]]
  def getUserAccounts(userId: UserId): F[Either[ConnectorError, List[BankAccount]]]
  
  // Trading profile operations
  def getUserTradingProfile(userId: UserId): F[Either[ConnectorError, Option[TradingProfile]]]
  def updateTradingProfile(userId: UserId, profile: TradingProfile): F[Either[ConnectorError, TradingProfile]]
  
  // Permission checks
  def canUserTrade(userId: UserId, symbol: TradingSymbol): F[Either[ConnectorError, Boolean]]
  def getUserTradingPermissions(userId: UserId): F[Either[ConnectorError, List[TradingPermission]]]
}

/**
 * Connector status information
 */
case class ConnectorStatus(
  healthy: Boolean,
  responseTimeMs: Long,
  lastChecked: Instant,
  details: Option[String] = None
)

/**
 * Connector metadata and configuration info
 */
case class ConnectorInfo(
  name: String,
  version: String,
  description: String,
  supportedOperations: List[String],
  configuration: Map[String, String]
)

/**
 * Base trait for connector errors
 */
sealed trait ConnectorError extends Exception {
  def message: String
  def cause: Option[Throwable] = None
}

case class ConnectionError(message: String, override val cause: Option[Throwable] = None) extends ConnectorError
case class ValidationError(message: String) extends ConnectorError
case class NotFoundError(message: String) extends ConnectorError
case class DuplicateError(message: String) extends ConnectorError
case class PermissionError(message: String) extends ConnectorError
case class ConfigurationError(message: String) extends ConnectorError
case class TimeoutError(message: String) extends ConnectorError
case class UnknownError(message: String, override val cause: Option[Throwable] = None) extends ConnectorError

/**
 * Connector factory for creating connector instances based on configuration
 */
trait ConnectorFactory[F[_]] {
  def createOfferConnector(config: ConnectorConfig): F[Either[ConnectorError, OfferConnector[F]]]
  def createTradeConnector(config: ConnectorConfig): F[Either[ConnectorError, TradeConnector[F]]]
  def createUserConnector(config: ConnectorConfig): F[Either[ConnectorError, UserConnector[F]]]
}

/**
 * Configuration for connector instances
 */
case class ConnectorConfig(
  connectorType: String,
  properties: Map[String, String]
)

/**
 * Companion object with utility methods
 */
object Connector {
  
  // Connector type constants
  val REDIS = "redis"
  val POSTGRES = "postgres" 
  val RABBITMQ = "rabbitmq"
  val KAFKA = "kafka"
  val MONGODB = "mongodb"
  val ELASTICSEARCH = "elasticsearch"
  
  // Operation constants
  val CREATE_OFFER = "createOffer"
  val UPDATE_OFFER = "updateOffer"
  val CANCEL_OFFER = "cancelOffer"
  val GET_OFFER = "getOffer"
  val RECORD_TRADE = "recordTrade"
  val GET_TRADE = "getTrade"
  
  /**
   * Creates a connector configuration from a map of properties
   */
  def configFrom(connectorType: String, properties: Map[String, String]): ConnectorConfig = {
    ConnectorConfig(connectorType, properties)
  }
  
  /**
   * Helper to extract required configuration property
   */
  def getRequiredProperty(config: ConnectorConfig, key: String): Either[ConnectorError, String] = {
    config.properties.get(key) match {
      case Some(value) => Right(value)
      case None => Left(ConfigurationError(s"Missing required property: $key"))
    }
  }
  
  /**
   * Helper to extract optional configuration property with default
   */
  def getProperty(config: ConnectorConfig, key: String, default: String): String = {
    config.properties.getOrElse(key, default)
  }
}