package com.vajrapulse.vortex;

import com.vajrapulse.vortex.results.ItemResult;

/**
 * Callback interface for handling individual item processing results.
 * 
 * <p>This interface is used with {@link MicroBatcher#submit(Object, ItemCallback)} to receive
 * notifications when an item's processing completes (as part of a batch).
 * 
 * <p>The callback fires once per item with that item's individual result (success or failure),
 * not the full batch result.
 * 
 * <p>This is a functional interface, so it can be used with lambda expressions:
 * <pre>{@code
 * batcher.submit(item, itemResult -> {
 *     if (itemResult instanceof ItemResult.Success<MyItem> success) {
 *         // Handle success - access item via success.getItem()
 *         MyItem processedItem = success.getItem();
 *     } else if (itemResult instanceof ItemResult.Failure<MyItem> failure) {
 *         // Handle failure - access item via failure.getItem()
 *         MyItem failedItem = failure.getItem();
 *         Throwable error = failure.error();
 *     }
 * });
 * }</pre>
 * 
 * <p>Or implemented as a class for more complex logic:
 * <pre>{@code
 * class MyItemCallback implements ItemCallback<MyItem> {
 *     @Override
 *     public void onResult(ItemResult<MyItem> result) {
 *         // Access item via result.getItem()
 *         MyItem item = result.getItem();
 *         // Complex logic here
 *     }
 * }
 * }</pre>
 * 
 * @param <T> the type of item
 * @since 0.0.11
 */
@FunctionalInterface
public interface ItemCallback<T> {
    /**
     * Called when an item's processing completes (as part of a batch).
     * 
     * <p>This method is invoked once per item with that item's individual result.
     * The callback fires when the batch containing this item is processed by the backend
     * (typically 10-50ms after submission, depending on batch size and linger time).
     * 
     * <p><strong>Important Notes:</strong>
     * <ul>
     *   <li>The callback may fire on a different thread (batch processing thread)</li>
     *   <li>The callback only fires if the item was accepted (not rejected immediately)</li>
     *   <li>The callback receives the individual item's result, not the full batch result</li>
     *   <li>The item can be accessed via {@link ItemResult#getItem()}</li>
     * </ul>
     * 
     * @param result the result of processing this specific item (Success or Failure)
     *               The item can be accessed via {@code result.getItem()}
     */
    void onResult(ItemResult<T> result);
}

