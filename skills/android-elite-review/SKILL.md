---
name: android-elite-review
description: Comprehensive Android app audit focused on correctness, architecture, performance, security, UX quality, visual design quality, and domain-specific risks in news aggregation systems that use Cloudflare Workers and TensorFlow Lite models. Use when asked to review Android code, identify bugs, assess security/privacy risk, critique UX/UI quality, or evaluate story clustering/ranking pipelines and edge ML integrations.
---

# Android Elite Review

## Overview
Perform a multi-expert review that behaves like a coordinated panel:
1. Elite Android engineer (architecture, correctness, maintainability, performance)
2. Android security expert (threats, secrets, transport, storage, abuse paths)
3. UX expert (task flow, clarity, trust, accessibility, cognitive load)
4. UI design expert (hierarchy, spacing, typography, consistency, polish)
5. Android news-app domain expert with Cloudflare Workers + TensorFlow Lite depth

Deliver concrete findings with evidence, impact, and pragmatic fixes.

## Workflow
1. Define scope and entry points.
   - Confirm modules and platforms in scope (Android app, worker/backend, shared schemas).
   - Identify critical user journeys: feed load, article detail, compare perspectives, follow/track, settings.
2. Load repo-specific guardrails before critique.
   - Read [newsthread-repo-standards.md](references/newsthread-repo-standards.md).
   - If repository standards conflict with assumptions, treat repository standards as source of truth.
3. Build a quick system map before deep critique.
   - Trace data flow: ingestion -> normalization -> clustering/ranking -> persistence -> UI rendering.
   - Mark trust boundaries (device, network, edge worker, third-party content).
4. Run the static baseline pass.
   - Run `node scripts/static-audit.mjs --repo <repo-root>`.
   - Ingest both outputs: `tmp/android-elite-review/static-audit.json` and `tmp/android-elite-review/static-audit.md`.
5. Run role-based passes.
   - Read [android-engineering-checklist.md](references/android-engineering-checklist.md).
   - Read [security-checklist.md](references/security-checklist.md).
   - Read [ux-ui-checklist.md](references/ux-ui-checklist.md).
   - Read [news-aggregation-edge-ml-checklist.md](references/news-aggregation-edge-ml-checklist.md).
6. Merge findings and de-duplicate overlaps.
   - Keep one finding per root cause; reference secondary symptoms.
   - Prioritize by user harm, exploitability, regression risk, and effort.
7. Score release readiness.
   - Use the weighted model in [review-report-template.md](references/review-report-template.md).
   - Calculate lens scores and final weighted score before giving release recommendation.
8. Produce a structured report.
   - Use [review-report-template.md](references/review-report-template.md).

## Evidence Rules
- Cite exact file paths and 1-based lines whenever possible.
- Distinguish verified defects from inferred risk.
- State assumptions and missing artifacts explicitly.
- Avoid speculative security claims without a plausible attack path.

## Severity and Confidence
Use these levels for every finding:
- `S0` Critical: exploitable/high-impact security issue or major data loss/corruption.
- `S1` High: severe user harm, privacy risk, frequent crash, or major workflow break.
- `S2` Medium: meaningful quality/performance/UX issue with moderate impact.
- `S3` Low: minor issue, polish gap, or maintainability concern.

Confidence:
- `High`: direct code evidence.
- `Medium`: strong signals but limited runtime proof.
- `Low`: plausible concern requiring confirmation.

## Review Quality Bar
- Favor high-signal findings over long generic lists.
- Include at least one viable fix path per finding.
- Include test coverage gaps for each high-severity issue.
- Call out strong patterns worth preserving (not only negatives).

## Output Contract
Return:
1. Executive summary (top risks and release recommendation).
2. Weighted readiness score and lens breakdown.
3. Findings sorted by severity, then confidence.
4. Cross-cutting risks (issues spanning Android + worker/ML/UX).
5. Quick wins (small changes, high impact).
6. Strategic investments (larger refactors, tooling, observability).
7. Open questions and evidence needed to close them.

If no issues are found, state that explicitly and list residual risk areas not fully validated.
