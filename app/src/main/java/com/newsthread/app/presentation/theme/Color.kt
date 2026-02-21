package com.newsthread.app.presentation.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Primitive Palette — Slate (Neutral Foundation)
// Unchanged from original; these anchor both light and dark surfaces.
// ---------------------------------------------------------------------------
val Slate950 = Color(0xFF020617) // Deepest Background (dark)
val Slate900 = Color(0xFF0F172B) // Surface / App Bar (dark) - Official Tailwind Slate900
val Slate800 = Color(0xFF1E293B) // Cards / Sheets (dark)
val Slate700 = Color(0xFF334155) // Borders / Dividers (dark)
val Slate600 = Color(0xFF475569) // Subtitles (dark mode secondary)
val Slate500 = Color(0xFF64748B) // Disabled / Hints
val Slate400 = Color(0xFF94A3B8) // Icons / Secondary UI
val Slate300 = Color(0xFFCBD5E1) // Secondary Text (dark)
val Slate200 = Color(0xFFE2E8F0) // Borders (light)
val Slate100 = Color(0xFFF1F5F9) // Surface Variant (light)
val Slate50  = Color(0xFFF8FAFC) // Background (light)

// ---------------------------------------------------------------------------
// Brand Accent — Amber
// Politically neutral; warm, editorial feel. Replaces Cyan as primary brand.
// Amber600 is used for text/links in light mode (WCAG AA on white).
// Amber500 is the brand swatch used for icons, buttons, nav active states.
// Amber300 is used for text/links in dark mode (WCAG AA on Slate900).
// ---------------------------------------------------------------------------
val Amber600 = Color(0xFFD97706) // Links / Headline tint — light mode
val Amber500 = Color(0xFFF59E0B) // Primary brand swatch (icons, buttons, nav)
val Amber400 = Color(0xFFFBBF24) // Hover / pressed state
val Amber300 = Color(0xFFFCD34D) // Links / Headline tint — dark mode
val Amber200 = Color(0xFFFDE68A) // Container fill (light mode primaryContainer)

// ---------------------------------------------------------------------------
// Credibility — Shield Color Scale
// Warm progression: green → amber → orange → slate.
// Orange is used for Low rather than red to avoid triggering the "danger/error"
// mental model. Amber500 is shared with the brand scale (intentional).
// ---------------------------------------------------------------------------
val Orange500 = Color(0xFFF97316) // Low Reliability shield — warm caution

// Semantic aliases for credibility shields
val CredHigh    = Color(0xFF22C55E) // Green500  — trusted, strong fact-checking
val CredMedium  = Amber500          // Amber500  — mixed record or higher bias
val CredLow     = Orange500         // Orange500 — flagged for inaccuracies / extreme bias
val CredUnrated = Slate600          // Slate600  — no rating data (dashed outline shield)

// ---------------------------------------------------------------------------
// Spectrum — Bias Visualization Layer
// These colors are RESERVED for the bias bar and perspective labels only.
// Violet is deliberately kept out of all other UI roles to avoid ambiguity.
// Both themes use the same spectrum values so users never re-learn the bar.
// ---------------------------------------------------------------------------
val BiasLeft    = Color(0xFF60A5FA) // Soft Blue  — left lean
val BiasCenter  = Color(0xFFA78BFA) // Soft Violet — center / mixed
val BiasRight   = Color(0xFFF87171) // Soft Red   — right lean
val BiasUnrated = Slate700          // No rating data

// Perspective section label colors (match bar endpoints for visual linkage)
val PerspectiveLeft  = BiasLeft   // #60A5FA
val PerspectiveRight = BiasRight  // #F87171

// ---------------------------------------------------------------------------
// Semantic Aliases — Dark Mode
// ---------------------------------------------------------------------------
val NewsPrimary          = Amber500
val NewsOnPrimary        = Slate900  // Dark text on amber button (better contrast than black)
val NewsBackground       = Slate950
val NewsSurface          = Slate900
val NewsOnSurface        = Slate300
val NewsOnSurfaceVariant = Slate400
val NewsOutline          = Slate700
val NewsLinkDark         = Amber300  // Amber text on dark surfaces
