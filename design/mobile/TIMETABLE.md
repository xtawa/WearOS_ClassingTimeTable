# Mobile Timetable

## Purpose

Timetable provides complete schedule inspection and management without making a dense weekly grid the Home experience. It answers: **What is scheduled on a chosen day or week, and how do occurrences relate?**

## Hierarchy

1. Selected week and date context.
2. Today/current-time relation when visible.
3. Selected day's effective occurrences.
4. Schedule changes and conflicts.
5. Alternate overview modes and management actions.

## Layout

### Portrait default

| Zone | Content |
|---|---|
| `TimeContextHeader` | Back, week/date label, `Today` contextual action |
| `WeekContextStrip` | Seven days with class count/occupancy indicator |
| Day summary island | Selected date, number of classes, first/last time, free-window summary |
| Full-day `CourseTimeline` | Ordered effective occurrences and breaks |
| Context action dock | Add/import/view-mode actions as needed, above navigation inset |

The selected-day timeline is the default at 360–430 dp. It is more legible than compressing seven columns into portrait width and matches the reference's vertical, context-first composition.

### Optional week overview

- A compact week occupancy ribbon shows each day's load and earliest/latest course.
- Selecting a day updates the timeline through horizontal shared-position motion.
- An explicit `Grid overview` mode may show a dense weekly matrix for users who need it.
- At 360–390 dp, grid overview scrolls horizontally with frozen time labels.
- At 412–430 dp, allow a partial next-day peek but do not shrink course text below `type.metadata`.
- Landscape may show 5–7 day columns depending on width and user weekend setting.

## Components

- `TimeContextHeader`
- `WeekContextStrip`
- `InformationIsland`
- `CourseTimeline`
- `CourseTimelineItem`
- `CourseStatusIndicator`
- `ScheduleChangeCard`
- `ChangeComparison`
- `FreeWindowCard`
- Course grid block (overview mode)
- Compact contextual action dock

## States

### Today

- Selected day is visually and semantically marked `Today`.
- Timeline scroll position centers the current/next occurrence.
- Current-time node is visible only within the day's time range.
- Past occurrences are reduced but remain available.

### Other day

- Day summary becomes primary.
- No current-time marker.
- Relative labels include absolute date to avoid ambiguity.

### Current week / other week

- Current week uses a small `This week` label.
- Other weeks show date range and semester week number when enabled.
- Returning to current week restores the last selected current-week day, defaulting to today.

### No classes on selected day

- Show a spacious day island: `No classes on Wednesday`.
- Show nearest previous/next academic anchor in the week.
- Keep add/import and Ask Classing entry contextual, not as a full-screen error.

### No timetable data

- Distinct setup state: import ICS/JSON, add manually, or restore/sync.
- Never reuse the no-class visual copy.

### Schedule changes

- Changed occurrences appear at the effective position.
- Moved item includes `Moved from 10:20`.
- Cancelled item remains as a collapsed, labelled row until filtered out explicitly.
- Added/make-up class includes `Added` label.

### Conflict

- Conflicting blocks use a shared conflict group and explicit `Conflict` text.
- In timeline mode, conflicts appear side-by-side only when width permits; otherwise stacked under one conflict header.
- Editing requires the user to resolve scope and occurrence.

## Interactions

- Tap day → select and update timeline.
- Swipe left/right on the timeline → adjacent day; gesture begins only outside horizontally interactive child content.
- Swipe the week header → previous/next week.
- Tap course → Course Detail for the effective occurrence.
- Long press may open a context menu for power users, but every action must also be reachable from Course Detail.
- Tap an empty interval → optional add-class action with that time prefilled.
- Tap changed label → Schedule Changes filtered to the occurrence.
- `Today` returns to current date and centers current/next course.
- Ask Classing can receive selected date/week as context.

## Motion

- Day changes move timeline content in the selected direction, `240–320 ms`, while week header remains stable.
- Week changes reflow the day strip; selected-day identity is preserved when the same date remains visible.
- Course tap uses shared card expansion into Course Detail.
- Switching timeline/grid uses `AnimatedContent` with geometry-aware reflow, not a simple fade.
- Current-time node uses a subtle static glow; optional pulse stops under reduce motion.
- Changed occurrence values use before/now spatial replacement.

## Accessibility

- Week strip is a collection of seven selectable dates, each announcing day, date, class count, and selected/today state.
- Timeline is an ordered list with explicit times; visual vertical position is not the only time cue.
- Grid overview has an accessible alternative traversal ordered by day then time.
- Pinch zoom is not required. Density controls use explicit buttons and preserve font scale.
- All edit/reorder gestures have button/menu alternatives.
- At 200% font, default to timeline mode and disable dense grid unless the user explicitly selects it.

## Edge cases

- Week starts Sunday or Monday according to locale/user setting.
- Weekend hidden: changes affecting hidden weekend surface an indicator and remain reachable.
- Cross-midnight course: split visual span while preserving one occurrence identity.
- Timezone change: show schedule timezone when different from device timezone.
- Semester-week parity and out-of-range occurrences.
- Duplicate imports and overlapping recurring exceptions.
- More than 12 classes in one day: timeline scrolls; Home remains unaffected.
- Missing start/end time: place in an `Unscheduled` group instead of guessing vertical position.
- Sync update while editing: preserve draft and show conflict before overwrite.

