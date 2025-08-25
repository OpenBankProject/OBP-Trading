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

package com.openbankproject.trading.model

import cats.data.ValidatedNel
import cats.syntax.all._

import java.time.Instant
import scala.math.BigDecimal

object validation {

  // Validation error types
  sealed trait ValidationError extends Exception {
    def message: String
  }

  case class AccountValidationError(message: String) extends ValidationError
  case class OfferValidationError(message: String) extends ValidationError  
  case class TradeValidationError(message: String) extends ValidationError
  case class BalanceValidationError(message: String) extends ValidationError
  case class PermissionValidationError(message: String) extends ValidationError
  case class BusinessRuleError(message: String) extends ValidationError

  type ValidationResult[A] = ValidatedNel[ValidationError, A]

  // Account validation rules
  object AccountValidation {
    
    def validateAccountActive(account: TradingAccount): ValidationResult[TradingAccount] = {
      if (account.isActive) account.validNel
      else AccountValidationError(s"Account ${account.accountId.value} is not active").invalidNel
    }

    def validateAccountOwnership(userId: UserId, account: TradingAccount): ValidationResult[TradingAccount] = {
      // Note: In a real implementation, you'd check account ownership through OBP API
      // For now, we assume this validation is done elsewhere
      account.validNel
    }

    def validateSufficientBalance(account: TradingAccount, requiredAmount: Amount): ValidationResult[TradingAccount] = {
      if (account.availableBalance.value >= requiredAmount.value) account.validNel
      else BalanceValidationError(
        s"Insufficient balance in account ${account.accountId.value}. " +
        s"Available: ${account.availableBalance.value}, Required: ${requiredAmount.value}"
      ).invalidNel
    }

    def validateCurrencyMatch(account: TradingAccount, expectedCurrency: String): ValidationResult[TradingAccount] = {
      if (account.currency == expectedCurrency) account.validNel
      else AccountValidationError(
        s"Currency mismatch. Account currency: ${account.currency}, Expected: $expectedCurrency"
      ).invalidNel
    }

    def validateAccountForOffer(
      userId: UserId, 
      account: TradingAccount, 
      requiredAmount: Amount,
      expectedCurrency: String
    ): ValidationResult[TradingAccount] = {
      (
        validateAccountActive(account),
        validateAccountOwnership(userId, account),
        validateSufficientBalance(account, requiredAmount),
        validateCurrencyMatch(account, expectedCurrency)
      ).mapN((_, _, _, validAccount) => validAccount)
    }
  }

  // Offer validation rules
  object OfferValidation {
    
    def validatePrice(price: Price): ValidationResult[Price] = {
      if (price.value > 0) price.validNel
      else OfferValidationError("Price must be positive").invalidNel
    }

    def validateQuantity(quantity: Quantity): ValidationResult[Quantity] = {
      if (quantity.value > 0) quantity.validNel
      else OfferValidationError("Quantity must be positive").invalidNel
    }

    def validateExpiry(expiresAt: Instant): ValidationResult[Instant] = {
      val now = Instant.now()
      if (expiresAt.isAfter(now)) expiresAt.validNel
      else OfferValidationError("Expiry time must be in the future").invalidNel
    }

    def validateMinMaxQuantity(quantity: Quantity, symbol: TradingSymbol): ValidationResult[Quantity] = {
      // These could be configurable per trading symbol
      val minQuantity = BigDecimal("0.01")
      val maxQuantity = BigDecimal("1000000.00")
      
      if (quantity.value < minQuantity) {
        OfferValidationError(s"Quantity ${quantity.value} is below minimum $minQuantity for ${symbol.value}").invalidNel
      } else if (quantity.value > maxQuantity) {
        OfferValidationError(s"Quantity ${quantity.value} exceeds maximum $maxQuantity for ${symbol.value}").invalidNel
      } else {
        quantity.validNel
      }
    }

    def validatePriceRange(price: Price, symbol: TradingSymbol): ValidationResult[Price] = {
      // Implement reasonable price boundaries (could be based on recent market data)
      val minPrice = BigDecimal("0.0001")
      val maxPrice = BigDecimal("1000000.00")
      
      if (price.value < minPrice) {
        OfferValidationError(s"Price ${price.value} is below minimum $minPrice for ${symbol.value}").invalidNel
      } else if (price.value > maxPrice) {
        OfferValidationError(s"Price ${price.value} exceeds maximum $maxPrice for ${symbol.value}").invalidNel
      } else {
        price.validNel
      }
    }

    def validateAccountAndBankConsistency(accountId: AccountId, bankId: BankId): ValidationResult[(AccountId, BankId)] = {
      // In a real implementation, you'd verify the account belongs to the bank
      // For now, we assume this is validated elsewhere via OBP API calls
      (accountId, bankId).validNel
    }

    def validateOfferCreation(offer: Offer): ValidationResult[Offer] = {
      (
        validatePrice(offer.price),
        validateQuantity(offer.originalQuantity),
        validateQuantity(offer.remainingQuantity),
        validateExpiry(offer.expiresAt),
        validateMinMaxQuantity(offer.originalQuantity, offer.symbol),
        validatePriceRange(offer.price, offer.symbol),
        validateAccountAndBankConsistency(offer.accountId, offer.bankId)
      ).mapN((_, _, _, _, _, _, _) => offer)
    }

    def validateOfferUpdate(original: Offer, updated: Offer): ValidationResult[Offer] = {
      val validations = List(
        // Core fields cannot be changed
        if (original.offerId == updated.offerId) ().validNel 
        else OfferValidationError("Offer ID cannot be changed").invalidNel,
        
        if (original.userId == updated.userId) ().validNel
        else OfferValidationError("User ID cannot be changed").invalidNel,
        
        if (original.accountId == updated.accountId) ().validNel
        else OfferValidationError("Account ID cannot be changed").invalidNel,
        
        if (original.bankId == updated.bankId) ().validNel
        else OfferValidationError("Bank ID cannot be changed").invalidNel,
        
        if (original.symbol == updated.symbol) ().validNel
        else OfferValidationError("Trading symbol cannot be changed").invalidNel,
        
        if (original.offerType == updated.offerType) ().validNel
        else OfferValidationError("Offer type cannot be changed").invalidNel,
        
        // Remaining quantity can only decrease
        if (updated.remainingQuantity.value <= original.remainingQuantity.value) ().validNel
        else OfferValidationError("Remaining quantity can only decrease").invalidNel,
        
        // Validate new remaining quantity is not negative
        if (updated.remainingQuantity.value >= 0) ().validNel
        else OfferValidationError("Remaining quantity cannot be negative").invalidNel
      )
      
      validations.sequence_.map(_ => updated)
    }
  }

  // Trading permission validation
  object PermissionValidation {
    
    def validateTradingPermission(
      permission: TradingPermission, 
      offerType: OfferType
    ): ValidationResult[TradingPermission] = {
      if (permission.canTrade(offerType)) permission.validNel
      else PermissionValidationError(
        s"User does not have permission to ${offerType.toString.toLowerCase} ${permission.symbol.value}"
      ).invalidNel
    }

    def validateOfferAmount(
      permission: TradingPermission, 
      offerAmount: Amount
    ): ValidationResult[TradingPermission] = {
      permission.maxOfferAmount match {
        case Some(maxAmount) if offerAmount.value > maxAmount.value =>
          PermissionValidationError(
            s"Offer amount ${offerAmount.value} exceeds maximum allowed ${maxAmount.value} for ${permission.symbol.value}"
          ).invalidNel
        case _ => permission.validNel
      }
    }

    def validateDailyVolume(
      permission: TradingPermission,
      currentDailyVolume: Amount,
      offerAmount: Amount
    ): ValidationResult[TradingPermission] = {
      permission.maxDailyVolume match {
        case Some(maxVolume) =>
          val newTotalVolume = currentDailyVolume.value + offerAmount.value
          if (newTotalVolume > maxVolume.value) {
            PermissionValidationError(
              s"Adding offer would exceed daily volume limit. Current: ${currentDailyVolume.value}, " +
              s"Offer: ${offerAmount.value}, Limit: ${maxVolume.value}"
            ).invalidNel
          } else {
            permission.validNel
          }
        case None => permission.validNel
      }
    }

    def validateFullTradingPermission(
      permission: TradingPermission,
      offerType: OfferType,
      offerAmount: Amount,
      currentDailyVolume: Amount
    ): ValidationResult[TradingPermission] = {
      (
        validateTradingPermission(permission, offerType),
        validateOfferAmount(permission, offerAmount),
        validateDailyVolume(permission, currentDailyVolume, offerAmount)
      ).mapN((validPermission, _, _) => validPermission)
    }
  }

  // Trade validation rules
  object TradeValidation {
    
    def validateTradeAccounts(trade: Trade): ValidationResult[Trade] = {
      val validations = List(
        // Buyer and seller must be different
        if (trade.buyerId != trade.sellerId) ().validNel
        else TradeValidationError("Buyer and seller cannot be the same user").invalidNel,
        
        // Buyer and seller accounts must be different  
        if (trade.buyerAccountId != trade.sellerAccountId) ().validNel
        else TradeValidationError("Buyer and seller cannot use the same account").invalidNel,
        
        // Trade quantity must be positive
        if (trade.quantity.value > 0) ().validNel
        else TradeValidationError("Trade quantity must be positive").invalidNel,
        
        // Trade price must be positive
        if (trade.price.value > 0) ().validNel
        else TradeValidationError("Trade price must be positive").invalidNel,
        
        // Amount should equal price * quantity
        if (trade.amount.value == (trade.price.value * trade.quantity.value)) ().validNel
        else TradeValidationError("Trade amount must equal price × quantity").invalidNel
      )
      
      validations.sequence_.map(_ => trade)
    }

    def validateOfferCompatibility(buyOffer: Offer, sellOffer: Offer): ValidationResult[(Offer, Offer)] = {
      val validations = List(
        // Must be same symbol
        if (buyOffer.symbol == sellOffer.symbol) ().validNel
        else TradeValidationError(s"Offer symbols don't match: ${buyOffer.symbol.value} vs ${sellOffer.symbol.value}").invalidNel,
        
        // Buy offer must be buy type, sell offer must be sell type
        if (buyOffer.offerType == OfferType.Buy) ().validNel
        else TradeValidationError("First offer must be a buy offer").invalidNel,
        
        if (sellOffer.offerType == OfferType.Sell) ().validNel
        else TradeValidationError("Second offer must be a sell offer").invalidNel,
        
        // Both offers must be active
        if (buyOffer.isActive) ().validNel
        else TradeValidationError(s"Buy offer ${buyOffer.offerId.value} is not active").invalidNel,
        
        if (sellOffer.isActive) ().validNel
        else TradeValidationError(s"Sell offer ${sellOffer.offerId.value} is not active").invalidNel,
        
        // Neither offer should be expired
        if (!buyOffer.isExpired) ().validNel
        else TradeValidationError(s"Buy offer ${buyOffer.offerId.value} is expired").invalidNel,
        
        if (!sellOffer.isExpired) ().validNel
        else TradeValidationError(s"Sell offer ${sellOffer.offerId.value} is expired").invalidNel,
        
        // Buy price must be >= sell price for a trade to occur
        if (buyOffer.price.value >= sellOffer.price.value) ().validNel
        else TradeValidationError(
          s"Buy price ${buyOffer.price.value} is less than sell price ${sellOffer.price.value}"
        ).invalidNel
      )
      
      validations.sequence_.map(_ => (buyOffer, sellOffer))
    }

    def validateTradeQuantity(
      buyOffer: Offer, 
      sellOffer: Offer, 
      tradeQuantity: Quantity
    ): ValidationResult[Quantity] = {
      val maxQuantity = buyOffer.remainingQuantity.min(sellOffer.remainingQuantity)
      
      if (tradeQuantity.value <= 0) {
        TradeValidationError("Trade quantity must be positive").invalidNel
      } else if (tradeQuantity.value > maxQuantity.value) {
        TradeValidationError(
          s"Trade quantity ${tradeQuantity.value} exceeds available quantity ${maxQuantity.value}"
        ).invalidNel
      } else {
        tradeQuantity.validNel
      }
    }
  }

  // Business rule validation
  object BusinessRuleValidation {
    
    def validateUserOfferLimits(
      userId: UserId, 
      currentOfferCount: Int, 
      maxOffersPerUser: Int
    ): ValidationResult[Unit] = {
      if (currentOfferCount >= maxOffersPerUser) {
        BusinessRuleError(s"User has reached maximum offer limit of $maxOffersPerUser").invalidNel
      } else {
        ().validNel
      }
    }

    def validateSymbolOfferLimits(
      userId: UserId,
      symbol: TradingSymbol,
      currentSymbolOfferCount: Int,
      maxOffersPerSymbol: Int
    ): ValidationResult[Unit] = {
      if (currentSymbolOfferCount >= maxOffersPerSymbol) {
        BusinessRuleError(
          s"User has reached maximum offer limit of $maxOffersPerSymbol for symbol ${symbol.value}"
        ).invalidNel
      } else {
        ().validNel
      }
    }

    def validateRateLimit(
      userId: UserId,
      recentOfferCount: Int,
      rateLimitPerMinute: Int
    ): ValidationResult[Unit] = {
      if (recentOfferCount >= rateLimitPerMinute) {
        BusinessRuleError(
          s"Rate limit exceeded: $recentOfferCount offers in the last minute (limit: $rateLimitPerMinute)"
        ).invalidNel
      } else {
        ().validNel
      }
    }

    def validateMarketHours(tradingSymbol: TradingSymbol): ValidationResult[Unit] = {
      // For now, assume all markets are always open
      // In a real implementation, you'd check market schedules
      ().validNel
    }
  }

  // Comprehensive validation functions
  object ComprehensiveValidation {
    
    def validateOfferCreationWithAccount(
      offer: Offer,
      account: TradingAccount,
      permission: TradingPermission,
      currentOfferCount: Int,
      currentSymbolOfferCount: Int,
      recentOfferCount: Int,
      currentDailyVolume: Amount,
      config: ValidationConfig
    ): ValidationResult[Offer] = {
      val requiredAmount = offer.price * offer.originalQuantity
      
      (
        OfferValidation.validateOfferCreation(offer),
        AccountValidation.validateAccountForOffer(offer.userId, account, requiredAmount, getCurrencyFromSymbol(offer.symbol)),
        PermissionValidation.validateFullTradingPermission(permission, offer.offerType, requiredAmount, currentDailyVolume),
        BusinessRuleValidation.validateUserOfferLimits(offer.userId, currentOfferCount, config.maxOffersPerUser),
        BusinessRuleValidation.validateSymbolOfferLimits(offer.userId, offer.symbol, currentSymbolOfferCount, config.maxOffersPerSymbol),
        BusinessRuleValidation.validateRateLimit(offer.userId, recentOfferCount, config.rateLimitPerMinute),
        BusinessRuleValidation.validateMarketHours(offer.symbol)
      ).mapN((validOffer, _, _, _, _, _, _) => validOffer)
    }

    def validateTradeExecution(
      buyOffer: Offer,
      sellOffer: Offer,
      buyerAccount: TradingAccount,
      sellerAccount: TradingAccount,
      tradeQuantity: Quantity,
      tradePrice: Price
    ): ValidationResult[Trade] = {
      val trade = Trade.create(buyOffer.symbol, buyOffer, sellOffer, tradePrice, tradeQuantity)
      val buyAmount = tradePrice * tradeQuantity
      
      (
        TradeValidation.validateOfferCompatibility(buyOffer, sellOffer),
        TradeValidation.validateTradeQuantity(buyOffer, sellOffer, tradeQuantity),
        TradeValidation.validateTradeAccounts(trade),
        AccountValidation.validateSufficientBalance(buyerAccount, buyAmount),
        // Seller should have sufficient asset quantity (in a real system)
        BusinessRuleValidation.validateMarketHours(trade.symbol)
      ).mapN((_, _, validTrade, _, _) => validTrade)
    }
  }

  // Configuration for validation rules
  case class ValidationConfig(
    maxOffersPerUser: Int = 100,
    maxOffersPerSymbol: Int = 10,
    rateLimitPerMinute: Int = 10,
    minOfferAmount: BigDecimal = BigDecimal("1.00"),
    maxOfferAmount: BigDecimal = BigDecimal("1000000.00")
  )

  // Helper functions
  private def getCurrencyFromSymbol(symbol: TradingSymbol): String = {
    // Simple parsing of EUR/USD format to get base currency
    // In a real implementation, this would be more sophisticated
    symbol.value.split("/").headOption.getOrElse("USD")
  }
}