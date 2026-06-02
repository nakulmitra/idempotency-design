# Why Idempotency Alone Is Not Enough

## Introduction

In Part 1 of this project, we implemented an Idempotent Payment API using:

* Spring Boot
* Redis
* PostgreSQL

The goal was to prevent duplicate payment processing when the same request is retried multiple times.

[![](https://markdown-videos-api.jorgenkh.no/youtube/IQR5AogKX8o)](https://youtu.be/IQR5AogKX8o)

The implementation worked correctly for retry scenarios:

```text
Request 1
    ↓
Process Payment
    ↓
Store Response in Redis
    ↓
Return SUCCESS

Request 2 (Retry)
    ↓
Fetch Response from Redis
    ↓
Return SUCCESS
```

This prevents duplicate execution when the same request arrives after the first request has already completed.

However, there is a critical flaw that still exists.

# The Hidden Problem

The problem occurs when multiple requests arrive simultaneously.

Consider the following payment request:

```json
{
  "paymentSessionId": "ps_123",
  "amount": 1000,
  "receiver": "merchant_123"
}
```

Headers:

```http
Idempotency-Key: retry-key-001
```

Now imagine two requests arriving at exactly the same moment.

```text
Request A
Request B
```

At this point Redis does not yet contain any cached response.

Therefore:

```text
Request A → Redis MISS
Request B → Redis MISS
```

Both requests continue execution.

# Understanding the Race Condition

A race condition occurs when multiple threads access and modify shared resources simultaneously and the final result depends on execution timing.

In our payment system:

```text
Request A
Request B
```

execute concurrently.

Timeline:

```text
Request A → Redis MISS
Request B → Redis MISS

Request A → Read Session Status
Request B → Read Session Status

Request A → Status = PENDING
Request B → Status = PENDING

Request A → Process Payment
Request B → Process Payment
```

Result:

```text
Two payments created
```

Even though we implemented idempotency.

# Why Did Idempotency Fail?

Many developers mistakenly believe, Idempotency automatically solves concurrency problems.

This is incorrect.

Idempotency and concurrency control solve different problems.

# What Idempotency Actually Solves

Idempotency protects against duplicate retries.

Example:

```text
Client sends request
       ↓
Payment succeeds
       ↓
Response lost
       ↓
Client retries
```

Without idempotency:

```text
Payment processed twice
```

With idempotency:

```text
Cached response returned
```

No duplicate processing occurs.

# What Idempotency Does NOT Solve

Idempotency does not prevent multiple requests from executing simultaneously.

Example:

```text
Request A arrives
Request B arrives

Both execute together
```

If neither request has completed yet:

```text
No cached response exists
```

Therefore both requests continue processing.

This is known as the race window.

# Visualizing the Race Window

Consider the following implementation:

```java
PaymentResponse response =
        redisTemplate.opsForValue()
                     .get(redisKey);

if(response != null){
    return response;
}
```

At first glance this looks correct.

However, the dangerous period exists between:

```text
Read Redis
      ↓
Write Redis
```

During this window another request may enter the same code path.

```text
Thread 1
    ↓
Redis MISS

                Thread 2
                    ↓
                Redis MISS

Thread 1
    ↓
Process Payment

                Thread 2
                    ↓
                Process Payment
```

Result:

```text
Duplicate transaction
```

# Solution: Distributed Locking

To solve this problem we must ensure that only one request can process a payment session at any given time.

This is where distributed locking becomes useful.

The idea is simple:

```text
Acquire Lock
      ↓
Execute Critical Section
      ↓
Release Lock
```

Only one request can own the lock.

All other requests must wait or fail.

# Why Redis?

Redis provides atomic operations that make distributed locking simple and efficient.

The lock is created using:

```text
SET lock_key requestId NX EX 300
```

# Understanding NX

NX means:

```text
Only create key if it does not already exist
```

Example:

```text
Request A
    ↓
Create Lock
```

Success:

```text
LOCK_PAYMENT_ps123
```

Now Request B tries:

```text
Request B
    ↓
Create Same Lock
```

Redis rejects it because the key already exists.

# Understanding EX

EX specifies lock expiration.

Example:

```text
EX 300
```

means:

```text
Automatically delete lock after 300 seconds
```

This prevents deadlocks.

# Why Lock Expiration Is Important

Imagine:

```text
Request acquires lock
```

Then:

```text
Application crashes
```

Without expiration:

```text
Lock remains forever
```

Future requests become blocked.

With expiration:

```text
Redis automatically removes lock
```

System recovers automatically.

# Lock Ownership Problem

Another important challenge exists.

Suppose:

```text
Thread A acquires lock
```

Lock expires.

Now:

```text
Thread B acquires same lock
```

If Thread A later executes:

```java
redisTemplate.delete(lockKey);
```

it may accidentally delete Thread B's lock. To avoid this, each lock stores an owner identifier.

Example:

```text
Lock Value = requestId
```

Before deleting:

```java
if(requestId.equals(currentLockOwner)){
    deleteLock();
}
```

Only the owner can release the lock.

# Limitation of GET + DELETE

Our implementation performs:

```java
GET lock
DELETE lock
```

in two separate operations. Although sufficient for learning purposes, it is not fully atomic.

A production-grade implementation usually uses:

* Lua Scripts
* Redisson
* Redlock Algorithm

to perform verification and deletion atomically.

# Relationship Between Locking and Idempotency

One of the most common interview questions is:

> If we already have locking, why do we still need idempotency?

Because they solve different problems.

### Locking

Prevents:

```text
Concurrent execution
```

Example:

```text
Request A
Request B

arrive simultaneously
```

Without locking:

```text
Duplicate payment
```

### Idempotency

Prevents:

```text
Duplicate retries
```

Example:

```text
Payment succeeds
Response lost
Client retries
```

Without idempotency:

```text
Payment executes again
```

With idempotency:

```text
Return cached response
```

# Improving User Experience with Polling

## Problem with Basic Locking

Our initial implementation uses the following approach:

```java
if (!lockAcquired) {

    throw new RuntimeException(
            "Payment already processing");
}
```

This successfully prevents duplicate payment execution because only one request can acquire the lock.

However, there is a drawback.

Consider the following scenario:

```text
Request A
    ↓
Acquire Lock
    ↓
Process Payment

Request B
    ↓
Lock Acquisition Failed
    ↓
Exception Returned
```

The payment is processed correctly, but the second request receives an error response.

From a business perspective this is not ideal.

The second request is not actually invalid.

It is simply a retry of the same business operation.

# Why This Reduces the Value of Idempotency

Recall the purpose of an idempotency key.

The goal is:

> Multiple retries of the same operation should receive the same result.

With the current implementation:

```text
Request A
    ↓
SUCCESS

Request B
    ↓
Payment already processing
```

Different requests receive different outcomes.

This defeats part of the purpose of idempotency.

Ideally we want:

```text
Request A
    ↓
SUCCESS

Request B
    ↓
SUCCESS
```

Both requests should receive the same response.

# Better Approach: Wait for the Existing Request

Instead of immediately throwing an exception when the lock is unavailable, the second request can wait for the first request to finish.

The idea is:

```text
Request A
    ↓
Acquire Lock
    ↓
Process Payment
    ↓
Store Response In Redis
    ↓
Release Lock


Request B
    ↓
Lock Not Available
    ↓
Wait
    ↓
Check Redis
    ↓
Response Found
    ↓
Return Same Response
```

Now both requests receive identical results.

# Polling-Based Implementation

A simple approach is to periodically check Redis for the response generated by the request currently holding the lock.

Example:

```java
private PaymentResponse waitForTheResponse(String redisKey) {
	int retries = 10;
	
	while(retries-- > 0) {
		PaymentResponse response = (PaymentResponse) redisTemplate.opsForValue().get(redisKey);
		
		if(response != null) {
			return response;
		}
		
		try {
			Thread.sleep(1000);
		}catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	
	return null;
}
```

# Updated Lock Handling Logic

Instead of throwing an exception immediately:

```java
if (!lockAcquired) {

    throw new RuntimeException(
            "Payment already processing");
}
```

we can use:

```java
if (!lockAcquired) {

    PaymentResponse response =
            waitForResponse(redisKey);

    if (response != null) {
        return response;
    }

    throw new RuntimeException(
            "Unable to process payment");
}
```

# Polling Flow Diagram

```text
                   Request A
                       │
                       ▼
               Acquire Lock
                       │
                       ▼
               Process Payment
                       │
                       ▼
           Store Response In Redis
                       │
                       ▼
                 Release Lock


                   Request B
                       │
                       ▼
            Lock Not Available
                       │
                       ▼
                  Poll Redis
                       │
         ┌─────────────┴─────────────┐
         │                           │
         │ Response Missing          │
         │                           │
         ▼                           ▼
       Wait 1000ms             Response Found
         │                           │
         └─────────────►─────────────┘
                       │
                       ▼
             Return Same Response
```

Now both requests receive:

```json
{
  "transactionId": "txn_123",
  "status": "SUCCESS"
}
```

which aligns with the true purpose of idempotency.

# Benefits of Polling

### Better User Experience

Users receive the same successful response instead of an error.

### Preserves Idempotency Guarantees

All retries for the same operation receive identical results.

### Prevents Duplicate Execution

The distributed lock still ensures that only one request performs the payment processing logic.

### Simple to Implement

No additional infrastructure is required beyond Redis.

# Limitations of Polling

Polling is easy to understand and works well for demonstration purposes, but it has some drawbacks:

### Increased Wait Time

Retry requests may need to wait until the first request completes.

### Additional Redis Reads

Multiple polling attempts generate extra Redis traffic.

### Not Ideal for Long Running Operations

If payment processing takes a long time, polling can become inefficient.

# Production Alternatives

Large-scale systems often use more advanced techniques such as:

* Redis Pub/Sub
* Event-Driven Architecture
* Kafka-based Notifications
* CompletableFuture
* Reactive Streams
* Request Coalescing
* Redisson Synchronizers

These approaches allow waiting requests to be notified immediately when processing completes instead of repeatedly polling Redis.

# Final Architecture

```text
Client Request
        ↓
Acquire Redis Lock
        ↓
Check Redis Cache
        ↓
Validate Session
        ↓
Process Payment
        ↓
Store Response in Redis
        ↓
Update Database
        ↓
Release Lock
        ↓
Return Response
```

Concurrent request:

```text
Client Request
        ↓
Lock Not Available
        ↓
Wait For Response
        ↓
Read Cached Result
        ↓
Return Same Response
```

# Key Takeaways

### Idempotency

Protects against:

```text
Duplicate retries
```

### Distributed Locking

Protects against:

```text
Concurrent execution
```

### Business State Validation

Protects against:

```text
Malicious duplicate processing
```

A reliable payment system requires all three mechanisms working together:

```text
Idempotency
      +
Distributed Locking
      +
Business State Validation
```

This combination forms the foundation of many modern payment platforms, order processing systems, and distributed applications.

> Previous: [Build Production-Ready Idempotent APIs using Spring Boot, Redis & PostgreSQL](https://github.com/nakulmitra/idempotency-design/blob/master/README.md)