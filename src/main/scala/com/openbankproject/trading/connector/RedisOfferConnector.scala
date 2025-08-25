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
import com.openbankproject.trading.model._
import dev.profunktor.redis4cats.RedisCommands
import io.circe.syntax._
import io.circe.parser._
import java.time.Instant
import scala.concurrent.duration._

/**
 * Redis implementation of OfferConnector
 * Uses Redis for fast storage and retrieval of trading offers
 */
class RedisOfferConnector[F[_]: Async](redis: RedisCommands[F, String, String]) 
    extends OfferConnector[F] {

  import RedisOfferConnector._

  override def getConnectorInfo: ConnectorInfo = ConnectorInfo(
    name = "Redis Offer Connector",
    version = "1.0.0",
    description = "Fast in-memory storage for trading offers using Redis",
    supportedOperations = List(
      Connector.CREATE_OFFER,
      Connector.UPDATE_OFFER,
      Connector.CANCEL_OFFER,
      Connector.GET_OFFER,
      "buildOrderBook",
      "getMarketDepth"
    ),
    configuration = Map(
      "storage" -> "redis",
      "persistence" -> "memory",
      "real_time" -> "true"
    )
  )

  override def healthCheck: F[ConnectorStatus] = {
    val startTime = System.currentTimeMillis()
    redis.ping.map { _ =>
      ConnectorStatus(
        healthy = true,
        responseTimeMs = System.currentTimeMillis() - startTime,
        lastChecked = Instant.now(),
        details = Some("Redis connection healthy")
      )
    }.handleError { error =>
      ConnectorStatus(
        healthy = false,
        responseTimeMs = System.currentTimeMillis() - startTime,
        lastChecked = Instant.now(),
        details = Some(s"Redis connection failed: ${error.getMessage}")
      )
    }
  }

  override def createOffer(offer: Offer): F[Either[ConnectorError, Offer]] = {
    for {
      json <- Async[F].delay(offer.asJson.noSpaces)
      _ <- redis.setEx(offerKey(offer.id), json, DEFAULT_EXPIRY)
      _ <- redis.zAdd(userOffersKey(offer.userId), offer.createdAt.toEpochMilli.toDouble, offer.id.value)
      _ <- redis.zAdd(symbolOffersKey(offer.symbol), priceScore(offer), offer.id.value)
      _ <- redis.sAdd(activeOffersKey, offer.id.value)
    } yield Right(offer)
  }.handleError(error => Left(ConnectionError(s"Failed to create offer: ${error.getMessage}", Some(error))))

  override def updateOffer(offer: Offer): F[Either[ConnectorError, Offer]] = {
    for {
      exists <- redis.exists(offerKey(offer.id))
      result <- if (exists) {
        for {
          json <- Async[F].delay(offer.asJson.noSpaces)
          _ <- redis.setEx(offerKey(offer.id), json, DEFAULT_EXPIRY)
          _ <- redis.zAdd(symbolOffersKey(offer.symbol), priceScore(offer), offer.id.value)
        } yield Right(offer)
      } else {
        Async[F].pure(Left(NotFoundError(s"Offer ${offer.id.value} not found")))
      }
    } yield result
  }.handleError(error => Left(ConnectionError(s"Failed to update offer: ${error.getMessage}", Some(error))))

  override def getOffer(id: OfferId): F[Either[ConnectorError, Option[Offer]]] = {
    redis.get(offerKey(id)).map {
      case Some(json) =>
        decode[Offer](json) match {
          case Right(offer) => Right(Some(offer))
          case Left(error) => Left(ValidationError(s"Invalid offer JSON: ${error.getMessage}"))
        }
      case None => Right(None)
    }.handleError(error => Left(ConnectionError(s"Failed to get offer: ${error.getMessage}", Some(error))))
  }

  override def cancelOffer(id: OfferId, userId: UserId): F[Either[ConnectorError, Unit]] = {
    for {
      offerOpt <- getOffer(id)
      result <- offerOpt match {
        case Right(Some(offer)) if offer.userId == userId =>
          for {
            cancelledOffer = offer.copy(status = OfferStatus.Cancelled)
            json <- Async[F].delay(cancelledOffer.asJson.noSpaces)
            _ <- redis.setEx(offerKey(id), json, DEFAULT_EXPIRY)
            _ <- redis.sRem(activeOffersKey, id.value)
            _ <- redis.zRem(symbolOffersKey(offer.symbol), id.value)
          } yield Right(())
        case Right(Some(_)) =>
          Async[F].pure(Left(PermissionError(s"User $userId cannot cancel offer $id")))
        case Right(None) =>
          Async[F].pure(Left(NotFoundError(s"Offer $id not found")))
        case Left(error) =>
          Async[F].pure(Left(error))
      }
    } yield result
  }.handleError(error => Left(ConnectionError(s"Failed to cancel offer: ${error.getMessage}", Some(error))))

  override def getUserOffers(userId: UserId, limit: Option[Int]): F[Either[ConnectorError, List[Offer]]] = {
    val maxResults = limit.getOrElse(DEFAULT_LIMIT).toLong
    for {
      offerIds <- redis.zRevRange(userOffersKey(userId), 0L, maxResults - 1)
      offers <- offerIds.traverse(id => getOffer(OfferId(id)))
      validOffers = offers.collect { case Right(Some(offer)) => offer }
    } yield Right(validOffers)
  }.handleError(error => Left(ConnectionError(s"Failed to get user offers: ${error.getMessage}", Some(error))))

  override def getActiveOffers(symbol: TradingSymbol, limit: Option[Int]): F[Either[ConnectorError, List[Offer]]] = {
    val maxResults = limit.getOrElse(DEFAULT_LIMIT).toLong
    for {
      // Get buy offers (highest price first)
      buyOfferIds <- redis.zRevRange(symbolOffersKey(symbol, OfferType.Buy), 0L, maxResults / 2)
      // Get sell offers (lowest price first)  
      sellOfferIds <- redis.zRange(symbolOffersKey(symbol, OfferType.Sell), 0L, maxResults / 2)
      
      buyOffers <- buyOfferIds.traverse(id => getOffer(OfferId(id)))
      sellOffers <- sellOfferIds.traverse(id => getOffer(OfferId(id)))
      
      validBuyOffers = buyOffers.collect { case Right(Some(offer)) => offer }
      validSellOffers = sellOffers.collect { case Right(Some(offer)) => offer }
      
    } yield Right(validBuyOffers ++ validSellOffers)
  }.handleError(error => Left(ConnectionError(s"Failed to get active offers: ${error.getMessage}", Some(error))))

  override def getOffersBySymbol(symbol: TradingSymbol, limit: Option[Int]): F[Either[ConnectorError, List[Offer]]] = {
    getActiveOffers(symbol, limit)
  }

  override def buildOrderBook(symbol: TradingSymbol, depth: Int): F[Either[ConnectorError, OrderBook]] = {
    for {
      // Get buy offers sorted by price (highest first)
      buyOfferIds <- redis.zRevRange(symbolOffersKey(symbol, OfferType.Buy), 0L, depth.toLong - 1)
      // Get sell offers sorted by price (lowest first)
      sellOfferIds <- redis.zRange(symbolOffersKey(symbol, OfferType.Sell), 0L, depth.toLong - 1)
      
      buyOffers <- buyOfferIds.traverse(id => getOffer(OfferId(id)))
      sellOffers <- sellOfferIds.traverse(id => getOffer(OfferId(id)))
      
      validBuyOffers = buyOffers.collect { case Right(Some(offer)) => offer }.filter(_.status == OfferStatus.Active)
      validSellOffers = sellOffers.collect { case Right(Some(offer)) => offer }.filter(_.status == OfferStatus.Active)
      
      // Group by price level
      buyLevels = groupByPriceLevel(validBuyOffers)
      sellLevels = groupByPriceLevel(validSellOffers)
      
      orderBook = OrderBook(
        symbol = symbol,
        bids = buyLevels.toList.sortBy(-_.price.value), // Highest first
        asks = sellLevels.toList.sortBy(_.price.value),  // Lowest first
        timestamp = Instant.now(),
        sequence = System.currentTimeMillis()
      )
    } yield Right(orderBook)
  }.handleError(error => Left(ConnectionError(s"Failed to build order book: ${error.getMessage}", Some(error))))

  override def getMarketDepth(symbol: TradingSymbol): F[Either[ConnectorError, MarketDepth]] = {
    for {
      buyCount <- redis.zCard(symbolOffersKey(symbol, OfferType.Buy))
      sellCount <- redis.zCard(symbolOffersKey(symbol, OfferType.Sell))
      
      // Get best bid and ask
      bestBid <- redis.zRevRange(symbolOffersKey(symbol, OfferType.Buy), 0L, 0L)
        .flatMap(_.headOption.fold(Async[F].pure(Option.empty[Offer]))(id => 
          getOffer(OfferId(id)).map(_.toOption.flatten)))
          
      bestAsk <- redis.zRange(symbolOffersKey(symbol, OfferType.Sell), 0L, 0L)
        .flatMap(_.headOption.fold(Async[F].pure(Option.empty[Offer]))(id => 
          getOffer(OfferId(id)).map(_.toOption.flatten)))
      
      depth = MarketDepth(
        symbol = symbol,
        bidCount = buyCount.toInt,
        askCount = sellCount.toInt,
        bestBid = bestBid.map(_.price),
        bestAsk = bestAsk.map(_.price),
        spread = for {
          bid <- bestBid.map(_.price)
          ask <- bestAsk.map(_.price)
        } yield ask - bid,
        timestamp = Instant.now()
      )
    } yield Right(depth)
  }.handleError(error => Left(ConnectionError(s"Failed to get market depth: ${error.getMessage}", Some(error))))

  override def expireOffers(before: Instant): F[Either[ConnectorError, Int]] = {
    for {
      activeOfferIds <- redis.sMembers(activeOffersKey)
      expiredCount <- activeOfferIds.foldLeftM(0) { (count, offerId) =>
        getOffer(OfferId(offerId)).flatMap {
          case Right(Some(offer)) if offer.expiresAt.isBefore(before) =>
            cancelOffer(offer.id, offer.userId).map(_ => count + 1)
          case _ => Async[F].pure(count)
        }
      }
    } yield Right(expiredCount)
  }.handleError(error => Left(ConnectionError(s"Failed to expire offers: ${error.getMessage}", Some(error))))

  override def cleanupExpiredOffers(): F[Either[ConnectorError, Unit]] = {
    expireOffers(Instant.now()).map(_.map(_ => ()))
  }

  // Helper methods
  private def groupByPriceLevel(offers: List[Offer]): List[OrderBookLevel] = {
    offers.groupBy(_.price).map { case (price, offersAtPrice) =>
      OrderBookLevel(
        price = price,
        quantity = offersAtPrice.map(_.remainingQuantity).sum,
        count = offersAtPrice.length
      )
    }.toList
  }

  private def priceScore(offer: Offer): Double = {
    offer.offerType match {
      case OfferType.Buy => offer.price.value.toDouble
      case OfferType.Sell => offer.price.value.toDouble
    }
  }
}

object RedisOfferConnector {
  // Redis key patterns
  private val OFFER_PREFIX = "offer"
  private val USER_OFFERS_PREFIX = "user_offers"
  private val SYMBOL_OFFERS_PREFIX = "symbol_offers"
  private val ACTIVE_OFFERS_KEY = "active_offers"
  
  private val DEFAULT_EXPIRY = 24.hours
  private val DEFAULT_LIMIT = 100
  
  private def offerKey(id: OfferId): String = s"$OFFER_PREFIX:${id.value}"
  private def userOffersKey(userId: UserId): String = s"$USER_OFFERS_PREFIX:${userId.value}"
  private def symbolOffersKey(symbol: TradingSymbol): String = s"$SYMBOL_OFFERS_PREFIX:${symbol.value}"
  private def symbolOffersKey(symbol: TradingSymbol, offerType: OfferType): String = 
    s"$SYMBOL_OFFERS_PREFIX:${symbol.value}:${offerType.toString.toLowerCase}"
  private val activeOffersKey: String = ACTIVE_OFFERS_KEY
}