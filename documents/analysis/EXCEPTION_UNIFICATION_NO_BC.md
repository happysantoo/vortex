# Exception Unification Analysis: Without Backward Compatibility Constraints

**Date**: December 6, 2025  
**Version**: 0.0.8  
**Question**: How would the exception design change if backward compatibility is not a concern?

---

## Current Implementation (With Backward Compatibility)

**Approach**: `BackpressureException` extends `RejectedExecutionException`

**Rationale**: 
- Maintains compatibility with existing code that catches `RejectedExecutionException`
- Standard Java exception type
- Applications can catch either type

**Limitations**:
- Still two exception types in the hierarchy
- Applications need to understand the inheritance relationship
- Some confusion about when to use which

---

## Proposed Design (Without Backward Compatibility)

### Option A: BackpressureException Only (Extends RuntimeException)

**Make `BackpressureException` the single, unified exception for all rejections.**

**Changes**:
1. `BackpressureException` extends `RuntimeException` (not `RejectedExecutionException`)
2. All rejections use `BackpressureException`:
   - Backpressure detected → `BackpressureException`
   - Queue full → `BackpressureException`
   - Concurrent limit → `BackpressureException`
3. Remove dependency on `RejectedExecutionException` entirely

**Benefits**:
- ✅ **Single exception type** - simpler API
- ✅ **Clear semantics** - all rejections are "capacity constraints"
- ✅ **Rich metadata** - always available (level, threshold, source)
- ✅ **No confusion** - one exception to handle
- ✅ **Cleaner hierarchy** - no inheritance from standard Java exception

**Drawbacks**:
- ❌ Not a standard Java exception (but that's okay if BC not a concern)
- ❌ Breaking change (but BC not a concern)

**Implementation**:
```java
public class BackpressureException extends RuntimeException {
    private final double backpressureLevel;
    private final double threshold;
    private final String sourceName;
    
    // Factory methods for different scenarios
    public static BackpressureException backpressure(double level, double threshold, String source) {
        return new BackpressureException(
            String.format("Backpressure too high: %.2f (threshold: %.2f, source: %s)", 
                level, threshold, source),
            level, threshold, source
        );
    }
    
    public static BackpressureException queueFull(int currentSize, int maxSize) {
        return new BackpressureException(
            String.format("Queue full: %d/%d", currentSize, maxSize),
            1.0, 1.0, "Vortex Queue Depth"
        );
    }
    
    public static BackpressureException concurrentLimit(int active, int max) {
        double level = max > 0 ? (double) active / max : 1.0;
        return new BackpressureException(
            String.format("Concurrent batch limit reached: %d/%d", active, max),
            level, 1.0, "Concurrent Batches"
        );
    }
}
```

**Application Handling**:
```java
// Simple, unified handling
if (error instanceof BackpressureException) {
    BackpressureException ex = (BackpressureException) error;
    // All rejections handled the same way
    storeToOverflow(item);
}
```

---

### Option B: CapacityException (New Name, Clearer Semantics)

**Create a new exception with clearer naming that reflects "capacity constraints".**

**Rationale**: 
- `BackpressureException` name suggests "pressure" but we're really talking about "capacity"
- More accurate: "can't accept because at capacity" vs "system under pressure"

**Implementation**:
```java
public class CapacityException extends RuntimeException {
    private final CapacityType type;  // BACKPRESSURE, QUEUE_FULL, CONCURRENT_LIMIT
    private final double utilization;  // 0.0 to 1.0
    private final double threshold;    // 0.0 to 1.0
    private final String sourceName;
    
    public enum CapacityType {
        BACKPRESSURE,      // Backpressure detected (proactive)
        QUEUE_FULL,        // Queue at capacity (reactive)
        CONCURRENT_LIMIT   // Concurrent batch limit reached
    }
    
    // Factory methods
    public static CapacityException backpressure(double level, double threshold, String source) {
        return new CapacityException(
            CapacityType.BACKPRESSURE,
            level, threshold, source,
            String.format("Backpressure detected: %.2f (threshold: %.2f, source: %s)", 
                level, threshold, source)
        );
    }
    
    public static CapacityException queueFull(int current, int max) {
        return new CapacityException(
            CapacityType.QUEUE_FULL,
            1.0, 1.0, "Vortex Queue Depth",
            String.format("Queue full: %d/%d", current, max)
        );
    }
    
    public static CapacityException concurrentLimit(int active, int max) {
        double level = max > 0 ? (double) active / max : 1.0;
        return new CapacityException(
            CapacityType.CONCURRENT_LIMIT,
            level, 1.0, "Concurrent Batches",
            String.format("Concurrent batch limit: %d/%d", active, max)
        );
    }
}
```

**Benefits**:
- ✅ **Clearer naming** - "Capacity" is more accurate than "Backpressure"
- ✅ **Type information** - enum distinguishes rejection reasons
- ✅ **Single exception** - unified handling
- ✅ **Rich metadata** - utilization, threshold, source

**Drawbacks**:
- ❌ Breaking change (but BC not a concern)
- ❌ New name might be unfamiliar

---

## Recommendation: Option A (BackpressureException Only)

**Why Option A is Best**:

1. **Simplest Change**
   - Just change `extends RejectedExecutionException` to `extends RuntimeException`
   - Keep existing name and structure
   - Minimal code changes

2. **Single Exception Type**
   - All rejections use `BackpressureException`
   - No confusion about which exception to catch
   - Cleaner API

3. **Rich Metadata Always Available**
   - Every rejection has level, threshold, source
   - Better for monitoring and debugging
   - Consistent information across all rejection types

4. **Clear Application Handling**
   ```java
   // One catch block handles everything
   if (error instanceof BackpressureException) {
       BackpressureException ex = (BackpressureException) error;
       logger.warn("Rejected: {} (level={:.2f}, source={})", 
           ex.getMessage(), ex.getBackpressureLevel(), ex.getSourceName());
       storeToOverflow(item);
   }
   ```

5. **No Standard Java Exception Dependency**
   - Not tied to `RejectedExecutionException` semantics
   - Can evolve independently
   - Clearer library-specific exception

---

## Implementation Changes (Without BC Concerns)

### 1. Change BackpressureException Base Class

```java
// Before (with BC)
public class BackpressureException extends RejectedExecutionException { ... }

// After (without BC)
public class BackpressureException extends RuntimeException { ... }
```

### 2. All Rejections Use BackpressureException

```java
// Queue full
BackpressureException.queueFull(currentSize, maxSize)

// Concurrent limit
BackpressureException.concurrentLimitReached(activeBatches, maxBatches)

// Backpressure detected (already using it)
new BackpressureException(message, level, threshold, source)
```

### 3. Simplified Application Handling

```java
// Before (with BC - need to handle both)
if (error instanceof BackpressureException || 
    error instanceof RejectedExecutionException) {
    // Handle rejection
}

// After (without BC - single exception)
if (error instanceof BackpressureException) {
    BackpressureException ex = (BackpressureException) error;
    // Handle rejection with rich metadata
    logger.warn("Rejected: level={:.2f}, source={}", 
        ex.getBackpressureLevel(), ex.getSourceName());
    storeToOverflow(item);
}
```

---

## Comparison: With vs Without BC

| Aspect | With BC (Current) | Without BC (Proposed) |
|--------|-------------------|----------------------|
| **Exception Types** | 2 (BackpressureException, RejectedExecutionException) | 1 (BackpressureException) |
| **Base Class** | `RejectedExecutionException` | `RuntimeException` |
| **Application Handling** | Catch `RejectedExecutionException` or `BackpressureException` | Catch `BackpressureException` only |
| **Metadata** | Available via `instanceof` check | Always available |
| **Clarity** | Some confusion about which to catch | Clear: one exception |
| **Standard Java** | Uses standard exception | Library-specific exception |
| **Breaking Change** | No | Yes (but BC not a concern) |

---

## Code Changes Required

### Minimal Changes (Option A)

1. **BackpressureException.java**:
   - Change `extends RejectedExecutionException` → `extends RuntimeException`
   - Update JavaDoc

2. **MicroBatcher.java**:
   - Already using `BackpressureException` for all rejections
   - No changes needed (already unified)

3. **Tests**:
   - Update tests to expect `BackpressureException` instead of `RejectedExecutionException`
   - Update exception type checks

4. **Documentation**:
   - Update guides to show single exception handling
   - Remove references to `RejectedExecutionException`

---

## Conclusion

**Without backward compatibility concerns, the design becomes much simpler:**

1. **Single Exception**: `BackpressureException` only
2. **Clear Semantics**: All rejections are capacity constraints
3. **Rich Metadata**: Always available (level, threshold, source)
4. **Simple Handling**: One catch block handles everything
5. **Cleaner API**: No confusion about which exception to catch

**Recommendation**: Change `BackpressureException` to extend `RuntimeException` instead of `RejectedExecutionException`, and use it for all rejection scenarios.

**Key Insight**: The application perspective is correct - "is the batcher accepting or rejecting?" - and a single exception type perfectly matches this mental model.

---

**Analysis Complete**

