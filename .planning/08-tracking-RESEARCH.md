# Phase 8 Research: Tracking Foundation

## Architecture Patterns

### 1. Room Foreign Keys & Migration
*   **Problem:** Adding a foreign key column to an existing table (`cached_articles`) in SQLite usually requires table recreation, which is risky for large datasets.
*   **Solution Strategy:**
    *   **Option A (Strict):** Recreate table.
    *   **Option B (Soft):** Add `storyId` column *without* a hard SQLite `FOREIGN KEY` constraint initially. Enforce referential integrity in the Repository layer (e.g., when deleting a Story, manually update articles).
    *   **Recommendation:** **Option B (Soft FK)** for Phase 8.
        *   Why: Simplify migration `MIGRATION_4_5`.
        *   Tradeoff: Application code must handle cleanup.
        *   Implementation: `ALTER TABLE cached_articles ADD COLUMN storyId TEXT;` (and `isTracked INTEGER NOT NULL DEFAULT 0`).
        *   *Note:* Room's `@Relation` works fine without hard SQLite FKs.

### 2. Material 3 Long-Press Menu
*   **Standard:** `Box` with `combinedClickable` wrapping the card content.
*   **Menu:** `DropdownMenu` inside the Box, toggled by a state variable.
*   **Interaction:**
    1.  User long-presses -> `expanded = true`.
    2.  `DropdownMenu` appears at touch point (or anchored to Box).
    3.  Menu items: "Follow Story", "Share", "Hide Source".
*   **Code Pattern:**
    ```kotlin
    var contextMenuExpanded by remember { mutableStateOf(false) }
    Box {
        ArticleCardContent(
            modifier = Modifier.combinedClickable(
                onClick = { onOpen() },
                onLongClick = { contextMenuExpanded = true }
            )
        )
        DropdownMenu(
            expanded = contextMenuExpanded,
            onDismissRequest = { contextMenuExpanded = false }
        ) {
            DropdownMenuItem(text = { Text("Follow Story") }, onClick = { ... })
        }
    }
    ```

## Standard Stack
*   **Persistence:** Room 2.6+ (we are on 2.6.1)
*   **UI:** M3 `DropdownMenu`, `exposed-dropdown-menu` (though that's usually for text fields).
*   **Async:** Coroutines `Flow` for reactive updates.

## Common Pitfalls
1.  **Context Menu Position:** `DropdownMenu` in a `LazyColumn` can sometimes be clipped or positioned weirdly if not careful. *Mitigation:* Use `Box` wrapping the item as the anchor.
2.  **Migration Failures:** Adding columns with `NOT NULL` without a default value crashes migrations. *Mitigation:* Use `DEFAULT 0` or `DEFAULT NULL`.

## Don't Hand-Roll
*   **SQL Migrations:** Use Room's `AutomatedMigration` if possible, or simple `db.execSQL` for adding columns. Don't try to manually copy-table-rename-delete unless absolutely necessary.
*   **Double-Click/Long-Click detection:** Use `combinedClickable`.

## Conclusion
*   Proceed with **Soft Foreign Key** (application-level integrity) to keep migration safe and simple.
*   Use **DropdownMenu** anchored to the List Item for the long-press menu.
