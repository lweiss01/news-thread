# Phase 4: Similarity Matching — Context

## Decisions

### Similarity Thresholds
- **Strong match**: cosine similarity ≥ 0.70
- **Weak match**: 0.50–0.69 (shown only when < 3 total matches)
- **No match**: < 0.50 (never shown)
- **Not user-adjustable** — fixed thresholds

### Match Display
- **Max 5 per perspective** (Left, Center, Right, Unrated)
- **Ordering**: Strong matches first, weak at bottom
- **Confidence hidden from users** — no badges or percentages
- **Empty state**: "No matches found" for 0 matches
- **Expansion**: "X more matches" button navigates to full results page (not inline expand)

### API Strategy (Tiered)
1. **Always first**: Search cached feed articles (~100 articles, free)
2. **If < 3 matches AND quota available**: Search NewsAPI for broader coverage
3. **If quota exhausted**: Show local matches only + "Limited results" note

### Time Windows (Dynamic)
| Article Age | Match Window |
|-------------|--------------|
| < 24 hours (breaking) | ±48 hours |
| 1–7 days (recent) | ±7 days |
| 7+ days (old) | ±14 days |

### Time Display
- Relative times: "2 hours ago", "yesterday", "3 days ago"
- Absolute dates for older articles

---

## Deferred Features

| Feature | Target Phase |
|---------|--------------|
| Story timeline visualization | Phase 7 or future milestone |
| User-adjustable similarity threshold | Not planned |

---

## Requirements Addressed
- MATCH-03: Cosine similarity with threshold
- MATCH-04: Dynamic time windows
- MATCH-05: Feed-internal clustering
- MATCH-06: NewsAPI search fallback
