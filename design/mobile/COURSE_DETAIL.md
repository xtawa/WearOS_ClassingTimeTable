# Mobile Course Detail

## Purpose

Course Detail turns a Home/timetable course object into a focused academic workspace. It answers: **What is this occurrence, what changed, what do I need, and what can I safely edit?**

The default entry is occurrence-specific. Recurring course information remains available but must not erase temporary schedule exceptions.

## Hierarchy

1. Effective occurrence identity and status.
2. Time and room.
3. Teacher and recurrence context.
4. Change/exception information.
5. Homework, exam, reminders, and notes.
6. Edit/manage actions.

## Layout

### Expanded contextual surface

- The source course card shared-expands from Home or Timetable.
- Top safe area contains a compact back action and selected date; it is not a standard titled AppBar.
- The course accent/media field occupies the upper background at low opacity.
- Course title and status form the primary island.
- Detail sections appear as child islands below, ordered by urgency.
- Destructive or broad-scope actions are placed at the end of the scroll.

### Reference structure

| Zone | Content |
|---|---|
| Shared hero | Course title, status, effective date, time, room, accent |
| Occurrence facts | Teacher, duration, recurrence/week, notes |
| Exception island | Change comparison, cancellation/addition/conflict |
| Academic children | Homework, exam, reminders |
| Actions | Edit, copy room, open navigation, manage reminders |
| Scope management | This occurrence / from this week / whole course |

## Components

- `TimeContextHeader`
- Expanded `CurrentCourseCard` / `InformationIsland`
- `CourseStatusIndicator`
- `ClassProgress` when active
- `ScheduleChangeCard`
- `ChangeComparison`
- `HomeworkCard`
- `ExamCard`
- Reminder card
- Scope selector sheet
- Contextual action dock

## States

### Upcoming occurrence

- Show `Starts in…`, effective time, room, and teacher.
- Display active reminder state and relevant schedule change.
- If entered from an AI result, show interpreted date context near the header.

### Active occurrence

- Show `In class` and `ClassProgress`.
- Next course may appear as a small child only when it affects transition planning.
- Editing time/room during the occurrence requires explicit scope and change warning.

### Past occurrence

- Show `Finished` and exact date/time.
- Homework completion or note access may remain.
- Future recurring occurrences are linked separately; do not rewrite the past occurrence to current course data without history.

### Moved / room changed / teacher changed

- Effective values remain in the hero.
- A visible exception island shows `Before` and `Now`.
- Source and last-updated metadata appear when available.

### Cancelled

- Hero remains, marked `Cancelled`; original time and room are preserved.
- Primary actions are `Restore` when supported or `View next occurrence`.
- It must not look identical to an empty day.

### Added / make-up course

- Mark `Added` and effective date.
- Show whether it belongs to a recurring course or is one-off.

### Conflict

- Present both effective candidates with source/update information.
- Block destructive propagation until the user chooses a resolution or leaves both as unresolved.

### Missing data

- Missing room/teacher/note uses explicit unavailable copy only where the field is important.
- Do not show strings such as `null`, empty separators, or fabricated placeholders.

## Interactions

- Back/predictive back returns to the source card or selected timetable date.
- Tap room → action sheet: copy, share, open external map when supported.
- Tap teacher → no action unless a real teacher surface exists; avoid fake affordance.
- Tap schedule change → Schedule Changes filtered to this occurrence.
- Tap homework/exam → expand inline or open its owning surface when implemented.
- Tap edit → choose scope before changing recurring data:
  - This occurrence
  - From this week
  - Whole course
- Save displays the effective result and sync state; it does not instantly remove before/after history.

## Motion

- Shared course title, accent marker, time, and room preserve identity from source.
- Hero expansion: `motion.sharedMorph`.
- Detail-only sections reveal after the hero settles using 70–100 ms stagger.
- Active progress does not restart on entry.
- Scope selector enters as a tonal bottom surface, not a full destination.
- Saving a change uses field-level before/now replacement; the entire detail screen does not flash or reload.
- Predictive back reverses the shared morph when source geometry is available.

## Accessibility

- Course title is the screen heading.
- Initial announcement includes status, date, time, and room.
- Before/after change rows have explicit labels and reading order.
- Progress supplies numeric semantics and remaining-time text.
- Actions use 48 dp minimum targets and do not rely on icon-only meaning.
- At large font, hero becomes content-height and details scroll; course title never overlaps status bar.
- External map action clearly announces that it leaves Classing.

## Edge cases

- Source occurrence was deleted while detail is open.
- Schedule sync changes the occurrence during an edit draft.
- Temporary exception references a base lesson that no longer exists.
- Course crosses midnight or changes timezone.
- Multiple teachers/rooms.
- Missing end time; active progress unavailable.
- Overlapping course conflict.
- User opens a future course outside the active semester range.
- Offline edit queues locally; sync state remains visible without blocking detail.

