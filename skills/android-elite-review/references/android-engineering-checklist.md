# Android Engineering Checklist

## Correctness and Stability
- Verify state management handles loading, success, empty, and error states without ambiguity.
- Check coroutine scope ownership and cancellation boundaries to prevent leaks and stale updates.
- Confirm nullability contracts are enforced at boundaries (network, db, parsing, intents).
- Identify crash-prone operations (unsafe casts, unchecked list access, divide-by-zero, parsing assumptions).

## Architecture and Modularity
- Confirm domain, data, and presentation layers have clear responsibility boundaries.
- Flag framework leakage into domain logic and over-coupled ViewModels.
- Verify dependency injection scope correctness and lifecycle alignment.
- Identify duplicated business logic that should be centralized.

## Performance and Resource Use
- Inspect hot paths for redundant recomposition, blocking calls, and unnecessary allocations.
- Validate pagination, caching, and background sync strategy for feed-heavy workloads.
- Check Room query patterns for N+1 behavior and missing indexes.
- Identify expensive parsing/tokenization done on main thread.

## Testability and Reliability
- Check that critical use cases have unit/integration coverage.
- Flag missing tests around parsing, clustering/ranking, and sync conflict handling.
- Verify deterministic tests for time windows and ranking logic.
- Identify flaky test smells (timing assumptions, global mutable state).

## Maintainability
- Flag god classes, deep nesting, and hard-coded constants that block safe iteration.
- Check naming clarity and code readability in critical modules.
- Verify observability hooks (logs/metrics/tracing) for production triage.
