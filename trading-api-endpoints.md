# OBP-Trading REST API Endpoints

## Base Path
All endpoints use the OBP convention with bank, account, and view context:
```
/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID
```

## Authentication
All endpoints require valid JWT tokens obtained from OBP-OIDC. The token must include appropriate permissions for the specified bank, account, and view.

## Content Type
- Request: `application/json`
- Response: `application/json`

---

## Offer Management

### 1. Create Trading Offer
**POST** `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers`

Creates a new trading offer (buy or sell intention).

**Request Body:**
```json
{
  "offer_type": "buy|sell",
  "asset_code": "BTC",
  "asset_amount": "1.5",
  "price_currency": "USD",
  "price_amount": "45000.00",
  "expiry_datetime": "2024-12-31T23:59:59Z",
  "minimum_fill": "0.1",
  "settlement_account_id": "account_123"
}
```

**Response (201 Created):**
```json
{
  "offer_id": "offer_789",
  "status": "active",
  "created_at": "2024-01-15T10:30:00Z",
  "offer_details": {
    "offer_type": "buy",
    "asset_code": "BTC",
    "asset_amount": "1.5",
    "price_currency": "USD",
    "price_amount": "45000.00",
    "expiry_datetime": "2024-12-31T23:59:59Z",
    "minimum_fill": "0.1",
    "settlement_account_id": "account_123"
  },
  "account_info": {
    "bank_id": "BANK_ID",
    "account_id": "ACCOUNT_ID",
    "view_id": "VIEW_ID"
  }
}
```

### 2. Get Account Trading Offers
**GET** `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers`

Lists all trading offers for the account (filtered by view permissions).

**Query Parameters:**
- `status`: `active|completed|cancelled|expired` (optional)
- `offer_type`: `buy|sell` (optional)
- `asset_code`: Filter by asset (optional)
- `limit`: Number of results (default: 50)
- `offset`: Pagination offset (default: 0)

**Response (200 OK):**
```json
{
  "offers": [
    {
      "offer_id": "offer_789",
      "status": "active",
      "created_at": "2024-01-15T10:30:00Z",
      "offer_details": {
        "offer_type": "buy",
        "asset_code": "BTC",
        "asset_amount": "1.5",
        "price_currency": "USD",
        "price_amount": "45000.00",
        "expiry_datetime": "2024-12-31T23:59:59Z",
        "minimum_fill": "0.1"
      }
    }
  ],
  "pagination": {
    "total": 1,
    "limit": 50,
    "offset": 0
  }
}
```

### 3. Get Specific Trading Offer
**GET** `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers/OFFER_ID`

Retrieves details of a specific trading offer.

**Response (200 OK):**
```json
{
  "offer_id": "offer_789",
  "status": "active",
  "created_at": "2024-01-15T10:30:00Z",
  "updated_at": "2024-01-15T10:30:00Z",
  "offer_details": {
    "offer_type": "buy",
    "asset_code": "BTC",
    "asset_amount": "1.5",
    "filled_amount": "0.3",
    "remaining_amount": "1.2",
    "price_currency": "USD",
    "price_amount": "45000.00",
    "expiry_datetime": "2024-12-31T23:59:59Z",
    "minimum_fill": "0.1"
  },
  "account_info": {
    "bank_id": "BANK_ID",
    "account_id": "ACCOUNT_ID",
    "view_id": "VIEW_ID"
  },
  "executions": [
    {
      "execution_id": "exec_123",
      "executed_amount": "0.3",
      "executed_price": "45000.00",
      "executed_at": "2024-01-15T11:00:00Z",
      "counterpart_offer_id": "offer_456"
    }
  ]
}
```

### 4. Update Trading Offer
**PUT** `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers/OFFER_ID`

Updates an existing trading offer (limited to price and expiry).

**Request Body:**
```json
{
  "price_amount": "46000.00",
  "expiry_datetime": "2024-12-31T23:59:59Z"
}
```

**Response (200 OK):** Same as Get Specific Trading Offer

### 5. Cancel Trading Offer
**DELETE** `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers/OFFER_ID`

Cancels an active trading offer.

**Response (200 OK):**
```json
{
  "offer_id": "offer_789",
  "status": "cancelled",
  "cancelled_at": "2024-01-15T12:00:00Z",
  "message": "Offer successfully cancelled"
}
```

---

## Trade History

### 6. Get Account Trading History
**GET** `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/trades`

Lists completed trades for the account.

**Query Parameters:**
- `asset_code`: Filter by asset (optional)
- `start_date`: Start date filter (optional)
- `end_date`: End date filter (optional)
- `limit`: Number of results (default: 50)
- `offset`: Pagination offset (default: 0)

**Response (200 OK):**
```json
{
  "trades": [
    {
      "trade_id": "trade_123",
      "executed_at": "2024-01-15T11:00:00Z",
      "asset_code": "BTC",
      "trade_type": "buy",
      "executed_amount": "0.3",
      "executed_price": "45000.00",
      "total_value": "13500.00",
      "currency": "USD",
      "fees": {
        "trading_fee": "13.50",
        "currency": "USD"
      },
      "my_offer_id": "offer_789",
      "counterpart_offer_id": "offer_456",
      "settlement_status": "completed"
    }
  ],
  "pagination": {
    "total": 1,
    "limit": 50,
    "offset": 0
  }
}
```

### 7. Get Specific Trade Details
**GET** `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/trades/TRADE_ID`

Retrieves detailed information about a specific trade.

**Response (200 OK):**
```json
{
  "trade_id": "trade_123",
  "executed_at": "2024-01-15T11:00:00Z",
  "asset_code": "BTC",
  "trade_type": "buy",
  "executed_amount": "0.3",
  "executed_price": "45000.00",
  "total_value": "13500.00",
  "currency": "USD",
  "fees": {
    "trading_fee": "13.50",
    "currency": "USD"
  },
  "offers": {
    "my_offer": {
      "offer_id": "offer_789",
      "original_amount": "1.5",
      "offer_price": "45000.00"
    },
    "counterpart_offer": {
      "offer_id": "offer_456",
      "original_amount": "0.5",
      "offer_price": "45000.00"
    }
  },
  "settlement": {
    "status": "completed",
    "settlement_id": "settlement_789",
    "settlement_at": "2024-01-15T11:01:00Z"
  }
}
```

---

## Market Data

### 8. Get Market Summary
**GET** `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/market`

Provides market overview and statistics.

**Query Parameters:**
- `asset_codes`: Comma-separated list of assets (optional)

**Response (200 OK):**
```json
{
  "market_data": [
    {
      "asset_code": "BTC",
      "currency": "USD",
      "last_price": "45250.00",
      "price_change_24h": "1250.00",
      "price_change_percent_24h": "2.84%",
      "volume_24h": "150.75",
      "high_24h": "46000.00",
      "low_24h": "43800.00",
      "active_offers": {
        "buy_offers": 25,
        "sell_offers": 18,
        "spread": "100.00"
      }
    }
  ],
  "updated_at": "2024-01-15T12:30:00Z"
}
```

### 9. Get Order Book
**GET** `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/market/ASSET_CODE/orderbook`

Provides current order book for a specific asset.

**Query Parameters:**
- `depth`: Number of price levels (default: 20)

**Response (200 OK):**
```json
{
  "asset_code": "BTC",
  "currency": "USD",
  "timestamp": "2024-01-15T12:30:00Z",
  "bids": [
    {
      "price": "45200.00",
      "amount": "2.5",
      "offer_count": 3
    },
    {
      "price": "45100.00", 
      "amount": "1.8",
      "offer_count": 2
    }
  ],
  "asks": [
    {
      "price": "45300.00",
      "amount": "1.2",
      "offer_count": 1
    },
    {
      "price": "45400.00",
      "amount": "3.1",
      "offer_count": 4
    }
  ]
}
```

---

## Account Trading Info

### 10. Get Trading Account Status
**GET** `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/status`

Provides trading-related account information and permissions.

**Response (200 OK):**
```json
{
  "account_info": {
    "bank_id": "BANK_ID",
    "account_id": "ACCOUNT_ID",
    "view_id": "VIEW_ID"
  },
  "trading_enabled": true,
  "permissions": {
    "can_create_offers": true,
    "can_cancel_offers": true,
    "can_view_trades": true,
    "can_view_market_data": true
  },
  "limits": {
    "max_offer_amount": "100000.00",
    "currency": "USD",
    "daily_trading_limit": "50000.00",
    "available_balance": "25000.00"
  },
  "statistics": {
    "active_offers": 3,
    "completed_trades_today": 2,
    "total_trading_volume_today": "15000.00"
  }
}
```

---

## Error Responses

All endpoints may return standard HTTP error codes with descriptive messages:

**400 Bad Request:**
```json
{
  "error": "INVALID_REQUEST",
  "message": "Invalid offer amount: must be greater than 0",
  "details": {
    "field": "asset_amount",
    "value": "-1.5"
  }
}
```

**401 Unauthorized:**
```json
{
  "error": "UNAUTHORIZED",
  "message": "Invalid or expired JWT token"
}
```

**403 Forbidden:**
```json
{
  "error": "INSUFFICIENT_PERMISSIONS", 
  "message": "View 'read-only' does not allow creating trading offers"
}
```

**404 Not Found:**
```json
{
  "error": "OFFER_NOT_FOUND",
  "message": "Offer with ID 'offer_789' not found"
}
```

**409 Conflict:**
```json
{
  "error": "INSUFFICIENT_FUNDS",
  "message": "Account balance insufficient for trading offer",
  "details": {
    "required": "45000.00",
    "available": "25000.00",
    "currency": "USD"
  }
}
```

**500 Internal Server Error:**
```json
{
  "error": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "request_id": "req_12345"
}
```

## Rate Limiting

All endpoints are subject to rate limiting:
- **Standard endpoints**: 100 requests per minute per user
- **Market data endpoints**: 300 requests per minute per user
- **Order book endpoints**: 60 requests per minute per user

Rate limit headers are included in responses:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1642248000
```
