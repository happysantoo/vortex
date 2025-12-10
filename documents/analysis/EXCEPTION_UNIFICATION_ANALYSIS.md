# Exception Unification Analysis: BackpressureException vs RejectedExecutionException

**Date**: December 6, 2025  
**Version**: 0.0.8  
**Question**: Why do we need both `BackpressureException` and `RejectedExecutionException`? Can they be unified?

---

## Current State

### BackpressureException
- **When**: Backpressure check detects pressure **before** queue offer
- **Trigger**: Backpressure level >= threshold (e.g., queue depth > 70%)
- **Requires**: Backpressure provider + strategy configured
- **Information**: Rich metadata (level, threshold, source)
- **Timing**: Proactive (early detection)

### RejectedExecutionException
- **When**: Queue `offer()` fails (queue is actually full)
- **Trigger**: Queue at capacity
- **Requires**: Nothing (always possible)
- **Information**: Simple message only
- **Timing**: Reactive (during queue operation)

---

## Analysis: Can They Be Unified?

### Argument FOR Unification

**1. Same Application Handling**
Both exceptions mean "can't accept item right now" and applications handle them identically:
```java
// Current code - handles both the same way
if (error instanceof BackpressureException || 
    error instanceof RejectedExecutionException) {
    storeToOverflow(item);
}
```

**2. Same Root Cause**
Both ultimately indicate capacity issues:
- `BackpressureException`: System is under pressure (queue approaching capacity)
- `RejectedExecutionException`: Queue is at capacity

**3. Simpler API**
One exception type is easier to understand and handle:
```java
// Unified approach
if (error instanceof CapacityException) {
    storeToOverflow(item);
}
```

**4. Consistent Semantics**
Both represent rejection due to capacity constraints, just at different stages.

---

### Argument AGAINST Unification

**1. Different Timing and Semantics**
- `BackpressureException`: **Proactive** - "System is under pressure, rejecting early"
- `RejectedExecutionException`: **Reactive** - "Queue is full, can't accept"

**2. Different Information Needs**
- `BackpressureException`: Rich metadata (level, threshold, source) for monitoring/alerting
- `RejectedExecutionException`: Simple message is sufficient

**3. Different Configuration Requirements**
- `BackpressureException`: Only when backpressure is configured
- `RejectedExecutionException`: Always possible, even without backpressure

**4. Different Use Cases**
- `BackpressureException`: Can come from any source (queue, connection pool, CPU, etc.)
- `RejectedExecutionException`: Only queue capacity

**5. Standard Java Exception**
- `RejectedExecutionException` is a standard Java exception (from `java.util.concurrent`)
- Applications may already handle it for other executors/thread pools
- Changing to custom exception breaks familiarity

---

## Proposed Solutions

### Option 1: Make BackpressureException Extend RejectedExecutionException

**Pros**:
- BackpressureException IS-A RejectedExecutionException
- Applications can catch RejectedExecutionException to handle both
- Maintains backward compatibility
- Rich metadata available when needed

**Cons**:
- Still two exception types (but unified hierarchy)
- Applications need to check instanceof for metadata access

**Implementation**:
```java
public class BackpressureException extends RejectedExecutionException {
    private final double backpressureLevel;
    private final double threshold;
    private final String sourceName;
    
    // ... existing constructors ...
}
```

**Usage**:
```java
// Applications can catch RejectedExecutionException for both
catch (RejectedExecutionException e) {
    if (e instanceof BackpressureException) {
        BackpressureException bpEx = (BackpressureException) e;
        // Access rich metadata
        logger.warn("Backpressure: level={}, source={}", 
            bpEx.getBackpressureLevel(), bpEx.getSourceName());
    }
    storeToOverflow(item);
}
```

---

### Option 2: Use BackpressureException for Both Cases

**Pros**:
- Single exception type
- Consistent API
- Rich metadata always available

**Cons**:
- Breaks Java standard (RejectedExecutionException is standard)
- Applications may expect RejectedExecutionException for queue full
- Requires changing all RejectedExecutionException usages

**Implementation**:
```java
// In MicroBatcher.proceedWithSubmission()
if (!queue.offer(request, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
    // Use BackpressureException instead of RejectedExecutionException
    BackpressureException ex = new BackpressureException(
        "Queue is full",
        1.0,  // backpressureLevel = 100% (queue full)
        1.0,  // threshold = 100%
        "Vortex Queue Depth"
    );
    future.completeExceptionally(ex);
}
```

---

### Option 3: Use RejectedExecutionException with Metadata (via Cause)

**Pros**:
- Maintains Java standard exception
- Can attach metadata via cause or custom fields
- Applications can handle standard exception

**Cons**:
- RejectedExecutionException doesn't have metadata fields
- Would need to wrap or extend (defeats purpose)
- Less clean API

---

### Option 4: Unified CapacityException (New Exception)

**Pros**:
- Single, clear exception type
- Can include all metadata
- Clear semantic meaning

**Cons**:
- Breaking change (new exception type)
- Not a standard Java exception
- Applications need to update handling

**Implementation**:
```java
public class CapacityException extends RuntimeException {
    private final CapacityType type;  // BACKPRESSURE, QUEUE_FULL, CONCURRENT_LIMIT
    private final double backpressureLevel;  // Optional
    private final double threshold;  // Optional
    private final String sourceName;  // Optional
    private final int currentSize;  // Optional (for queue full)
    private final int maxSize;  // Optional (for queue full)
    
    // ... constructors for different scenarios ...
}
```

---

## Recommendation: Option 1 (Hierarchy Unification)

**Make `BackpressureException` extend `RejectedExecutionException`**

### Why This Is Best

1. **Maintains Standard Java Exception**
   - `RejectedExecutionException` is familiar to Java developers
   - Used by `ExecutorService`, `ThreadPoolExecutor`, etc.
   - Applications may already handle it

2. **Unified Handling**
   - Applications can catch `RejectedExecutionException` to handle both
   - Optional: Check `instanceof BackpressureException` for metadata

3. **Backward Compatible**
   - Existing code handling `RejectedExecutionException` continues to work
   - New code can use `BackpressureException` for rich metadata

4. **Clear Semantics**
   - `BackpressureException` IS-A `RejectedExecutionException`
   - Represents a specific type of rejection (due to backpressure)

5. **Minimal Changes**
   - Only need to change `BackpressureException` class definition
   - All existing code continues to work

### Implementation

```java
package com.vajrapulse.vortex.backpressure;

import java.util.concurrent.RejectedExecutionException;

/**
 * Exception thrown when an item is rejected due to backpressure.
 * 
 * <p>This exception extends {@link RejectedExecutionException} to provide
 * rich metadata about the backpressure condition while maintaining compatibility
 * with standard Java exception handling.
 * 
 * <p>Applications can catch {@link RejectedExecutionException} to handle both
 * backpressure rejections and queue-full rejections:
 * <pre>{@code
 * try {
 *     batcher.submit(item);
 * } catch (RejectedExecutionException e) {
 *     if (e instanceof BackpressureException) {
 *         BackpressureException bpEx = (BackpressureException) e;
 *         // Access rich metadata
 *         logger.warn("Backpressure: level={}, source={}", 
 *             bpEx.getBackpressureLevel(), bpEx.getSourceName());
 *     }
 *     // Handle rejection (store to overflow, etc.)
 *     storeToOverflow(item);
 * }
 * }</pre>
 */
public class BackpressureException extends RejectedExecutionException {
    /** The backpressure level that triggered the rejection (0.0 to 1.0). */
    private final double backpressureLevel;
    /** The threshold that was exceeded (0.0 to 1.0). */
    private final double threshold;
    /** The name of the backpressure source. */
    private final String sourceName;
    
    /**
     * Creates a new backpressure exception.
     * 
     * @param message the error message
     * @param backpressureLevel the backpressure level that triggered the rejection
     * @param threshold the threshold that was exceeded
     * @param sourceName the name of the backpressure source
     */
    public BackpressureException(String message, double backpressureLevel, double threshold, String sourceName) {
        super(message);
        this.backpressureLevel = backpressureLevel;
        this.threshold = threshold;
        this.sourceName = sourceName;
    }
    
    /**
     * Creates a new backpressure exception with a cause.
     * 
     * @param message the error message
     * @param cause the cause of this exception
     * @param backpressureLevel the backpressure level that triggered the rejection
     * @param threshold the threshold that was exceeded
     * @param sourceName the name of the backpressure source
     */
    public BackpressureException(String message, Throwable cause, double backpressureLevel, double threshold, String sourceName) {
        super(message, cause);
        this.backpressureLevel = backpressureLevel;
        this.threshold = threshold;
        this.sourceName = sourceName;
    }
    
    // ... existing getter methods ...
}
```

### Benefits

1. **Unified Exception Handling**:
```java
// Applications can handle both with one catch block
future.whenComplete((result, error) -> {
    if (error != null) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        
        if (cause instanceof RejectedExecutionException) {
            // Handles both BackpressureException and RejectedExecutionException
            if (cause instanceof BackpressureException) {
                BackpressureException bpEx = (BackpressureException) cause;
                // Access rich metadata if needed
                logger.warn("Backpressure: level={:.2f}, threshold={:.2f}, source={}",
                    bpEx.getBackpressureLevel(), bpEx.getThreshold(), bpEx.getSourceName());
            }
            // Unified handling
            storeToOverflow(item);
        }
    }
});
```

2. **Backward Compatible**:
   - Existing code handling `RejectedExecutionException` works unchanged
   - New code can use `BackpressureException` for metadata

3. **Clear Semantics**:
   - `BackpressureException` is a specific type of rejection
   - IS-A relationship is clear and intuitive

---

## Alternative: Keep Both (Current Design)

### Why Keep Both Separate?

1. **Different Semantics**
   - `BackpressureException`: "System is under pressure" (proactive)
   - `RejectedExecutionException`: "Queue is full" (reactive)

2. **Different Information Needs**
   - `BackpressureException`: Rich metadata for monitoring
   - `RejectedExecutionException`: Simple message is sufficient

3. **Different Configuration**
   - `BackpressureException`: Only when backpressure configured
   - `RejectedExecutionException`: Always possible

4. **Standard Java Exception**
   - `RejectedExecutionException` is familiar and standard
   - Used by many Java libraries

### Current Handling (Acceptable)

Applications can handle both with minimal code:
```java
if (error instanceof BackpressureException || 
    error instanceof RejectedExecutionException) {
    storeToOverflow(item);
}
```

Or use a helper method:
```java
private boolean isCapacityException(Throwable error) {
    return error instanceof BackpressureException || 
           error instanceof RejectedExecutionException;
}
```

---

## Conclusion

**Recommendation**: **Option 1 - Make BackpressureException extend RejectedExecutionException**

This provides:
- ✅ Unified exception hierarchy
- ✅ Backward compatibility
- ✅ Standard Java exception (RejectedExecutionException)
- ✅ Rich metadata when needed (BackpressureException)
- ✅ Simple application handling (catch RejectedExecutionException)
- ✅ Minimal code changes

**Alternative**: Keep both separate if the semantic distinction is important to your use case.

**Key Insight**: From the application's perspective, both exceptions mean "can't accept item" and should be handled the same way. The distinction is mainly for monitoring and debugging purposes.

---

**Analysis Complete**

