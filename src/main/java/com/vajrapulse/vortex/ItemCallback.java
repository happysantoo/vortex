package com.vajrapulse.vortex;

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
 * batcher.submit(item, (submittedItem, itemResult) -> {
 *     if (itemResult instanceof ItemResult.Success<MyItem>) {
 *         // Handle success
 *     } else if (itemResult instanceof ItemResult.Failure<MyItem> failure) {
 *         // Handle failure
 *     }
 * });
 * }</pre>
 * 
 * <p>Or implemented as a class for more complex logic:
 * <pre>{@code
 * class MyItemCallback implements ItemCallback<MyItem> {
 *     @Override
 *     public void onResult(MyItem item, ItemResult<MyItem> result) {
 *         // Complex logic here
 *     }
 * }
 * }</pre>
 * 
 * @param <T> the type of item
 * @since 0.0.9
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
     * </ul>
     * 
     * @param item the item that was submitted
     * @param result the result of processing this specific item (Success or Failure)
     */
    void onResult(T item, ItemResult<T> result);
}

