# Vortex Micro-Batching Library - Design Document

## Overview

Vortex is a lightweight micro-batching library that groups individual requests into batches and dispatches them to a backend. The library uses Java 21 virtual threads for efficient I/O-bound operations and provides a simple, clean API.

## Core Design Principles

1. **Separation of Concerns**: Client API is async, Backend implementation is synchronous
2. **Virtual Threads**: Leverage Java 21 virtual threads for efficient I/O operations
3. **Smart Batching**: Trigger on batch size OR time (whichever comes first)
4. **Simplicity**: Backend implementers write simple, synchronous code
5. **Non-blocking Client API**: Clients get async results via CompletableFuture

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Code                             │
│                                                                 │
│  CompletableFuture<BatchResult> future =                        │
│      batcher.submit("item");                                    │
│                                                                 │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       │ submit(item) → CompletableFuture
                       │
┌──────────────────────▼──────────────────────────────────────────┐
│                    MicroBatcher                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Request Queue (BlockingQueue<PendingRequest>)           │  │
│  │  - Stores incoming requests with their CompletableFuture │  │
│  └──────────────────┬───────────────────────────────────────┘  │
│                      │                                            │
│  ┌──────────────────▼───────────────────────────────────────┐  │
│  │  Batch Processor (Virtual Thread)                         │  │
│  │  - Polls queue for requests                               │  │
│  │  - Groups into batches (size or time-based)              │  │
│  │  - Dispatches batches to backend                          │  │
│  └──────────────────┬───────────────────────────────────────┘  │
│                      │                                            │
│                      │ dispatch(batch)                            │
│                      │                                            │
└──────────────────────┼──────────────────────────────────────────┘
                       │
                       │ Synchronous call (runs on virtual thread)
                       │
┌──────────────────────▼──────────────────────────────────────────┐
│                    Backend Implementation                        │
│                                                                 │
│  BatchResult dispatch(List<T> batch) throws Exception {         │
│      // Your synchronous code here                             │
│      // Can be blocking I/O (HTTP, DB, etc.)                   │
│      // Runs on virtual thread - blocking is efficient         │
│      return new BatchResult<>(successes, failures);             │
│  }                                                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Request Flow

### 1. Client Submits Request

```java
// Client code
CompletableFuture<BatchResult<String>> future = batcher.submit("item-1");
```

**What happens:**
- Client calls `batcher.submit(item)`
- MicroBatcher creates a `PendingRequest` containing:
  - The item data
  - A `CompletableFuture<BatchResult<T>>` for the client
- Request is enqueued in the internal queue
- `CompletableFuture` is returned immediately to client (non-blocking)

### 2. Batch Processor Groups Requests

**What happens:**
- A virtual thread (batch processor) continuously polls the queue
- Collects requests into batches based on:
  - **Batch Size**: When batch reaches configured size (e.g., 10 items)
  - **Linger Time**: When configured time elapses (e.g., 100ms)
  - **Whichever comes first**
- Once a batch is ready, it's dispatched to the backend

### 3. Backend Execution

**What happens:**
- Batch is dispatched to your `Backend.dispatch(List<T> batch)` method
- This runs on a **virtual thread** (efficient for I/O)
- Your backend code can be **blocking** (HTTP calls, DB queries, etc.)
- You return a `BatchResult` synchronously
- No need to manage `CompletableFuture` in your backend code

### 4. Result Distribution

**What happens:**
- MicroBatcher receives the `BatchResult` from your backend
- Maps results back to individual requests
- Completes each request's `CompletableFuture` with the appropriate result
- Client's `CompletableFuture` completes with their result

## Key Design Decisions

### Why Synchronous Backend Interface?

**Problem**: If backend returned `CompletableFuture`, implementers would need to:
- Manage async execution themselves
- Handle thread pools
- Deal with async complexity

**Solution**: Backend is synchronous, MicroBatcher handles async:
- Backend implementers write simple, blocking code
- MicroBatcher runs backend on virtual threads
- Virtual threads make blocking I/O efficient
- Simpler, cleaner backend code

### Why CompletableFuture for Client API?

**Problem**: If `submit()` was synchronous, clients would:
- Block waiting for batching and backend processing
- Lose benefits of async processing

**Solution**: Client API returns `CompletableFuture`:
- Non-blocking for clients
- Can use callbacks: `future.thenAccept(result -> ...)`
- Can compose: `CompletableFuture.allOf(...)`
- Can wait if needed: `future.get()`

### Virtual Threads Strategy

**Why Virtual Threads?**
- Perfect for I/O-bound operations (HTTP, DB, file I/O)
- Very lightweight (millions can exist)
- Blocking operations don't waste platform threads
- Backend can be blocking without performance penalty

**How it works:**
- Each batch dispatch runs on a separate virtual thread
- Backend blocking operations (e.g., `httpClient.send()`) park the virtual thread
- Platform thread is freed for other virtual threads
- When I/O completes, virtual thread resumes

## Example Flow

```
Time    Client Thread          MicroBatcher Thread          Backend (Virtual Thread)
─────────────────────────────────────────────────────────────────────────────────────
T0      submit("item-1") ────> Enqueue request
        returns Future ────────┐
                                │
T1      submit("item-2") ────> Enqueue request
        returns Future          │
                                │
T2      submit("item-3") ────> Enqueue request
        returns Future          │
                                │
                                │ Batch Processor:
                                │ - Polls queue
                                │ - Collects 3 items
                                │ - Batch ready (size=3 or time elapsed)
                                │
                                │ dispatch([item-1, item-2, item-3])
                                │                    │
                                │                    └──> Backend.dispatch()
                                │                         (runs on virtual thread)
                                │                         - Makes HTTP calls
                                │                         - Blocks on I/O
                                │                         - Returns BatchResult
                                │
                                │ Process results
                                │ - Map to individual requests
                                │ - Complete futures
                                │
T3      Future completes <─────┘
        result available
```

## API Contracts

### Client API (MicroBatcher)

```java
public CompletableFuture<BatchResult<T>> submit(T data)
```

- **Input**: Single item to be batched
- **Output**: `CompletableFuture` that completes when item is processed
- **Behavior**: Non-blocking, returns immediately
- **Thread**: Runs on client's thread (doesn't block)

### Backend API (Backend Interface)

```java
BatchResult<T> dispatch(List<T> batch) throws Exception
boolean shouldReplaySuccesses(BatchResult<T> result)  // Optional, default returns false
```

- **dispatch()**:
  - **Input**: List of items in the batch
  - **Output**: `BatchResult` with successes and failures
  - **Behavior**: Synchronous, can block
  - **Thread**: Runs on virtual thread (blocking is efficient)

- **shouldReplaySuccesses()** (default method):
  - **Input**: The batch result from dispatch
  - **Output**: `true` if successful items should be replayed, `false` otherwise
  - **Default**: Returns `false` (no replay)
  - **Override**: Backends can override to customize replay behavior
  - **Use Cases**:
    - Atomic backends (e.g., DB with unique constraints) → return `true` when failures exist
    - Backends that handle everything internally → return `false`

## Benefits of This Design

1. **Simple Backend Code**: Write synchronous, blocking code
2. **Efficient I/O**: Virtual threads handle blocking efficiently
3. **Non-blocking Clients**: Clients get async API
4. **Automatic Batching**: Library handles grouping and timing
5. **Resource Efficient**: Virtual threads are lightweight
6. **Error Handling**: Exceptions from backend are caught and converted to failures

## Configuration Options

- **batchSize**: Maximum items per batch
- **lingerTime**: Maximum time to wait before dispatching
- **atomicCommit**: If true, batch fails if any item fails
- **maxConcurrency**: Maximum concurrent batch dispatches (less relevant with virtual threads)
- **autoReplaySuccesses**: Default replay behavior (used if backend doesn't override `shouldReplaySuccesses()`)

## Metrics & Observability

The library tracks:
- Requests submitted
- Batches dispatched
- Success/failure counts
- Queue depth
- Latencies (wait time, dispatch time)

All metrics exposed via Micrometer for integration with monitoring systems.

## Replay Strategy

The library supports intelligent replay decisions:

1. **Backend-Driven**: Backend implements `shouldReplaySuccesses()` to decide based on result
   - Atomic backends can replay when failures occur (e.g., unique constraint violations)
   - Backends that handle everything internally can opt-out

2. **Config Fallback**: `autoReplaySuccesses` config provides default behavior
   - Only used if backend uses default `shouldReplaySuccesses()` implementation
   - Allows global policy when backends don't customize

3. **Replay Logic**: 
   - Only replays when batch has both successes and failures
   - Successful items are re-submitted once
   - Failures are returned immediately to clients

## Summary

**Client Side**: Async API with `CompletableFuture` - non-blocking, composable
**Backend Side**: Synchronous API - simple, blocking code is fine
**Execution**: Virtual threads bridge the gap - efficient I/O handling
**Batching**: Automatic - size or time based, whichever comes first
**Replay**: Backend-driven decision with config fallback - flexible and intelligent

This design provides the best of both worlds: simple backend code and async client API, with intelligent replay strategies.

