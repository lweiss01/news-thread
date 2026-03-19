# T02: Plan 02

**Slice:** S14 — **Milestone:** M001

## Description

Standardize ViewModel dependencies to use UseCases (where appropriate), fix TrackingViewModel's AndroidViewModel pattern, and clean up MainActivity's manual DI.

Purpose: Complete the architecture refactor by ensuring ViewModels follow the pragmatic rule (UseCases for domain logic, direct repos OK for simple reads) and all dependency injection goes through Hilt.

Output: 3 refactored ViewModels, 1 cleaned-up MainActivity, 1 fixed DatabaseSeeder. No behavior changes.
