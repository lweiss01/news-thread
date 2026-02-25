## 2024-05-22 - [Bun Test Environment Limitations]
**Learning:** `vi.stubGlobal` fails in the Bun test environment with `TypeError: vi.stubGlobal is not a function`.
**Action:** Always use manual assignment to `global` (e.g., `global.fetch = mock`) in `beforeEach` and restore it in `afterEach` when writing tests for Cloudflare Workers in this repo.

## 2024-05-22 - [Google News Query Encoding]
**Learning:** The `googleNewsSearchUrl` helper uses `encodeURIComponent`, resulting in `%20` for spaces, not `+`.
**Action:** Ensure tests verify against `%20` for query parameters when using this helper, or update expectations if `+` is strictly required (though Google handles both).
