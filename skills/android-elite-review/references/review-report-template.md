# Review Report Template

## Executive Summary
- Release recommendation: [Ship / Ship with guardrails / Block]
- Top risks: [1-3 concise bullets]
- Overall confidence: [High / Medium / Low]

## Findings
For each finding:
- ID: `F-###`
- Severity: `S0|S1|S2|S3`
- Confidence: `High|Medium|Low`
- Lens: `Android|Security|UX|UI|Edge-ML|Cross-cutting`
- Evidence: `path:line` references
- Problem: what is wrong
- Impact: who/what is harmed
- Fix: minimal viable remediation + robust follow-up
- Validation: tests/metrics/checks to prove fix

## Cross-Cutting Risks
- List issues that span multiple layers (Android app, worker API, model pipeline, design system).

## Positive Findings
- Preserve effective patterns that should not be regressed.

## Quick Wins (<= 1 day)
- Small, high-leverage changes.

## Strategic Investments (> 1 day)
- Structural improvements with expected ROI.

## Open Questions
- Unknowns blocking higher confidence and what evidence is needed.
