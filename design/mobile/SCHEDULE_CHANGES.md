# Mobile Schedule Changes

## Purpose

Schedule Changes is the authoritative exception surface for temporary moves, cancellations, added classes, room/teacher changes, and conflicts. It answers: **What changed, when does it apply, and has the student acknowledged it?**

Home shows only the most urgent change. This destination preserves full history and comparison without crowding the live course context.

## Hierarchy

1. Imminent unresolved changes.
2. Changes affecting today.
3. Upcoming changes this week.
4. Conflicts requiring resolution.
5. Acknowledged/recent history.
6. Sync/source metadata when relevant.

## Layout

| Zone | Content |
|---|---|
| `TimeContextHeader` | Back, `Schedule changes`, unresolved count/filter |
| Priority island | Most urgent change with effective time and direct action |
| Change timeline | Grouped by Today, Tomorrow, This week, Earlier |
| Filter chips | All, Unresolved, Moved, Cancelled, Added, Conflicts |
| Context dock | Refresh/sync only when actionable |

The screen uses spacious change islands and before/after comparisons. It is not a notification inbox with dense identical rows.

## Components

- `TimeContextHeader`
- `ScheduleChangeCard`
- `ChangeComparison`
- `CourseStatusIndicator`
- Compact course context card
- Group/date header
- Filter chips
- Conflict resolution card
- Sync/freshness label
- Empty-state island

## States

### Moved time

- Title: `Physics moved`.
- Effective date and occurrence.
- `Before · 14:10–14:55`.
- `Now · 15:05–15:50`.
- Room/teacher displayed only if also changed.
- Action: `Open course`; secondary: `Acknowledge`.

### Room changed

- Primary changed field is room: `A302 → A205`.
- Time remains visible as occurrence context.
- If imminent, Home promotes this card above the current/next course secondary region.

### Teacher changed

- Explicit `Teacher changed` label and before/now names.
- Teacher data remains optional; missing previous value is `Not provided`, not blank.

### Cancelled

- Course identity and original time remain visible.
- Status: `Cancelled` with effective date.
- Optional restore action appears only when the current user has permission and the underlying model supports it.
- Acknowledging never deletes cancellation history.

### Added / make-up class

- Mark `Added` or `Make-up class`.
- Show whether it is a one-off occurrence or linked to a recurring course.
- Emphasize date/time/room and source.

### Conflict

- Display both candidate values/sources.
- Primary action: `Review conflict`.
- No automatic acknowledgement.
- If conflict affects current/next course, Home shows a conflict hero rather than guessing.

### Acknowledged

- Lower contrast and success/check label.
- Remains in history based on retention policy.
- Can be reopened for before/after details.

### Empty

- `No schedule changes` with supporting line `Your effective timetable matches the regular schedule`.
- Show `View timetable` and current sync freshness when useful.
- Preserve ambient system and whitespace; do not show a bare empty list.

### Offline / stale

- Local exception data remains visible.
- Show `Last updated…` when stale status matters.
- Refresh action appears only if sync is configured.

## Interactions

- Tap change card → expand inline details or Course Detail at affected occurrence.
- Tap `Acknowledge` → lower urgency and update Home; record is retained.
- Filter chips update groups without navigating.
- Tap conflict → resolution surface with explicit options and source information.
- Tap source/sync detail → relevant Settings/diagnostics only when the user asks for it.
- Predictive back returns to the exact Home or Course Detail context that launched the screen.

## Motion

- Home change card shared-expands into the priority island.
- Before value moves up/fades while now value takes its position when a live update arrives.
- Acknowledgement compresses the card into its date group; subsequent cards reflow with `motion.layoutReflow`.
- Filter changes use bounded list reflow; groups do not all fade simultaneously.
- New urgent change enters from its affected course/date context and announces once.
- Reduce motion uses content replacement and static emphasis.

## Accessibility

- Change type is announced before course and effective date.
- `Before` and `Now` labels are explicit; arrows are decorative.
- Filter chips announce selected state and result count.
- Acknowledgement action explains that it marks seen and does not undo the change.
- Conflict options include complete time, room, teacher, and source information.
- Large font stacks comparisons vertically and keeps actions full width.

## Edge cases

- Multiple changes affect the same occurrence; combine into one card with multiple changed fields.
- A change is reverted; show `Restored to original` with history.
- Base course deleted while an exception remains.
- Two sources issue contradictory changes.
- Change arrives while user views/edits the occurrence.
- Effective date outside semester or hidden weekend.
- Device is offline when acknowledgement occurs; queue locally and expose sync state.
- Timezone changes alter displayed local times; retain source timezone metadata.

