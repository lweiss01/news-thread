# Android Security Checklist

## Threat Modeling
- Map trust boundaries across app, device storage, network, Cloudflare Worker, and third-party sources.
- Identify attacker goals: data exfiltration, feed manipulation, account abuse, tracking/fingerprinting.
- Confirm protections for high-risk operations (auth, sync, remote config, model updates).

## Secrets and Sensitive Data
- Verify API keys, tokens, and secrets are not hardcoded in app or worker source.
- Check local persistence for sensitive fields and confirm encryption where required.
- Review logs for accidental token/PII leakage.
- Confirm build variants do not expose debug secrets in release artifacts.

## Network and Transport
- Validate TLS-only transport and strict host validation.
- Review `network_security_config.xml` for cleartext or over-broad trust settings.
- Confirm certificate pinning strategy if threat model requires it.
- Check request signing or server-side validation for high-value endpoints.

## Web and Content Safety
- Audit HTML/content extraction for script injection and unsafe rendering.
- Verify URL handling blocks dangerous schemes and open redirect paths.
- Confirm untrusted content is sanitized before display.
- Check WebView usage for risky flags (JS enabled without isolation, file access, mixed content).

## Dependency and Supply Chain
- Identify outdated libraries with known CVEs and high-risk transitive dependencies.
- Verify dependency locking/version policy and update cadence.
- Check worker package dependencies for vulnerable parsers/fetch tooling.

## Abuse and Integrity
- Assess anti-automation/rate limiting controls at edge APIs.
- Verify feed ranking inputs cannot be trivially poisoned by malicious sources.
- Check replay/tamper risks for worker responses consumed by clients.
- Validate telemetry and alerting for suspicious behavior patterns.
