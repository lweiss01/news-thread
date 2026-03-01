# Sentinel Journal
## 2025-02-28 - [Fail Open API Key Validation]
**Vulnerability:** The API key middleware in the Cloudflare Worker allowed unauthenticated access if the `SHARED_KEY` environment variable was missing (`c.env.SHARED_KEY && apiKey !== c.env.SHARED_KEY`). This "Fail Open" configuration could lead to complete bypass of authentication if the environment variable was inadvertently omitted or misconfigured during deployment.
**Learning:** Security controls should default to "Fail Closed" to ensure endpoints remain protected even in the event of missing configuration. Conditional checks that bypass validation when configuration is missing are a critical risk.
**Prevention:** Explicitly enforce validation or fail securely (`!c.env.SHARED_KEY || apiKey !== c.env.SHARED_KEY`). Implement configuration validation during startup to detect missing secrets immediately.

## 2024-03-01 - [Prevent Timing Attacks in Security Comparisons]
**Vulnerability:** The `worker/src/index.ts` API key middleware was using a standard comparison (`apiKey !== c.env.SHARED_KEY`) to validate incoming API keys. This is susceptible to timing attacks, where an attacker measures the response time to guess the secret token character by character.
**Learning:** Frameworks like Hono are frequently deployed in edge and high-performance environments where timing discrepancies are noticeable.
**Prevention:** Always use constant-time comparison methods, like `timingSafeEqual` (from `hono/utils/buffer` or Node's `crypto` module), when verifying cryptographic secrets, API keys, and passwords.
