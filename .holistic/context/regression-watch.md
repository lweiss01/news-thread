# Regression Watch

Use this before changing existing behavior. It is the short list of fixes and outcomes that future agents should preserve.

## Capture work and prepare a clean handoff.

- Goal: Capture work and prepare a clean handoff.
- Durable changes:
- No durable changes recorded.
- Why this matters:
- No impact notes recorded.
- Do not regress:
- [FIX] Background notifications removed entirely when disabling in-app toasts | files: NotificationHelper.kt,StoryUpdateWorker.kt | risk: Removing showNotification call from StoryUpdateWorker or removing foreground/background branching in NotificationHelper
- Source session: session-2026-03-22T17-27-06-591Z

