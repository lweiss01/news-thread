# T01: Plan 01

**Slice:** S02 — **Milestone:** M001

## Description

Add Readability4J and jsoup dependencies, create domain models for text extraction results, and implement paywall detection heuristics.

Purpose: Establish the foundation types and detection logic needed by the extraction pipeline. These domain models are pure Kotlin with no Android dependencies, enabling clean separation and testability.

Output:
- build.gradle.kts with Readability4J 1.0.8 and jsoup 1.22.1 dependencies
- ExtractionResult.kt sealed class (Success, PaywallDetected, NetworkError, ExtractionError, NotFetched)
- ArticleFetchPreference.kt enum (ALWAYS, WIFI_ONLY, NEVER)
- PaywallDetector.kt object with heuristic detection
