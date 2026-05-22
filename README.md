# Build Production-Ready Idempotent APIs using Spring Boot, Redis & PostgreSQL

## Overview

This project demonstrates how to build a production-style Idempotent Payment API using:

* Java
* Spring Boot
* Redis
* PostgreSQL

The implementation focuses on:

* Preventing duplicate payment processing
* Handling retry requests safely
* Redis-based response caching
* Payment session architecture
* Distributed locking using Redis
* Race condition prevention
* Production-grade backend design

[![](https://markdown-videos-api.jorgenkh.no/youtube/8HLXQiKsW-U)](https://youtu.be/8HLXQiKsW-U)

# Why Idempotency Matters

In distributed systems, duplicate requests are extremely common.

Reasons include:

* User clicks payment button multiple times
* Mobile applications retry automatically
* Network timeout occurs
* API Gateway retries request
* Frontend retry mechanisms
* Temporary server/network failures

Without idempotency:

* Duplicate payments may happen
* Duplicate orders may get created
* Same transaction may process multiple times

# What is Idempotency?

An operation is called idempotent if performing it multiple times produces the same result.

Example:

| HTTP Method | Idempotent |
| ----------- | ---------- |
| GET         | Yes        |
| PUT         | Yes        |
| DELETE      | Mostly Yes |
| POST        | Usually No |

POST APIs are usually non-idempotent because they create resources.

Example:

```http
POST /payments
```

```json
{
  "amount": 1000,
  "receiver": "merchant_123"
}
```

If the same request executes multiple times:

* money may get deducted multiple times
* duplicate transactions may happen

# Important Engineering Insight

## Duplicate Detection ≠ Idempotency

Many beginners implement idempotency using payload hashing.

Example:

```text
hash(userId + amount + receiver)
```

This approach is dangerous.

Suppose:

* Same user sends same amount
* To same receiver
* Again intentionally

Backend may incorrectly reject legitimate payment.

This is why:

> Idempotency requires operation identity, not payload similarity.

# Final Architecture

This project uses a two-step payment flow.

## Step 1 - Create Payment Session

Client requests payment session.

```http
POST /payment-sessions
```

Backend returns:

```json
{
  "paymentSessionId": "ps_123"
}
```

The payment session identifies the business operation.

## Step 2 - Execute Payment

```http
POST /payments
```

Headers:

```http
Idempotency-Key: retry-key-001
```

Request Body:

```json
{
  "paymentSessionId": "ps_123",
  "amount": 1000,
  "receiver": "merchant_123"
}
```

# Component Responsibilities

| Component        | Responsibility                |
| ---------------- | ----------------------------- |
| paymentSessionId | Identifies business operation |
| idempotencyKey   | Handles retry safety          |
| Redis            | Fast caching & locking        |
| PostgreSQL       | Source of truth               |

# System Flow Diagram (Without Redis Locking)

```text
                ┌────────────────────┐
                │       Client       │
                └─────────┬──────────┘
                          │
                          │ Create Session
                          ▼
             ┌──────────────────────────┐
             │ POST /payment-sessions   │
             └─────────┬────────────────┘
                       │
                       │ Generate paymentSessionId
                       ▼
              ┌──────────────────────┐
              │ PostgreSQL Database  │
              │ status = PENDING     │
              └─────────┬────────────┘
                        │
                        │ Return session id
                        ▼
              ┌──────────────────────┐
              │ paymentSessionId     │
              └─────────┬────────────┘
                        │
                        │ Execute Payment
                        ▼
                ┌────────────────────┐
                │ POST /payments     │
                └─────────┬──────────┘
                          │
                          │ Check Redis Cache
                          ▼
                  ┌───────────────┐
                  │ Redis Cache   │
                  └──────┬────────┘
                         │
          ┌──────────────┴──────────────┐
          │                             │
          │ Cache Hit                   │ Cache Miss
          │                             │
          ▼                             ▼
  Return Cached Response      Validate Session Status
                                        │
                                        ▼
                              Process Payment
                                        │
                                        ▼
                              Save in PostgreSQL
                                        │
                                        ▼
                              Update Session Status
                                        │
                                        ▼
                              Store Response in Redis
                                        │
                                        ▼
                               Return Response
```

# System Flow Diagram

```text
                ┌────────────────────┐
                │       Client       │
                └─────────┬──────────┘
                          │
                          │ Create Session
                          ▼
             ┌──────────────────────────┐
             │ POST /payment-sessions   │
             └─────────┬────────────────┘
                       │
                       │ Generate paymentSessionId
                       ▼
              ┌──────────────────────┐
              │ PostgreSQL Database  │
              │ status = PENDING     │
              └─────────┬────────────┘
                        │
                        │ Return session id
                        ▼
              ┌──────────────────────┐
              │ paymentSessionId     │
              └─────────┬────────────┘
                        │
                        │ Execute Payment
                        ▼
                ┌────────────────────┐
                │ POST /payments     │
                └─────────┬──────────┘
                          │
                          │ Acquire Redis Lock
                          ▼
                  ┌───────────────┐
                  │ Redis Locking │
                  └──────┬────────┘
                         │
                         │ Check Redis Cache
                         ▼
                  ┌───────────────┐
                  │ Redis Cache   │
                  └──────┬────────┘
                         │
          ┌──────────────┴──────────────┐
          │                             │
          │ Cache Hit                   │ Cache Miss
          │                             │
          ▼                             ▼
  Return Cached Response      Validate Session Status
                                        │
                                        ▼
                              Process Payment
                                        │
                                        ▼
                              Save in PostgreSQL
                                        │
                                        ▼
                              Update Session Status
                                        │
                                        ▼
                              Store Response in Redis
                                        │
                                        ▼
                               Return Response
```

# Database Design

## payment_sessions

```sql
CREATE TABLE IF NOT EXISTS projects.payment_sessions
(
    id integer NOT NULL DEFAULT nextval('projects.payment_sessions_id_seq'::regclass),
    payment_session_id character varying(39) COLLATE pg_catalog."default" NOT NULL,
    status character varying(12) COLLATE pg_catalog."default" NOT NULL,
    created_at timestamp without time zone,
    CONSTRAINT payment_sessions_pkey PRIMARY KEY (id),
    CONSTRAINT payment_sessions_payment_session_id_key UNIQUE (payment_session_id)
);
```

## payments

```sql
CREATE TABLE IF NOT EXISTS projects.payments
(
    id integer NOT NULL DEFAULT nextval('projects.payments_id_seq'::regclass),
    transaction_id character varying(36) COLLATE pg_catalog."default" NOT NULL,
    receiver character varying(20) COLLATE pg_catalog."default" NOT NULL,
    amount numeric(6,2),
    payment_session_id character varying(39) COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT payments_pkey PRIMARY KEY (id)
);
```

# Redis Usage

Redis is used for:

* Fast response caching
* Retry handling
* Distributed locking
* Preventing concurrent execution

# Why Redis?

Advantages:

* Extremely fast
* In-memory storage
* TTL support
* Excellent for caching
* Great for distributed systems

# Redis Serialization

Redis does not understand Java objects directly.

Serialization converts:

```text
Java Object ↔ Redis Bytes
```

This project uses:

```java
GenericJackson2JsonRedisSerializer
```

which stores objects as JSON.

# Redis Configuration

```java
@Bean
public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
    return template;
}
```

# Race Condition Problem

Even with idempotency, concurrent requests may still create duplicate payments.

Example:

```text
Thread 1 → Redis MISS
Thread 2 → Redis MISS

Thread 1 → status=PENDING
Thread 2 → status=PENDING

Both process payment
```

Result:

* Duplicate payment
* Double transaction execution

# Important Engineering Insight

> Idempotency alone does not solve concurrency problems.

# Redis Distributed Locking

To prevent concurrent execution, this project uses Redis distributed locking.

# Locking Flow

```text
Acquire Redis Lock
        ↓
Check Redis Cache
        ↓
Validate Payment Session
        ↓
Process Payment
        ↓
Update DB
        ↓
Store Response in Redis
        ↓
Release Lock
```

# Security Discussion

Suppose attacker changes:

```http
Idempotency-Key
```

using tools like:

* Burp Suite
* Postman

The system still remains safe because:

* payment session status is authoritative
* business state validation prevents duplicate execution

If payment already completed:

```text
status = COMPLETED
```

backend rejects further processing.

# Important Final Insight

> Idempotency keys prevent accidental retries.
> Business state prevents malicious duplicate execution.

# API Endpoints

## Create Payment Session

```http
POST /payment-sessions
```

Response:

```json
{
  "paymentSessionId": "ps_123"
}
```

## Execute Payment

```http
POST /payments
```

Headers:

```http
Idempotency-Key: retry-key-001
```

Request Body:

```json
{
  "paymentSessionId": "ps_123",
  "amount": 1000,
  "receiver": "merchant_123"
}
```

# Tech Stack

* Java
* Spring Boot
* Redis
* PostgreSQL
* Spring Data JPA
* Spring Data Redis

# Production Improvements

Possible enhancements:

* Lua script based atomic lock release
* Redis Redisson implementation
* Optimistic locking
* SELECT FOR UPDATE
* Kafka event publishing
* Retry queue
* Audit logging
* Payment gateway integration
* Fraud detection layer

# Conclusion

This project demonstrates how production grade payment systems:

* Prevent duplicate processing
* Handle retries safely
* Use Redis for distributed coordination
* Maintain business correctness
* Solve concurrency issues

This implementation provides a strong foundation for understanding:

* Distributed systems
* Backend architecture
* Payment system design
* Microservices concurrency handling
* Idempotent API design

## Support
If this helped you, consider supporting my tutorials

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-Support-yellow?logo=buymeacoffee)](https://buymeacoffee.com/nakulmitra)