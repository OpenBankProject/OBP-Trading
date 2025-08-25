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

package com.openbankproject.trading

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

import java.time.Instant
import java.util.UUID
import scala.math.BigDecimal

package object model {

  // Core Identifiers
  case class UserId(value: String) extends AnyVal
  case class AccountId(value: String) extends AnyVal
  case class BankId(value: String) extends AnyVal
  case class OfferId(value: String) extends AnyVal
  case class TradeId(value: String) extends AnyVal
  case class TradingSymbol(value: String) extends AnyVal
  case class ConsentId(value: String) extends AnyVal
  case class ReservationId(value: String) extends AnyVal

  // Trading Value Types
  case class Price(value: BigDecimal) extends AnyVal {
    def +(other: Price): Price = Price(value + other.value)
    def -(other: Price): Price = Price(value - other.value)
    def *(quantity: Quantity): Amount = Amount(value * quantity.value)
  }

  case class Quantity(value: BigDecimal) extends AnyVal {
    def +(other: Quantity): Quantity = Quantity(value + other.value)
    def -(other: Quantity): Quantity = Quantity(value - other.value)
    def min(other: Quantity): Quantity = if (value <= other.value) this else other
    def isPositive: Boolean = value > 0
  }

  case class Amount(value: BigDecimal) extends AnyVal {
    def +(other: Amount): Amount = Amount(value + other.value)
    def -(other: Amount): Amount = Amount(value - other.value)
  }

  // Enumerations
  sealed trait OfferType
  object OfferType {
    case object Buy extends OfferType
    case object Sell extends OfferType

    implicit val encoder: Encoder[OfferType] = Encoder.encodeString.contramap {
      case Buy => "buy"
      case Sell => "sell"
    }

    implicit val decoder: Decoder[OfferType] = Decoder.decodeString.emap {
      case "buy" => Right(Buy)
      case "sell" => Right(Sell)
      case other => Left(s"Invalid offer type: $other")
    }
  }

  sealed trait OfferStatus
  object OfferStatus {
    case object Active extends OfferStatus
    case object PartiallyFilled extends OfferStatus
    case object Filled extends OfferStatus
    case object Cancelled extends OfferStatus
    case object Expired extends OfferStatus
    case object Rejected extends OfferStatus

    implicit val encoder: Encoder[OfferStatus] = Encoder.encodeString.contramap {
      case Active => "active"
      case PartiallyFilled => "partially_filled"
      case Filled => "filled"
      case Cancelled => "cancelled"
      case Expired => "expired"
      case Rejected => "rejected"
    }

    implicit val decoder: Decoder[OfferStatus] = Decoder.decodeString.emap {
      case "active" => Right(Active)
      case "partially_filled" => Right(PartiallyFilled)
      case "filled" => Right(Filled)
      case "cancelled" => Right(Cancelled)
      case "expired" => Right(Expired)
      case "rejected" => Right(Rejected)
      case other => Left(s"Invalid offer status: $other")
    }
  }

  sealed trait TradeStatus
  object TradeStatus {
    case object Pending extends TradeStatus
    case object Settled extends TradeStatus
    case object Failed extends TradeStatus

    implicit val encoder: Encoder[TradeStatus] = Encoder.encodeString.contramap {
      case Pending => "pending"
      case Settled => "settled"
      case Failed => "failed"
    }

    implicit val decoder: Decoder[TradeStatus] = Decoder.decodeString.emap {
      case "pending" => Right(Pending)
      case "settled" => Right(Settled)
      case "failed" => Right(Failed)
      case other => Left(s"Invalid trade status: $other")
    }
  }

  // Core Domain Objects
  case class Offer(
    offerId: OfferId,
    userId: UserId,                 // User who owns the account
    user_id: UserId,                // User who created the offer (could be different due to delegation)
    consent_id: ConsentId,          // Consent used to authorize the offer creation
    accountId: AccountId,           // Link to specific bank account
    bankId: BankId,                 // Bank that owns the account
    symbol: TradingSymbol,
    offerType: OfferType,
    price: Price,
    originalQuantity: Quantity,
    remainingQuantity: Quantity,
    status: OfferStatus,
    createdAt: Instant,
    updatedAt: Instant,
    expiresAt: Instant,
    metadata: Map[String, String] = Map.empty
  ) {
    def isActive: Boolean = status == OfferStatus.Active
    def isExpired: Boolean = Instant.now().isAfter(expiresAt)
    def filledQuantity: Quantity = Quantity(originalQuantity.value - remainingQuantity.value)
    def fillPercentage: BigDecimal = 
      if (originalQuantity.value == 0) BigDecimal(0)
      else (filledQuantity.value / originalQuantity.value) * 100

    def withRemainingQuantity(newQuantity: Quantity): Offer = 
      copy(
        remainingQuantity = newQuantity,
        status = if (newQuantity.value <= 0) OfferStatus.Filled 
                else if (newQuantity.value < originalQuantity.value) OfferStatus.PartiallyFilled
                else status,
        updatedAt = Instant.now()
      )

    def cancel: Offer = copy(status = OfferStatus.Cancelled, updatedAt = Instant.now())
    def expire: Offer = copy(status = OfferStatus.Expired, updatedAt = Instant.now())
  }

  case class Trade(
    tradeId: TradeId,
    user_id: UserId,                // User who initiated/created the trade
    consent_id: ConsentId,          // Consent used to authorize the trade
    symbol: TradingSymbol,
    buyerId: UserId,
    buyerAccountId: AccountId,      // Buyer's bank account
    buyerBankId: BankId,
    sellerId: UserId, 
    sellerAccountId: AccountId,     // Seller's bank account
    sellerBankId: BankId,
    price: Price,
    quantity: Quantity,
    amount: Amount,                 // price * quantity
    buyOfferId: OfferId,
    sellOfferId: OfferId,
    status: TradeStatus,
    executedAt: Instant,
    settledAt: Option[Instant] = None,
    metadata: Map[String, String] = Map.empty
  ) {
    def isPending: Boolean = status == TradeStatus.Pending
    def isSettled: Boolean = status == TradeStatus.Settled
    def isFailed: Boolean = status == TradeStatus.Failed

    def settle: Trade = copy(
      status = TradeStatus.Settled,
      settledAt = Some(Instant.now())
    )

    def fail(reason: String): Trade = copy(
      status = TradeStatus.Failed,
      metadata = metadata + ("failure_reason" -> reason)
    )
  }

  // Market Data Objects
  case class OrderBookLevel(
    price: Price,
    quantity: Quantity,
    count: Int  // Number of offers at this price level
  )

  case class OrderBook(
    symbol: TradingSymbol,
    bids: List[OrderBookLevel], // Buy offers, highest price first
    asks: List[OrderBookLevel], // Sell offers, lowest price first
    timestamp: Instant,
    sequence: Long
  ) {
    def bestBid: Option[Price] = bids.headOption.map(_.price)
    def bestAsk: Option[Price] = asks.headOption.map(_.price)
    def spread: Option[Price] = for {
      bid <- bestBid
      ask <- bestAsk
    } yield ask - bid
  }

  case class MarketDepth(
    symbol: TradingSymbol,
    bidCount: Int,
    askCount: Int,
    bestBid: Option[Price],
    bestAsk: Option[Price],
    spread: Option[Price],
    timestamp: Instant
  ) {
    def totalOffers: Int = bidCount + askCount
    def hasLiquidity: Boolean = bidCount > 0 && askCount > 0
  }

  case class Ticker(
    symbol: TradingSymbol,
    lastPrice: Option[Price],
    lastQuantity: Option[Quantity],
    bestBid: Option[Price],
    bestAsk: Option[Price],
    volume24h: Quantity,
    high24h: Option[Price],
    low24h: Option[Price],
    change24h: Option[Price],
    changePercent24h: Option[BigDecimal],
    timestamp: Instant
  )

  // User and Account Related Objects
  case class TradingProfile(
    userId: UserId,
    accounts: List[TradingAccount], // User's trading-enabled accounts
    permissions: List[TradingPermission],
    settings: TradingSettings,
    createdAt: Instant,
    updatedAt: Instant
  )

  case class TradingAccount(
    accountId: AccountId,
    bankId: BankId,
    currency: String,
    balance: Amount,
    availableBalance: Amount, // balance - reserved for pending trades
    isActive: Boolean,
    permissions: List[String] = List("trade", "view"),
    createdAt: Instant
  ) {
    def canTrade: Boolean = isActive && availableBalance.value > 0
    def reserveAmount(amount: Amount): TradingAccount = 
      copy(availableBalance = Amount(availableBalance.value - amount.value))
  }

  case class TradingPermission(
    symbol: TradingSymbol,
    canBuy: Boolean,
    canSell: Boolean,
    maxOfferAmount: Option[Amount] = None,
    maxDailyVolume: Option[Amount] = None
  ) {
    def canTrade(offerType: OfferType): Boolean = offerType match {
      case OfferType.Buy => canBuy
      case OfferType.Sell => canSell
    }
  }

  case class TradingSettings(
    defaultExpiryHours: Int = 24,
    maxOffersPerSymbol: Int = 10,
    enableNotifications: Boolean = true,
    riskLevel: String = "medium" // low, medium, high
  )

  // Statistics and Analytics
  case class UserTradingStats(
    userId: UserId,
    totalTrades: Int,
    totalVolume: Amount,
    totalFees: Amount,
    profitLoss: Amount,
    bestTrade: Option[Amount],
    worstTrade: Option[Amount],
    averageTradeSize: Amount,
    lastTradeDate: Option[Instant],
    winRate: BigDecimal, // percentage of profitable trades
    periodStart: Instant,
    periodEnd: Instant
  )

  case class TradingVolume(
    symbol: TradingSymbol,
    volume: Quantity,
    amount: Amount,
    tradeCount: Int,
    period: String, // "1h", "24h", "7d", etc.
    timestamp: Instant
  )

  // Account Balance Management
  case class AccountReservation(
    reservationId: ReservationId,
    accountId: AccountId,
    amount: Amount,
    purpose: String, // "offer_creation", "trade_settlement"
    offerId: Option[OfferId] = None,
    tradeId: Option[TradeId] = None,
    holdId: Option[String] = None, // ID of the hold in external system (e.g., OBP-API)
    transactionRequestId: Option[String] = None, // Transaction request ID
    createdAt: Instant,
    expiresAt: Instant,
    isActive: Boolean = true
  ) {
    def expire: AccountReservation = copy(isActive = false)
  }

  // JSON encoders/decoders for all types
  implicit val userIdEncoder: Encoder[UserId] = Encoder.encodeString.contramap(_.value)
  implicit val userIdDecoder: Decoder[UserId] = Decoder.decodeString.map(UserId.apply)
  
  implicit val accountIdEncoder: Encoder[AccountId] = Encoder.encodeString.contramap(_.value)
  implicit val accountIdDecoder: Decoder[AccountId] = Decoder.decodeString.map(AccountId.apply)
  
  implicit val bankIdEncoder: Encoder[BankId] = Encoder.encodeString.contramap(_.value)
  implicit val bankIdDecoder: Decoder[BankId] = Decoder.decodeString.map(BankId.apply)
  
  implicit val offerIdEncoder: Encoder[OfferId] = Encoder.encodeString.contramap(_.value)
  implicit val offerIdDecoder: Decoder[OfferId] = Decoder.decodeString.map(OfferId.apply)
  
  implicit val tradeIdEncoder: Encoder[TradeId] = Encoder.encodeString.contramap(_.value)
  implicit val tradeIdDecoder: Decoder[TradeId] = Decoder.decodeString.map(TradeId.apply)
  
  implicit val tradingSymbolEncoder: Encoder[TradingSymbol] = Encoder.encodeString.contramap(_.value)
  implicit val tradingSymbolDecoder: Decoder[TradingSymbol] = Decoder.decodeString.map(TradingSymbol.apply)
  
  implicit val consentIdEncoder: Encoder[ConsentId] = Encoder.encodeString.contramap(_.value)
  implicit val consentIdDecoder: Decoder[ConsentId] = Decoder.decodeString.map(ConsentId.apply)
  
  implicit val reservationIdEncoder: Encoder[ReservationId] = Encoder.encodeString.contramap(_.value)
  implicit val reservationIdDecoder: Decoder[ReservationId] = Decoder.decodeString.map(ReservationId.apply)
  
  implicit val priceEncoder: Encoder[Price] = Encoder.encodeBigDecimal.contramap(_.value)
  implicit val priceDecoder: Decoder[Price] = Decoder.decodeBigDecimal.map(Price.apply)
  
  implicit val quantityEncoder: Encoder[Quantity] = Encoder.encodeBigDecimal.contramap(_.value)
  implicit val quantityDecoder: Decoder[Quantity] = Decoder.decodeBigDecimal.map(Quantity.apply)
  
  implicit val amountEncoder: Encoder[Amount] = Encoder.encodeBigDecimal.contramap(_.value)
  implicit val amountDecoder: Decoder[Amount] = Decoder.decodeBigDecimal.map(Amount.apply)

  // Derive encoders/decoders for case classes
  implicit val offerEncoder: Encoder[Offer] = deriveEncoder
  implicit val offerDecoder: Decoder[Offer] = deriveDecoder
  
  implicit val tradeEncoder: Encoder[Trade] = deriveEncoder
  implicit val tradeDecoder: Decoder[Trade] = deriveDecoder
  
  implicit val orderBookLevelEncoder: Encoder[OrderBookLevel] = deriveEncoder
  implicit val orderBookLevelDecoder: Decoder[OrderBookLevel] = deriveDecoder
  
  implicit val orderBookEncoder: Encoder[OrderBook] = deriveEncoder
  implicit val orderBookDecoder: Decoder[OrderBook] = deriveDecoder
  
  implicit val marketDepthEncoder: Encoder[MarketDepth] = deriveEncoder
  implicit val marketDepthDecoder: Decoder[MarketDepth] = deriveDecoder
  
  implicit val tickerEncoder: Encoder[Ticker] = deriveEncoder
  implicit val tickerDecoder: Decoder[Ticker] = deriveDecoder
  
  implicit val tradingAccountEncoder: Encoder[TradingAccount] = deriveEncoder
  implicit val tradingAccountDecoder: Decoder[TradingAccount] = deriveDecoder
  
  implicit val tradingPermissionEncoder: Encoder[TradingPermission] = deriveEncoder
  implicit val tradingPermissionDecoder: Decoder[TradingPermission] = deriveDecoder
  
  implicit val tradingSettingsEncoder: Encoder[TradingSettings] = deriveEncoder
  implicit val tradingSettingsDecoder: Decoder[TradingSettings] = deriveDecoder
  
  implicit val tradingProfileEncoder: Encoder[TradingProfile] = deriveEncoder
  implicit val tradingProfileDecoder: Decoder[TradingProfile] = deriveDecoder
  
  implicit val userTradingStatsEncoder: Encoder[UserTradingStats] = deriveEncoder
  implicit val userTradingStatsDecoder: Decoder[UserTradingStats] = deriveDecoder
  
  implicit val tradingVolumeEncoder: Encoder[TradingVolume] = deriveEncoder
  implicit val tradingVolumeDecoder: Decoder[TradingVolume] = deriveDecoder
  
  implicit val accountReservationEncoder: Encoder[AccountReservation] = deriveEncoder
  implicit val accountReservationDecoder: Decoder[AccountReservation] = deriveDecoder

  // Helper functions for creating objects
  object Offer {
    def create(
      userId: UserId,
      createdByUserId: UserId,
      consentId: ConsentId,
      accountId: AccountId,
      bankId: BankId,
      symbol: TradingSymbol,
      offerType: OfferType,
      price: Price,
      quantity: Quantity,
      expiryHours: Int = 24
    ): Offer = {
      val now = Instant.now()
      Offer(
        offerId = OfferId(UUID.randomUUID().toString),
        userId = userId,
        user_id = createdByUserId,
        consent_id = consentId,
        accountId = accountId,
        bankId = bankId,
        symbol = symbol,
        offerType = offerType,
        price = price,
        originalQuantity = quantity,
        remainingQuantity = quantity,
        status = OfferStatus.Active,
        createdAt = now,
        updatedAt = now,
        expiresAt = now.plusSeconds(expiryHours * 3600L)
      )
    }
  }

  object Trade {
    def create(
      createdByUserId: UserId,
      consentId: ConsentId,
      symbol: TradingSymbol,
      buyOffer: Offer,
      sellOffer: Offer,
      tradePrice: Price,
      tradeQuantity: Quantity
    ): Trade = {
      Trade(
        tradeId = TradeId(UUID.randomUUID().toString),
        user_id = createdByUserId,
        consent_id = consentId,
        symbol = symbol,
        buyerId = buyOffer.userId,
        buyerAccountId = buyOffer.accountId,
        buyerBankId = buyOffer.bankId,
        sellerId = sellOffer.userId,
        sellerAccountId = sellOffer.accountId,
        sellerBankId = sellOffer.bankId,
        price = tradePrice,
        quantity = tradeQuantity,
        amount = tradePrice * tradeQuantity,
        buyOfferId = buyOffer.offerId,
        sellOfferId = sellOffer.offerId,
        status = TradeStatus.Pending,
        executedAt = Instant.now()
      )
    }
  }
}