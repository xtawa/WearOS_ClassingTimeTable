# Design System Specification: The Academic Curator

## 1. Overview & Creative North Star
The "Creative North Star" for this design system is **The Academic Curator**. Moving beyond a utility-based "timetable" app, this system treats a student’s schedule as a high-end editorial gallery. 

By utilizing **Organic Brutalism**—the intersection of rigid scheduling and soft, fluid interfaces—we break the traditional "grid of boxes" look. We achieve this through intentional asymmetry, where large typographic headers bleed into white space, and nested surfaces create a tactile, paper-like depth. The goal is to transform "checking a schedule" into an experience of calm, professional clarity.

---

## 2. Color & Tonal Architecture
The palette is rooted in a deep, authoritative Indigo (`primary: #24389c`), but its "soul" comes from how it interacts with the light, atmospheric neutrals of the `surface` tokens.

### The "No-Line" Rule
**Standard 1px borders are strictly prohibited.** Sectioning must be achieved through background shifts. 
- Use `surface-container-low` (`#f4f2fc`) for the main page background.
- Use `surface-container-lowest` (`#ffffff`) for primary interactive cards.
- The boundary between elements is defined by the shift in luminance, not a structural line.

### Surface Hierarchy & Nesting
Treat the UI as a series of stacked, semi-translucent sheets. 
- **Base Layer:** `surface` (#fbf8ff).
- **Secondary Container:** `surface-container` (#efedf6) for grouping minor content.
- **Topmost Priority:** `surface-container-highest` (#e3e1ea) for urgent alerts or active states.

### Glass & Gradient Rule
For floating action buttons or high-priority headers, use a **Glassmorphism** effect:
- **Color:** `surface_tint` (#4355b9) at 80% opacity.
- **Effect:** `backdrop-blur: 20px`.
- **CTA Soul:** Apply a subtle linear gradient to primary buttons from `primary` (#24389c) to `primary_container` (#3f51b5) at a 135-degree angle to provide a premium, non-flat finish.

---

## 3. Typography: Editorial Authority
The typography system uses a high-contrast scale to create an "Editorial" feel. By pairing the wide, modern stance of **Plus Jakarta Sans** for Latin characters and headers with the clinical precision of **PingFang SC** for Chinese text, we ensure the schedule feels curated.

- **Display (The Statement):** Use `display-lg` (3.5rem) for "Day" views. This should be oversized and slightly offset to create a signature look.
- **Headline (The Context):** `headline-sm` (1.5rem) for course titles. Use `on_surface` (#1a1b22) to ensure maximum legibility.
- **Labels (The Metadata):** `label-sm` (0.6875rem) in `on_surface_variant` (#454652) for classroom locations and teacher names. 
- **Hierarchy Tip:** Use weight over color. Keep titles Semi-Bold and metadata Regular to guide the eye without adding visual noise.

---

## 4. Elevation & Depth: Tonal Layering
Traditional drop shadows are replaced by **Tonal Layering**. Depth is a physical property of the "paper" layers.

### The Layering Principle
To elevate a card, do not add a shadow. Instead:
- Place a `surface-container-lowest` (#ffffff) card on top of a `surface-container-low` (#f4f2fc) background. 
- This creates a "soft lift" that feels integrated into the OS.

### Ambient Shadows
When an element must float (e.g., a Bottom Sheet), use a **Shadow Tint**:
- **Blur:** 32px to 64px.
- **Color:** `on_surface` (#1a1b22) at 4% opacity. This mimics natural ambient light rather than a digital "glow."

### The Ghost Border Fallback
If a visual divider is required for accessibility in complex data tables:
- Use `outline-variant` (#c5c5d4) at **15% opacity**. It should be felt, not seen.

---

## 5. Component Strategies

### Cards & Schedules
- **No Dividers:** Forbid the use of lines between classes. Use `spacing-4` (1rem) of vertical white space to separate course blocks.
- **Corner Radii:** Use `rounded-lg` (2rem) for main course cards and `rounded-md` (1.5rem) for nested filter chips.

### Buttons & Interaction
- **Primary Action:** Pill-shaped (`rounded-full`), utilizing the Indigo gradient.
- **Filter Chips:** Use `surface-container-high` (#e9e7f0) with `on_surface_variant` text. When active, transition to `primary_container` with `on_primary_container` text.
- **Input Fields:** Use a "bottom-heavy" design. No box. Only a `surface-container-highest` background with a 2dp `primary` underline that animates on focus.

### Specialized Components for 'Classing'
- **The "Timeline Ghost":** A vertical line using `surface-tint` at 10% opacity that runs behind the schedule, connecting classes throughout the day.
- **Status Pills:** Small, `rounded-sm` indicators for "Ongoing" or "Cancelled," using `tertiary_fixed` (#ffdcc6) for a soft, sophisticated contrast to the blue primary.

---

## 6. Do’s and Don'ts

### Do
- **Do** allow headers to be oversized. Let the "Day of the Week" take up 30% of the screen height in a scroll-collapse header.
- **Do** use `primary_fixed_dim` (#bac3ff) for disabled states instead of grey. It keeps the palette "on-brand" even in inactive states.
- **Do** use the `16` spacing token (4rem) for bottom-of-page padding to ensure the floating navigation bar doesn't obscure content.

### Don't
- **Don't** use 100% black (#000000). Always use `on_surface` (#1a1b22) for text to maintain the "calming" indigo-tinted atmosphere.
- **Don't** use standard Material 3 "Elevated Cards" with default shadows. Stick to the Tonal Layering principle.
- **Don't** crowd the interface. If a screen feels full, increase the global margin from `spacing-4` (16dp) to `spacing-5` (20dp). Space is a luxury; use it.