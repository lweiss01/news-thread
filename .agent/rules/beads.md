---
trigger: always_on
---

I use Beads to track issues. If I mention beads, beads issues, or open a beads issue, I mean the beads extenstion and the bd commands

these are beads commands:

# CLI Command Reference

**For:** AI agents and developers using bd command-line interface  
**Version:** 0.21.0+

## Quick Navigation

- [Basic Operations](#basic-operations)
- [Issue Management](#issue-management)
- [Dependencies & Labels](#dependencies--labels)
- [Filtering & Search](#filtering--search)
- [Advanced Operations](#advanced-operations)
- [Molecular Chemistry](#molecular-chemistry)
- [Database Management](#database-management)
- [Editor Integration](#editor-integration)

## Basic Operations

### Check Status

```bash
# Check database path and daemon status
bd info --json

# Example output:
# {
#   "database_path": "/path/to/.beads/beads.db",
#   "issue_prefix": "bd",
#   "daemon_running": true,
#   "agent_mail_enabled": false
# }
```

### Find Work

```bash
# Find ready work (no blockers, not already claimed)
bd ready --json

# Atomically claim an issue from the ready queue
bd update <id> --claim --json               # Fails if already claimed

# Find stale issues (not updated recently)
bd stale --days 30 --json                    # Default: 30 days
bd stale --days 90 --status in_progress --json  # Find abandoned claims
bd stale --limit 20 --json                   # Limit results
```

## Issue Management

### Create Issues

```bash
# Basic creation
# IMPORTANT: Always quote titles and descriptions with double quotes
bd create "Issue title" -t bug|feature|task -p 0-4 -d "Description" --json

# Create with explicit ID (for parallel workers)
bd create "Issue title" --id worker1-100 -p 1 --json

# Create with labels (--labels or --label work)
bd create "Issue title" -t bug -p 1 -l bug,critical --json
bd create "Issue title" -t bug -p 1 --label bug,critical --json

# Examples with special characters (all require quoting):
bd create "Fix: auth doesn't validate tokens" -t bug -p 1 --json
bd create "Add support for OAuth 2.0" -d "Implement RFC 6749 (OAuth 2.0 spec)" --json
bd create "Implement auth" --spec-id "docs/specs/auth.md" --json

# Create multiple issues from markdown file
bd create -f feature-plan.md --json

# Create with description from file (avoids shell escaping issues)
bd create "Issue title" --body-file=description.md --json
bd create "Issue title" --body-file description.md -p 1 --json

# Read description from stdin
echo "Description text" | bd create "Issue title" --body-file=- --json
cat description.md | bd create "Issue title" --body-file - -p 1 --json

# Create epic with hierarchical child tasks
bd create "Auth System" -t epic -p 1 --json                     # Returns: bd-a3f8e9
bd create "Login UI" -p 1 --parent bd-a3f8e9 --json             # Auto-assigned: bd-a3f8e9.1
bd create "Backend validation" -p 1 --parent bd-a3f8e9 --json   # Auto-assigned: bd-a3f8e9.2
bd create "Tests" -p 1 --parent bd-a3f8e9 --json                # Auto-assigned: bd-a3f8e9.3

# Create and link discovered work (one command)
bd create "Found bug" -t bug -p 1 --deps discovered-from:<parent-id> --json

# Create with external reference (v0.9.2+)
bd create "Fix login" -t bug -p 1 --external-ref "gh-123" --json  # Short form
bd create "Fix login" -t bug -p 1 --external-ref "https://github.com/org/repo/issues/123" --json  # Full URL
bd create "Jira task" -t task -p 1 --external-ref "jira-PROJ-456" --json  # Custom prefix
```

### Update Issues

```bash
# Update one or more issues
bd update <id> [<id>...] --status in_progress --json
bd update <id> [<id>...] --priority 1 --json
bd update <id> [<id>...] --spec-id "docs/specs/auth.md" --json

# Update external reference (v0.9.2+)
bd update <id> --external-ref "gh-456" --json           # Short form
bd update <id> --external-ref "jira-PROJ-789" --json    # Custom prefix

# Atomically claim an issue for work (prevents race conditions)
# Sets assignee to you and status to in_progress in one atomic operation
# Fails if already claimed (assignee is not empty)
bd update <id> --claim --json

# Edit issue fields in $EDITOR (HUMANS ONLY - not for agents)
# NOTE: This command is intentionally NOT exposed via the MCP server
# Agents should use 'bd update' with field-specific parameters instead
bd edit <id>                    # Edit description
bd edit <id> --title            # Edit title
bd edit <id> --design           # Edit design notes
bd edit <id> --notes            # Edit notes
bd edit <id> --acceptance       # Edit acceptance criteria
```

### Close/Reopen Issues

```bash
# Complete work (supports multiple IDs)
bd close <id> [<id>...] --reason "Done" --json

# Reopen closed issues (supports multiple IDs)
bd reopen <id> [<id>...] --reason "Reopening" --json
```

### View Issues

```bash
# Show dependency tree
bd dep tree <id>

# Get issue details (supports multiple IDs)
bd show <id> [<id>...] --json
```

## Dependencies & Labels

### Dependencies

```bash
# Link discovered work (old way - two commands)
bd dep add <discovered-id> <parent-id> --type discovered-from

# Create and link in one command (new way - preferred)
bd create "Issue title" -t bug -p 1 --deps discovered-from:<parent-id> --json
```

### Labels

```bash
# Label management (supports multiple IDs)
bd label add <id> [<id>...] <label> --json
bd label remove <id> [<id>...] <label> --json
bd label list <id> --json
bd label list-all --json
```

### State (Labels as Cache)

For operational state tracking on role beads. Uses `<dimension>:<value>` label convention.
See [LABELS.md](LABELS.md#operational-state-pattern-labels-as-cache) for full pattern documentation.

```bash
# Query current state value
bd state <id> <dimension>                    # Output: value
bd state witness-abc patrol                  # Output: active
bd state --json witness-abc patrol           # {"issue_id": "...", "dimension": "patrol", "value": "active"}

# List all state dimensions on an issue
bd state list <id> --json
bd state list witness-abc                    # patrol: active, mode: normal, health: healthy

# Set state (creates event + updates label atomically)
bd set-state <id> <dimension>=<value> --reason "explanation" --json
bd set-state witness-abc patrol=muted --reason "Investigating stuck polecat"
bd set-state witness-abc mode=degraded --reason "High error rate"
```

**Common dimensions:**
- `patrol`: active, muted, suspended
- `mode`: normal, degraded, maintenance
- `health`: healthy, warning, failing
- `status`: idle, working, blocked

**What `set-state` does:**
1. Creates event bead with reason (source of truth)
2. Removes old `<dimension>:*` label if exists
3. Adds new `<dimension>:<value>` label (cache)

## Filtering & Search

### Basic Filters

```bash
# Filter by status, priority, type
bd list --status open --priority 1 --json               # Status and priority
bd list --assignee alice --json                         # By assignee
bd list --type bug --json                               # By issue type
bd list --id bd-123,bd-456 --json                       # Specific IDs
bd list --spec "docs/specs/" --json                     # Spec prefix
```

### Label Filters

```bash
# Labels (AND: must have ALL)
bd list --label bug,critical --json

# Labels (OR: has ANY)
bd list --label-any frontend,backend --json
```

### Text Search

```bash
# Title search (substring)
bd list --title "auth" --json

# Pattern matching (case-insensitive substring)
bd list --title-contains "auth" --json                  # Search in title
bd list --desc-contains "implement" --json              # Search in description
bd list --notes-contains "TODO" --json                  # Search in notes

# Find beads issue by external reference
bd list --json | jq -r '.[] | select(.external_ref == "gh-123") | .id'
```

### Date Range Filters

```bash
# Date range filters (YYYY-MM-DD or RFC3339)
bd list --created-after 2024-01-01 --json               # Created after date
bd list --created-before 2024-12-31 --json              # Created before date
bd list --updated-after 2024-06-01 --json               # Updated after date
bd list --updated-before 2024-12-31 --json              # Updated before date
bd list --closed-after 2024-01-01 --json                # Closed after date
bd list --closed-before 2024-12-31 --json               # Closed before date
```

### Empty/Null Checks

```bash
# Empty/null checks
bd list --empty-description --json                      # Issues with no description
bd list --no-assignee --json                            # Unassigned issues
bd list --no-labels --json                              # Issues with no labels
```

### Priority Ranges

```bash
# Priority ranges
bd list --priority-min 0 --priority-max 1 --json        # P0 and P1 only
bd list --priority-min 2 --json                         # P2 and below
```

### Combine Filters

```bash
# Combine multiple filters
bd list --status open --priority 1 --label-any urgent,critical --no-assignee --json
```

## Global Flags

Global flags work with any bd command and must appear **before** the subcommand.

### Sandbox Mode

**Auto-detection (v0.21.1+):** bd automatically detects sandboxed environments and enables sandbox mode.

When detected, you'll see: `ℹ️  Sandbox detected, using direct mode`

**Manual override:**

```bash
# Explicitly enable sandbox mode
bd --sandbox <command>

# Equivalent to combining these flags:
bd --no-daemon --no-auto-flush --no-auto-import <command>
```

**What it does:**
- Disables daemon (uses direct SQLite mode)
- Disables auto-export to JSONL
- Disables auto-import from JSONL

**When to use:** Sandboxed environments where daemon can't be controlled (permission restrictions), or when auto-detection doesn't trigger.

### Staleness Control

```bash
# Skip staleness check (emergency escape hatch)
bd --allow-stale <command>

# Example: access database even if out of sync with JSONL
bd --allow-stale ready --json
bd --allow-stale list --status open --json
```

**Shows:** `⚠️  Staleness check skipped (--allow-stale), data may be out of sync`

**⚠️ Caution:** May show stale or incomplete data. Use only when stuck and other options fail.

### Force Import

```bash
# Force metadata update even when DB appears synced
bd import --force -i .beads/issues.jsonl
```

**When to use:** `bd import` reports "0 created, 0 updated" but staleness errors persist.

**Shows:** `Metadata updated (database already in sync with JSONL)`

### Other Global Flags

```bash
# JSON output for programmatic use
bd --json <command>

# Force direct mode (bypass daemon)
bd --no-daemon <command>

# Disable auto-sync
bd --no-auto-flush <command>    # Disable auto-export to JSONL
bd --no-auto-import <command>   # Disable auto-import from JSONL

# Custom database path
bd --db /path/to/.beads/beads.db <command>

# Custom actor for audit trail
bd --actor alice <command>
```

**See also:**
- [TROUBLESHOOTING.md - Sandboxed environments](TROUBLESHOOTING.md#sandboxed-environments-codex-claude-code-etc) for detailed sandbox troubleshooting

## Advanced Operations

### Cleanup

```bash
# Clean up closed issues (bulk deletion)
bd admin cleanup --force --json                                   # Delete ALL closed issues
bd admin cleanup --older-than 30 --force --json                   # Delete closed >30 days ago
bd admin cleanup --dry-run --json                                 # Preview what would be deleted
bd admin cleanup --older-than 90 --cascade --force --json         # Delete old + dependents
```

### Orphan Detection

Find issues referenced in git commits that were never closed:

```bash
# Basic usage - scan current repo
bd orphans

# Cross-repo: scan CODE repo's commits against external BEADS database
cd ~/my-code-repo
bd orphans --db ~/my-beads-repo/.beads/beads.db

# JSON output
bd orphans --json
```

**Use case**: When your beads database lives in a separate repository from your code, run `bd orphans` from the code repo and point `--db` to the external database. This scans commits in your current directory while