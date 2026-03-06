# Review Report Template

## Executive Summary
- Release recommendation: [Ship / Ship with guardrails / Block]
- Top risks: [1-3 concise bullets]
- Overall confidence: [High / Medium / Low]

## Weighted Readiness Score
Use this weighted model:
- Security: 35%
- Reliability/Correctness: 25%
- UX: 20%
- UI Design: 10%
- Domain (News + Edge ML): 10%

For each lens, assign a 0-100 score.
Recommended scoring formula per lens (clamped to 0-100):
- `lens_score = 100 - (40 * S0) - (20 * S1) - (8 * S2) - (2 * S3)`
- Use unresolved findings for that lens.

Weighted score:
- `overall = (security * 0.35) + (reliability * 0.25) + (ux * 0.20) + (ui * 0.10) + (domain * 0.10)`

Release mapping:
- `>= 85`: Ship
- `70-84`: Ship with guardrails
- `< 70`: Block

Automatic block conditions (override score):
- Any unresolved `S0`
- More than two unresolved `S1` in Security or Reliability/Correctness

## Lens Score Table
- Security: [0-100], rationale:
- Reliability/Correctness: [0-100], rationale:
- UX: [0-100], rationale:
- UI Design: [0-100], rationale:
- Domain (News + Edge ML): [0-100], rationale:
- Overall weighted score: [0-100]

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
