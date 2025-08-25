# OBP-Trading Core Concepts

## Overview

OBP-Trading builds on the foundational concepts from the Open Bank Project ecosystem, extending them to support trading functionality. This document explains the key concepts and their role in the trading system.

## Core OBP Concepts in Trading Context

### Bank
**Purpose**: Banks provide organizational boundaries and role-based control over groups of accounts.

In the trading context:
- Different banks can have different trading rules, permissions, and configurations
- Banks can control which trading features are available to their account holders
- Risk management and compliance can be implemented at the bank level
- Banks can have different fee structures for trading operations
- Regulatory requirements can be enforced per bank jurisdiction

**Example**: A retail bank might allow basic spot trading, while an investment bank might support complex derivatives trading.

### Account
**Purpose**: Accounts serve as the fundamental unit for trading operations, linking all offers and trades to specific financial accounts.

In the trading context:
- All trading offers are created from and executed against specific accounts
- Account balances determine available trading funds
- Trade settlement occurs between accounts
- Trading history and positions are tracked per account
- Risk limits and trading permissions are enforced at the account level

**Example**: A user might have multiple accounts (checking, savings, investment) with different trading capabilities and balance requirements.

### View
**Purpose**: Views provide granular permission control, allowing different levels of access to account trading functionality based on roles and relationships.

In the trading context:
- Account owners might have full trading permissions (create, cancel, execute offers)
- Account managers might have read-only access to trading activity
- Compliance officers might have audit-only access to trading records
- Family members might have limited trading permissions on shared accounts
- Different views can expose different sets of trading data and operations

**Example**: A corporate account might have:
- `owner` view: Full trading access for account executives
- `manager` view: Approval rights for large trades
- `auditor` view: Read-only access to all trading records
- `limited` view: Basic trading with monetary limits

## API Endpoint Structure

All OBP-Trading endpoints follow the OBP convention:
```
/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/...
```

This structure ensures:
- **Bank-level control**: Trading features can be enabled/disabled per bank
- **Account-level isolation**: All trading operations are tied to specific accounts
- **View-level permissions**: Fine-grained access control for different user roles
- **Consistent patterns**: Follows established OBP API conventions
- **Future extensibility**: Can easily add bank-specific or account-specific trading features

## Trading-Specific Concepts

### Offers
- Trading intentions created by account holders
- Linked to specific accounts for fund validation and settlement
- Subject to view permissions for creation, modification, and visibility

### Trades
- Executed matches between compatible offers
- Settlement occurs between the involved accounts
- Trading history tracked per account and accessible via appropriate views

### Market Data
- Real-time price feeds and trading statistics
- May be filtered based on bank policies and view permissions
- Can include bank-specific or account-specific trading analytics

## Security and Compliance

The Bank/Account/View structure provides multiple layers for:
- **Authentication**: User identity verification
- **Authorization**: Permission checking at bank, account, and view levels
- **Audit trails**: Complete tracking of who performed what trading operations
- **Regulatory compliance**: Bank-level and jurisdiction-specific rule enforcement
- **Risk management**: Account-level position limits and bank-level exposure controls

## Integration Benefits

This structure enables seamless integration with existing OBP services:
- **OBP-API**: Account balance validation, transaction execution
- **OBP-OIDC**: Authentication and role-based permissions
- **Existing account management**: Leverage established account structures and permissions