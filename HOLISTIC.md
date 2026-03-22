# HOLISTIC

<!-- ═══════════════════════════════════════════════════════════════════════
     AGENT INSTRUCTIONS — READ THIS ENTIRE FILE BEFORE DOING ANYTHING ELSE
     ═══════════════════════════════════════════════════════════════════════

  1. Read this file top-to-bottom.
  2. Read AGENTS.md for the setup steps specific to your agent.
  3. Summarise to the user: what was last worked on, what's planned next.
  4. Ask: "What would you like to work on — continue recent work, or start something new?"
  5. Run `holistic resume --agent <your-agent-name>` to open a session.
  6. If `holistic` is not on PATH, use the repo-local helper under `system/` for this repo.

  ⚠️  If you are about to edit a file listed under KNOWN FIXES, STOP and
     read that fix entry carefully before proceeding.
  ════════════════════════════════════════════════════════════════════════ -->

## Start Here

This repo uses Holistic for cross-agent handoffs. The source of truth is the repo itself: handoff docs, history, and regression memory should be committed and synced so any device can continue. Read this file first, then review the long-term history docs and zero-touch architecture note, then use the adapter doc for your app. The Holistic daemon is optional and only improves passive capture on devices where it is installed.

If `holistic` is not on PATH, use the repo-local helper instead: Windows `.\.holistic\system\holistic.cmd resume --agent <your-agent-name>`; macOS/Linux `./.holistic/system/holistic resume --agent <your-agent-name>`.

## Product North Star

Open repo, start working, Holistic quietly keeps continuity alive.

That is the intended end state for this project. Prefer changes that reduce ceremony, keep continuity durable, and make Holistic fade further into the background of normal work.

## Known Fixes — Do Not Regress

⚠️  If you are about to edit a file listed here, STOP and read the fix entry first.

- Background notifications removed entirely when disabling in-app toasts
  Sensitive files: NotificationHelper.kt,StoryUpdateWorker.kt
  Risk: Removing showNotification call from StoryUpdateWorker or removing foreground/background branching in NotificationHelper
- Hardcoded API key, broken isStrictGoogleNewsUrl, duplicate MAX_ARTICLES, unencoded search queries, leaked CoroutineScope, per-call tensor resize, corrupted ArticleCard/Modifiers/MatchedArticleCard/StoryContent files, hand-rolled JSON parsing, dead Request.Builder, force-refresh on every launch
  Sensitive files: app/build.gradle.kts,worker/src/resolver.ts,RssNewsRepository.kt,ArticleMatchingRepositoryImpl.kt,EmbeddingModelManager.kt,ArticleCard.kt,Modifiers.kt,MatchedArticleCard.kt,StoryContent.kt,ComparisonScreen.kt,StoryDetailScreen.kt,MainActivity.kt
  Risk: Re-adding hardcoded keys to build.gradle.kts, corrupting files via bad merges, removing URL encoding from search, reverting tensor resize to per-call, replacing AppScope with unstructured CoroutineScope

## Current Objective

**Capture work and prepare a clean handoff.**

Capture work and prepare a clean handoff.

## Latest Work Status

Committed: docs: expand regression watch with all 10 items from codebase review

## What Was Tried

- Nothing recorded yet.

## What To Try Next

- Ask the user what they'd like to work on.

## Active Plan

- Read HOLISTIC.md
- Confirm next step with the user

## Overall Impact So Far

- Nothing recorded yet.

## Regression Watch

- Review the regression watch document before changing related behavior.

## Key Assumptions

- None recorded.

## Blockers

- None.

## Changed Files In Current Session

- .gitattributes
- .github/workflows/pages.yml
- .gitignore
- .gradle/8.13/checksums/checksums.lock
- .gradle/8.13/checksums/sha1-checksums.bin
- .gradle/8.13/executionHistory/executionHistory.bin
- .gradle/8.13/executionHistory/executionHistory.lock
- .gradle/8.13/fileChanges/last-build.bin
- .gradle/8.13/fileHashes/fileHashes.bin
- .gradle/8.13/fileHashes/fileHashes.lock
- .gradle/8.13/fileHashes/resourceHashesCache.bin
- .gradle/buildOutputCleanup/buildOutputCleanup.lock
- .gradle/buildOutputCleanup/outputFiles.bin
- .gradle/file-system.probe
- .gsd/STATE.md
- .gsd/milestones/M001/slices/S23/S23-PLAN.md
- PLAY_DATA_SAFETY.md
- app/build.gradle.kts
- app/proguard-rules.pro
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/newsthread/app/presentation/settings/SettingsScreen.kt
- app/src/main/res/xml/backup_rules.xml
- app/src/main/res/xml/data_extraction_rules.xml
- docs/index.html
- docs/privacy/index.html
- docs/styles.css
- docs/terms/index.html
- gradle.properties
- hs_err_pid2072.log
- hs_err_pid21980.log
- hs_err_pid22120.log
- hs_err_pid3944.log
- keystore.properties
- keystore.properties.example
- keystore/newsthread-release.jks
- replay_pid3944.log

## Pending Work Queue

- None.

## Long-Term Memory

- Project history: [.holistic/context/project-history.md](.holistic/context/project-history.md)
- Regression watch: [.holistic/context/regression-watch.md](.holistic/context/regression-watch.md)
- Zero-touch architecture: [.holistic/context/zero-touch.md](.holistic/context/zero-touch.md)
- Portable sync model: handoffs are intended to be committed and synced so any device with repo access can continue.

## Supporting Documents

- State file: [.holistic/state.json](.holistic/state.json)
- Current plan: [.holistic/context/current-plan.md](.holistic/context/current-plan.md)
- Session protocol: [.holistic/context/session-protocol.md](.holistic/context/session-protocol.md)
- Session archive: [.holistic/sessions](.holistic/sessions)
- Adapter docs:
- codex: [.holistic/context/adapters/codex.md](.holistic/context/adapters/codex.md)
- claude: [.holistic/context/adapters/claude-cowork.md](.holistic/context/adapters/claude-cowork.md)
- antigravity: [.holistic/context/adapters/antigravity.md](.holistic/context/adapters/antigravity.md)
- gemini: [.holistic/context/adapters/gemini.md](.holistic/context/adapters/gemini.md)
- copilot: [.holistic/context/adapters/copilot.md](.holistic/context/adapters/copilot.md)
- cursor: [.holistic/context/adapters/cursor.md](.holistic/context/adapters/cursor.md)
- goose: [.holistic/context/adapters/goose.md](.holistic/context/adapters/goose.md)
- gsd: [.holistic/context/adapters/gsd.md](.holistic/context/adapters/gsd.md)

## Historical Memory

- Last updated: 2026-03-22T20:03:06.997Z
- Last handoff: None yet.
- Pending sessions remembered: 0
