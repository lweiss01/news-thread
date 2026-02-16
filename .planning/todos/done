---
created: 2026-02-04T18:46:00Z
title: Test plans must include validation methods and steps
area: planning
files: []
---

## Problem

During Phase 2 UAT, user couldn't validate infrastructure components (TextExtractionRepository, UserPreferencesRepository, etc.) because:
1. UI integration isn't complete yet (Settings screen pending in 02-04)
2. Test descriptions lack specific validation steps
3. Success criteria say "verify it works" without explaining HOW

Example: "TextExtractionRepository orchestrates full pipeline" - user has no way to see this happening without UI indicators or specific debugging steps.

This makes UAT sessions frustrating for infrastructure phases where user-facing behavior isn't visible yet.

## Solution

For future plans (especially infrastructure/foundation phases):

**Option 1: User-facing validation**
- Link each component to specific user behavior that validates it
- Example: "NetworkMonitor works correctly" → "Toggle airplane mode, app shows offline indicator"

**Option 2: Code inspection validation**
- Provide specific files and methods to review
- Example: "PaywallDetector has 3-tier detection" → "Check PaywallDetector.kt contains PAYWALL_CSS_SELECTORS, PAYWALL_TEXT_PATTERNS, and isAccessibleForFree check"

**Option 3: Debug/logging validation**
- Include logcat commands or debug steps
- Example: "ArticleHtmlFetcher logs errors" → "Filter logcat for 'ArticleHtmlFetcher', trigger 404, verify error logged"

**Apply to:**
- Phase planning (PLAN.md success criteria)
- UAT test creation (verify-work extracts testable with HOW)
- Plan verification (plan-checker checks validation methods present)
