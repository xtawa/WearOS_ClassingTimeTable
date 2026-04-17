# Design System Strategy: The Midnight Scholar

## 1. Overview & Creative North Star
The Creative North Star for this design system is **"The Digital Curator."** 

Moving beyond the utilitarian nature of a standard class schedule, this system treats academic data as a premium editorial experience. We achieve this by moving away from "app-like" grids toward an "editorial-like" flow. The interface should feel like a high-end, obsidian-glass desk: deep, layered, and meticulously organized. We break the "template" look by utilizing intentional white space (negative space), dramatic typographic scaling, and a "No-Line" philosophy that relies on tonal depth rather than structural strokes.

---

## 2. Colors: Tonal Depth & The No-Line Rule
The palette is rooted in deep slate and vibrant indigo, optimized for high legibility in low-light environments without causing eye fatigue.

### The "No-Line" Rule
**Explicit Instruction:** Designers are prohibited from using 1px solid borders to section off content. In "The Digital Curator," boundaries are defined through background color shifts or subtle tonal transitions. For example, a `surface-container-low` section sitting on a `background` creates a clear but soft boundary that feels more premium than a stroke.

### Surface Hierarchy & Nesting
Treat the UI as a series of physical layers—like stacked sheets of tinted glass.
- **Base Layer:** `surface` (#12131a)
- **Primary Content Area:** `surface-container-low` (#1a1b22)
- **Interactive Cards:** `surface-container` (#1e1f26)
- **Floating Elements/Modals:** `surface-container-highest` (#33343c)

### The "Glass & Gradient" Rule
To avoid a flat, "out-of-the-box" Material feel, use **Glassmorphism** for floating elements (e.g., navigation bars or quick-add buttons). Use `surface-tint` (#bac3ff) at 8% opacity with a `backdrop-blur` of 20px. 

### Signature Textures
For main CTAs or "Current Class" hero states, use a subtle linear gradient: 
`linear-gradient(135deg, #dee0ff 0%, #bac3ff 100%)`. This provides a visual "soul" that flat hex codes cannot achieve.

---

## 3. Typography: The Editorial Scale
We use **Plus Jakarta Sans** for its modern, geometric clarity and academic authority.

- **Display (Large/Medium):** Reserved for "Today" headers or large time blocks. Use `-0.02em` letter spacing to give it a tight, premium editorial feel.
- **Headline (Small):** Used for Course Names. These should feel authoritative.
- **Title (Medium/Small):** Used for location and professor names.
- **Body:** Optimized for legibility. Use `on-surface-variant` (#c6c5d0) for secondary body text to reduce visual noise.
- **Labels:** Always in `uppercase` with `+0.05em` letter spacing when used for metadata (e.g., "LECTURE" or "ROOM 402").

---

## 4. Elevation & Depth: Tonal Layering
In this system, elevation is a color property, not just a shadow property.

### The Layering Principle
Depth is achieved by "stacking" the surface-container tiers. Place a `surface-container-lowest` card on a `surface-container-low` section to create a soft, natural "recessed" look.

### Ambient Shadows
When a floating effect is required (e.g., a Bottom Sheet), use extra-diffused shadows:
- **Shadow:** `0px 24px 48px rgba(12, 22, 73, 0.08)`
The shadow color is a tinted version of `on-primary-fixed`, mimicking natural ambient light rather than a generic grey drop shadow.

### The "Ghost Border" Fallback
If a border is absolutely necessary for accessibility, use the **Ghost Border**: `outline-variant` (#46464f) at 15% opacity. **Never use 100% opaque borders.**

---

## 5. Components

### Buttons
- **Primary:** Gradient-filled (`primary` to `primary-container`) with `on-primary` text. Shape: `ROUND_FULL`.
- **Secondary:** `surface-container-high` background with `primary` text. No border.
- **Tertiary:** Transparent background, `primary` text. 

### The Course Card (The "Curator" Card)
Forgo the standard card. Use a `surface-container-low` base. The "accent" color indicating the course category should be a vertical 4px pill on the left, rather than coloring the whole card. This maintains the dark-mode aesthetic while providing color-coding.

### Chips
Use `secondary-container` for the background and `on-secondary-container` for text. For "Active" states, use the `primary` color but at 20% opacity with a high-contrast `primary` label.

### Inputs
Text fields should not have bottom lines. Use `surface-container-highest` with a `ROUND_FULL` radius. The label should float above in `label-md` using the `primary` color when focused.

### Schedules & Lists
**Strict Rule:** No dividers. Use `spacing-6` (1.5rem) to separate list items. The "Time" column should use `on-surface-variant` at a smaller scale (`label-md`) to ensure the "Course Name" remains the focal point.

---

## 6. Do's and Don'ts

### Do
- **Do** use `surface-bright` for hover states to create a "glow" effect.
- **Do** use `ROUND_FULL` (9999px) for all buttons and chips to maintain the soft academic vibe.
- **Do** leverage the `tertiary` (#ffdf8f) color sparingly for "Urgent" tasks or "Exam" alerts to create a sophisticated "Gold on Obsidian" contrast.

### Don't
- **Don't** use pure black (#000000). It kills the "Academic Curator" depth and causes "smearing" on OLED screens. Use `surface` (#12131a).
- **Don't** use standard Material Design 2dp shadows. They look "cheap" in this editorial context.
- **Don't** use 1px dividers to separate classes in the schedule. Use tonal shifts between `surface-container-low` and `surface-container-high`.