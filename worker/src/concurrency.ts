/**
 * Executes an async function over an array of items with a controlled concurrency limit.
 * Unlike batching (e.g., Promise.all with chunks), this approach starts a new task
 * as soon as one finishes, preventing "head-of-line blocking" where fast tasks wait for slow ones.
 *
 * @param items The array of items to process.
 * @param concurrency The maximum number of simultaneous async operations.
 * @param fn The async function to apply to each item.
 * @returns A promise that resolves to an array of results, in the same order as the input items.
 */
export async function runWithConcurrency<T, R>(
    items: T[],
    concurrency: number,
    fn: (item: T) => Promise<R>
): Promise<R[]> {
    if (items.length === 0) return [];

    // Ensure concurrency is at least 1
    const limit = Math.max(1, concurrency);

    // Pre-allocate results array to ensure order is preserved
    const results: R[] = new Array(items.length);

    // Shared index to track which item to process next
    let nextIndex = 0;

    // Worker function: repeatedly picks the next item and processes it
    const worker = async () => {
        while (nextIndex < items.length) {
            const index = nextIndex++;
            // Check again to avoid race condition if multiple workers incremented (though JS is single-threaded here)
            // Actually, nextIndex++ is atomic in the event loop sense, so `index` is unique for each iteration.

            try {
                results[index] = await fn(items[index]);
            } catch (error) {
                // If fn throws, we let the whole Promise.all fail, or user should handle catch inside fn.
                // Re-throw to fail fast, or could be modified to return a Result type.
                throw error;
            }
        }
    };

    // Spawn workers up to the concurrency limit or item count
    const workerCount = Math.min(limit, items.length);
    const workers = new Array(workerCount).fill(null).map(() => worker());

    await Promise.all(workers);
    return results;
}
