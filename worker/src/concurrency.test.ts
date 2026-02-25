import { describe, it, expect } from 'vitest';
import { runWithConcurrency } from './concurrency';

describe('runWithConcurrency', () => {
    it('processes all items and returns results in order', async () => {
        const items = [1, 2, 3, 4, 5];
        // Using a delay to ensure async execution matters
        const result = await runWithConcurrency(items, 2, async (i) => {
            await new Promise(resolve => setTimeout(resolve, 1));
            return i * 2;
        });
        expect(result).toEqual([2, 4, 6, 8, 10]);
    });

    it('respects concurrency limit', async () => {
        const items = [1, 2, 3, 4, 5, 6];
        let active = 0;
        let maxActive = 0;

        const task = async (i: number) => {
            active++;
            maxActive = Math.max(maxActive, active);
            // Simulate work that takes time
            await new Promise(resolve => setTimeout(resolve, 20));
            active--;
            return i;
        };

        // Concurrency 2 means at most 2 active tasks
        await runWithConcurrency(items, 2, task);
        expect(maxActive).toBeLessThanOrEqual(2);
        // It should have reached 2 at some point
        expect(maxActive).toBe(2);
    });

    it('handles concurrency greater than item count', async () => {
        const items = [1, 2];
        const result = await runWithConcurrency(items, 5, async (i) => i);
        expect(result).toEqual([1, 2]);
    });

    it('handles empty input', async () => {
        const result = await runWithConcurrency([], 5, async () => 1);
        expect(result).toEqual([]);
    });

    it('propagates errors immediately', async () => {
        const items = [1, 2, 3, 4];
        const task = async (i: number) => {
            if (i === 3) throw new Error('fail');
            await new Promise(resolve => setTimeout(resolve, 10));
            return i;
        };

        await expect(runWithConcurrency(items, 2, task)).rejects.toThrow('fail');
    });
});
