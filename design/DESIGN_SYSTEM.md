# Classing Mobile Design System

## 1. Design foundation

This system translates the observed AI phone concept into a timetable product. It preserves the reference video's visual and interaction grammar—ambient light, large whitespace, floating information islands, persistent context anchors, shared-element morphs, and generated UI composition—without copying its travel content.

The design target is Android portrait at 360–430 dp width, edge-to-edge, implemented later with Kotlin, Jetpack Compose, and Material 3 primitives.

### Translation from the reference to Classing

| Reference behavior | Classing translation |
|---|---|
| Flight card as persistent context | Current or next course as the persistent context object |
| AI question inherits arrival context | Ask Classing inherits date, time, active course, and schedule changes |
| Generated destination card | Generated schedule answer card |
| Parent card produces child cards | A course/date answer produces occurrence, task, or free-window cards |
| Place card expands to detail and route | Course card expands to course detail and room/navigation context |
| Completed travel task returns to Home | Answer, reminder, or accepted change returns as a compact Home summary |

### Design principles

1. **Context before inventory.** Home answers what matters now; it does not expose the whole timetable.
2. **One dominant island.** Every Home state has one primary information island. Secondary cards are smaller and lower contrast.
3. **Objects persist across states.** A course does not disappear between Home, AI, detail, and timetable; it changes representation.
4. **Whitespace is functional.** Empty area separates active context from possible next actions and gives motion room.
5. **AI produces interfaces, not chat transcripts.** A concise query can recompose cards, timelines, and actions in place.
6. **Status is semantic and redundant.** Color is supported by text, shape, position, and time information.
7. **Material 3 is infrastructure.** Theme, semantics, insets, and primitives are reused; default component appearance is not the visual target.

## 2. Layout foundation

### Reference canvases

| Width | Side inset | Primary island max width | Notes |
|---:|---:|---:|---|
| 360 dp | 16 dp | 328 dp | Compact phones; metadata may wrap to two lines |
| 390 dp | 20 dp | 350 dp | Primary design reference |
| 412–430 dp | 24 dp | 382 dp | More internal breathing room; do not stretch type |

- Content draws edge-to-edge behind system bars.
- Interactive content respects `WindowInsets.safeDrawing`.
- Status bar is transparent. Icon appearance follows the effective background behind it.
- Bottom prompt/dock clears gesture navigation by at least `navigationBarsPadding()` plus `spacing.sm`.
- Portrait is primary. Landscape is a supported timetable viewing mode, not a scaled portrait Home.

### Vertical composition

Home uses flexible zones rather than fixed rows:

1. Context header: 56–72 dp excluding status bar.
2. Greeting/status statement: content-sized, normally 48–88 dp.
3. Primary island: 220–340 dp depending on state.
4. Secondary dynamic area: 0–220 dp depending on urgency.
5. AI prompt: bottom-anchored, 56–64 dp.

The primary island may move and resize. Secondary content must not force it above the greeting or below the prompt.

## 3. Color tokens

The values below are the Classing baseline palette. Dynamic color may remap selected roles as described later, but semantic contrast must remain stable.

### Light palette

| Token | Value | Use |
|---|---|---|
| `color.background` | `#F5F7F6` | Edge-to-edge base |
| `color.backgroundAmbientPeach` | `#F2C6B8` | Low-opacity ambient light |
| `color.backgroundAmbientBlue` | `#B9D8E8` | Low-opacity ambient light |
| `color.backgroundAmbientViolet` | `#C7CEF1` | Low-opacity ambient light |
| `color.surface` | `#FCFDFC` at 86% | Primary information island |
| `color.surfaceSecondary` | `#EFF2F1` at 76% | Secondary cards and placeholders |
| `color.surfaceStrong` | `#FFFFFF` | Text-critical controls and accessibility fallback |
| `color.textPrimary` | `#171A1E` | Titles and primary numbers |
| `color.textSecondary` | `#687078` | Metadata and supporting copy |
| `color.textTertiary` | `#899097` | De-emphasized past items |
| `color.accent` | `#3E5ED7` | AI identity, active actions, current marker |
| `color.onAccent` | `#FFFFFF` | Content on accent |
| `color.warning` | `#B95B19` | Schedule changes requiring attention |
| `color.warningSurface` | `#FFE8D6` | Change card surface |
| `color.success` | `#2E795A` | Finished/synced/confirmed |
| `color.successSurface` | `#DFF3E8` | Positive state surface |
| `color.error` | `#B3261E` | Invalid or failed state |
| `color.outlineSoft` | `#DDE2E1` | Only when tonal separation is insufficient |
| `color.scrim` | `#111416` at 36% | Expanded overlays |

### Dark palette

| Token | Value | Use |
|---|---|---|
| `color.background.dark` | `#111417` | Base |
| `color.surface.dark` | `#1C2024` at 90% | Primary island |
| `color.surfaceSecondary.dark` | `#252A2E` at 82% | Secondary card |
| `color.textPrimary.dark` | `#F1F4F3` | Primary text |
| `color.textSecondary.dark` | `#B8C0C4` | Metadata |
| `color.accent.dark` | `#B8C4FF` | AI and current state |
| `color.warning.dark` | `#FFB77B` | Schedule changes |
| `color.success.dark` | `#8DD6B2` | Finished/synced |

### Course accents

Course colors label identity; they never carry state alone.

| Course family | Light accent | Dark accent |
|---|---|---|
| Physics / engineering | `#4E6FD0` | `#AFC0FF` |
| Mathematics | `#745BBE` | `#CDBBFF` |
| Biology | `#3F8063` | `#92D6B3` |
| Chemistry | `#A66B20` | `#F1C47B` |
| Language | `#B85F68` | `#FFB2B9` |
| General / unassigned | `#60727B` | `#B7C8D0` |

Each course accent is paired with a short label, course name, and optional icon/pattern. Users can customize colors, but the app must validate text contrast.

### Dynamic color compatibility

- Dynamic color is opt-in through Settings and defaults to **Blend**, not full replacement.
- Blend may remap `backgroundAmbient*`, `accent`, and neutral surface hue.
- It must not remap `warning`, `error`, `success`, or the user's explicit course colors.
- If system colors reduce contrast or make adjacent courses indistinguishable, fall back to baseline semantic roles.
- A **Classic Classing** mode uses the baseline palette unchanged.

## 4. Gradient and surface treatment

### Ambient field

- Use 2–3 large, blurred radial color fields behind content.
- Typical alpha: 12–24% in light mode, 8–16% in dark mode.
- The field may change slowly by Home state: cool blue before class, course accent while in class, warm peach after classes.
- Do not animate hue continuously. State changes crossfade position and opacity over long durations.

### Information islands

- Primary surface uses a milky neutral with subtle background color transmission.
- Border is normally absent. Use `outlineSoft` only in high-contrast mode or over complex dynamic color.
- Shadow is wide and low-opacity: perceived elevation rather than a hard drop shadow.
- Android blur is optional. The required fallback is an opaque tonal surface with identical layout and contrast.
- Nested cards should use tonal difference, not additional elevation.

## 5. Spacing tokens

| Token | Value | Use |
|---|---:|---|
| `spacing.xxs` | 4 dp | Icon/label micro gaps |
| `spacing.xs` | 8 dp | Inline metadata, chip internals |
| `spacing.sm` | 12 dp | Adjacent controls and compact card padding |
| `spacing.md` | 16 dp | Standard card padding |
| `spacing.lg` | 20 dp | Screen inset on reference width |
| `spacing.xl` | 24 dp | Section separation and spacious padding |
| `spacing.xxl` | 32 dp | Context separation |
| `spacing.xxxl` | 48 dp | Deliberate whitespace between primary zones |

Spacing follows relationships, not a global `Arrangement.spacedBy` value. Parent/child items use smaller gaps than unrelated sections.

## 6. Radius tokens

| Token | Value | Use |
|---|---:|---|
| `radius.small` | 12 dp | Small badges, compact timeline items |
| `radius.medium` | 20 dp | Secondary cards and sheets |
| `radius.large` | 28 dp | Primary information islands |
| `radius.extraLarge` | 36 dp | Expanded contextual surfaces |
| `radius.pill` | 50% of height | Prompt bars, chips, primary inline actions |
| `radius.circle` | 50% of width | Avatars, icon actions, timeline nodes |

Shared transitions interpolate between source and destination radius. Do not snap a 28 dp card directly to a square full-screen surface.

## 7. Typography

Font family uses the platform sans-serif unless a Classing brand font is supplied later. Use variable/system weights where available.

| Token | Size / line height | Weight | Use |
|---|---|---|---|
| `type.display` | 36 / 40 sp | 600 | Greeting, `Classes finished`, empty-state statement |
| `type.timeHero` | 56 / 60 sp | 600 | `13 min`, `32 min`, break countdown |
| `type.headline` | 28 / 34 sp | 600 | Current course title, AI result title |
| `type.title` | 21 / 27 sp | 600 | Card titles and page context titles |
| `type.body` | 16 / 24 sp | 400 | Course detail and explanatory text |
| `type.label` | 14 / 20 sp | 500 | Buttons, chips, statuses |
| `type.metadata` | 12 / 16 sp | 500 | Teacher, room, exact time, date |

Rules:

- Countdown numbers use tabular figures where supported.
- Course names may wrap to two lines; they never ellipsize on the primary island unless the device is below 360 dp.
- Metadata uses sentence case, not all caps. Short status labels such as `IN CLASS` may use uppercase only when localized length remains safe.
- Font scaling is supported to 200%. At 1.3× and above, the primary island becomes vertically scrollable only inside Course Detail; Home reflows and hides tertiary content instead of scrolling the hero.

## 8. Iconography

- Use Material Symbols Rounded or the closest available Material icon.
- Default optical size: 20–24 dp; primary circular actions: 24 dp inside 48–56 dp touch targets.
- Do not use emoji as UI icons.
- Course icons are optional and secondary to text.
- Warning and status icons always include a text label.

## 9. State and emphasis

| State | Surface behavior | Text/status behavior |
|---|---|---|
| Upcoming | Cool ambient field; primary card high clarity | Countdown is largest element |
| In class | Course accent lightly enters surface; progress gains emphasis | `In class` and remaining time both visible |
| Break | Neutral field with one bright next-course island | Break duration and next room share priority |
| Finished | Warm ambient field; success accent used sparingly | Tomorrow anchor replaces countdown |
| No class | Spacious neutral field; optional task cards remain | Free-day statement plus next academic anchor |
| Changed | Warning surface at secondary level, promoted if imminent | Before/after values and effective date |
| AI active | Existing context compresses; generated result becomes primary | Query and source date remain visible |

## 10. Accessibility and contrast

- Primary text: minimum 4.5:1 against effective surface; large display text: minimum 3:1.
- Touch targets: minimum 48 × 48 dp; 56 dp preferred for bottom prompt actions.
- Timeline line is decorative; semantics are carried by each item.
- Status never depends on hue. Use `In class`, `Starts in 13 min`, `Moved`, `Cancelled`, and `Finished` text.
- High-contrast mode removes translucency, adds a 1 dp outline, and increases secondary text contrast.
- Reduce motion replaces shared morphs with a short crossfade while preserving focus and reading order.
- Screen readers announce temporal relation first: “Next class, Physics, starts in 13 minutes, 14:10 to 14:55, Building A room 302.”

## 11. Material 3 implementation boundary

Allowed foundations include `MaterialTheme`, `Surface`, `Text`, `IconButton`, `BasicTextField`, `AnimatedContent`, shared transitions, Lookahead, and spring/tween specs.

Do not ship default `TopAppBar + Card + NavigationBar` composition for primary screens. Theme roles should map to Classing tokens, and components must use the geometry, hierarchy, and motion defined here.

