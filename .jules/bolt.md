## 2024-05-22 - [Bun Test Environment Limitations]
**Learning:** `vi.stubGlobal` fails in the Bun test environment with `TypeError: vi.stubGlobal is not a function`.
**Action:** Always use manual assignment to `global` (e.g., `global.fetch = mock`) in `beforeEach` and restore it in `afterEach` when writing tests for Cloudflare Workers in this repo.

## 2024-05-22 - [Google News Query Encoding]
**Learning:** The `googleNewsSearchUrl` helper uses `encodeURIComponent`, resulting in `%20` for spaces, not `+`.
**Action:** Ensure tests verify against `%20` for query parameters when using this helper, or update expectations if `+` is strictly required (though Google handles both).

## 2025-02-20 - [Kotlin Regex Optimization]
**Learning:** `Regex` object instantiation inside hot paths (like text parsing functions in `EntityExtractor.kt` or `BertTokenizerWrapper.kt`) can cause unnecessary memory allocation and performance degradation due to repeated regex compilation.
**Action:** Always hoist `Regex` literals to a `companion object` to precompile them when used in frequently executed functions in Kotlin.
