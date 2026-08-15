# Mobile Home

## Purpose

Home answers one question: **What does the student need to know now?**

It is a time-driven composition, not a dashboard of all product features. It selects one primary academic context, preserves urgent exceptions, shows only the nearest useful sequence, and keeps Ask Classing reachable without a persistent navigation bar.

## Hierarchy

### Stable hierarchy

1. Time-critical schedule change, when one affects the next 60 minutes.
2. Current state statement: upcoming, in class, break, finished, or no class.
3. Primary information island.
4. One local timeline or up to two secondary cards.
5. Ask Classing prompt.
6. Profile and date affordances in the quiet context header.

### What Home deliberately excludes

- Full Monday–Sunday grid.
- Course-management controls.
- Heatmaps and semester statistics.
- Permanent bottom navigation.
- More than two homework/exam cards.
- Chat history.

These remain accessible through contextual destinations.

## Layout

### Reference: 390 × 844 dp

| Zone | Position / size | Content |
|---|---|---|
| Safe status area | System-controlled | Transparent status bar |
| Context header | x 20, top safe inset + 8; 350 × 56 | Date/greeting context, Classing orb, profile |
| State statement | x 20; content height 56–88 | Greeting or temporal label |
| Primary island | x 20; 350 × 240–310 | State-specific course/break/finished/no-class content |
| Secondary region | x 20; 350 × 84–190 | Timeline, next course, urgent task/change |
| Ask Classing | x 20; bottom safe inset + 12; 350 × 60 | `AiPromptBar` |

The area between the primary island and prompt is flexible. When content is sparse, it remains whitespace rather than stretching cards. When a change card appears, it occupies secondary space and the timeline shortens.

### Top context header

- Left: `Tue, Apr 23` and optional academic week label.
- Center/right: Classing AI orb as an identity indicator, not a large CTA.
- Right: profile avatar opens Settings.
- Tapping the date opens Timetable at today.
- No AppBar title and no overflow menu.

## Components

- `AmbientBackground`
- `GreetingHeader`
- `CurrentCourseCard`
- `NextCourseCard`
- `BreakIsland`
- `FinishedDayIsland`
- `NoClassIsland`
- `CourseTimeline`
- `CourseTimelineItem`
- `CourseStatusIndicator`
- `ClassProgress`
- `ScheduleChangeCard`
- `HomeworkCard`
- `ExamCard`
- `AiPromptBar`

## States

## State A — Upcoming class

### Example context

- Current time: 13:57
- Physics: 14:10–14:55
- Building A, room 302
- Teacher: Ms. Lin
- Starts in 13 minutes

### Hierarchy

1. `13 min`
2. `Physics`
3. `14:10–14:55`
4. `Building A · 302`
5. Teacher and course status
6. Courses after Physics

### Primary island composition

- Status label: `NEXT CLASS` or localized equivalent.
- Course title: `Physics`, `type.headline`.
- Hero countdown: `13 min`, `type.timeHero`.
- Time range and room remain on separate readable lines.
- A thin temporal arc/line connects `now` to `14:10`; it is not a determinate class-progress bar.
- Course accent appears as a small edge light, node, or 8–12% tint—not a full saturated card.
- Optional change badge sits next to the affected field.

### Secondary composition

Show a local timeline beginning with the Physics item and at most two later items:

- `14:10 Physics · A302`
- `15:05 English · A205`
- `16:00 Biology · Lab 2`

Items after the next course use reduced contrast. If a schedule change affects Physics, the change card replaces the least relevant timeline item.

### Interactions

- Tap primary island → Physics Course Detail for today's occurrence.
- Tap a timeline item → that occurrence's Course Detail.
- Tap timeline header/date → Timetable at today.
- Tap prompt → AI assistant in contextual mode.

### Motion

- Entering the 30-minute threshold promotes the next-course card through shared reflow.
- Countdown changes by minute without scale animation; meaningful thresholds (15, 10, 5, 1) may use `motion.contentReveal` once.
- At start time, countdown contracts into remaining-time position and class progress enters.

### Edge cases

- Room missing: show `Room not provided`, not an empty line.
- Teacher missing: omit teacher without shifting time/room hierarchy.
- Two simultaneous courses: show conflict primary island with both course titles and `Resolve` action.
- Course changed within 60 minutes: change comparison appears above secondary timeline.
- All-day event is not treated as an upcoming class unless typed as a class occurrence.

## State B — In class

### Example context

- Physics: 14:10–14:55
- Status: `In class`
- Remaining: 32 minutes
- Next: English at 15:05, room A205

### Hierarchy

1. `Physics`
2. `In class · 32 min remaining`
3. Class progress and end time
4. Room and teacher
5. Next course

### Primary island composition

- Course title remains dominant; countdown number becomes `32 min` remaining.
- `ClassProgress` shows start, current position, and end.
- Course accent tint increases slightly compared with Upcoming.
- The island may occupy up to 300 dp height but must not become visually noisy.
- A compact `Add note` or reminder action may appear only if product scope later supports it; it is not required by this spec.

### Secondary composition

`NextCourseCard(compact)` shows:

- `Next · English`
- `15:05`
- `A205`
- `10 min break` when derivable

No full remaining-day list is shown until the user expands the timeline.

### Interactions

- Tap hero → current occurrence detail.
- Tap progress label → announce exact end time; no modal required.
- Tap next card → next occurrence detail.
- Ask Classing remains available, but AI results must preserve the current course context anchor.

### Motion

- Progress advances visually once per minute.
- When the class finishes, progress completes before the current card collapses into the past timeline node.
- Next card moves to primary position rather than being replaced by a new instance.

### Edge cases

- App opens after scheduled end: resolve to Break/Finished immediately; do not replay completion.
- Unknown end time: hide determinate progress and show `End time unavailable`.
- Overtime event: show `Scheduled end 14:55` without negative remaining time.
- Active cancellation received during class: show high-priority change but do not erase the user's current context without acknowledgement.

## State C — Break

### Example context

- Break: 10 minutes
- Next: English at 15:05
- Room: A205

### Hierarchy

1. `Break · 10 min`
2. `English`
3. `15:05 · A205`
4. Remaining courses today

### Primary island composition

`BreakIsland` is one coherent object:

- Small status: `BREAK`
- Hero value: `10 min`
- Divider/connector indicating transition to next course
- Next course title and room in the lower half
- When two minutes remain, `A205` gains emphasis while the countdown stays stable

The break is not rendered as an empty hole between two unrelated cards.

### Secondary composition

- A two-item future timeline starts with English.
- If lunch or another long free window exists, label the segment `Free until 15:05` rather than `Break 75 min`.

### Interactions

- Tap next-course portion → English detail.
- Tap break duration → expands today timeline around this gap.
- AI suggestions prioritize `Where is my next class?` and `How much free time do I have?`.

### Motion

- Current course collapses into a completed node.
- Next course preserves identity while moving into the lower part of BreakIsland.
- At course start, the break label exits and progress enters without moving the course title.

### Edge cases

- Next course in another building: surface room/building and optional travel buffer; Classing must not claim travel feasibility without data.
- Break shortened by a moved course: show change card and authoritative new countdown.
- No later class today: resolve to Finished, not Break.

## State D — Classes finished

### Hierarchy

1. `Classes finished`
2. Tomorrow's first class
3. Homework, exam, or reminder requiring attention
4. Ask Classing

### Primary island composition

`FinishedDayIsland` includes:

- Completion statement in `type.display`.
- Small summary such as `5 classes today`.
- Tomorrow anchor: `Tomorrow · 08:00 Mathematics`.
- Room shown when available.
- Warm ambient background with restrained success accent.

### Secondary dynamic cards

At most two cards, sorted by urgency:

1. Homework due tonight/tomorrow.
2. Exam within the configured horizon.
3. Reminder.
4. Non-urgent schedule change.

If there are more, show one aggregate card such as `3 things to review`.

### Interactions

- Tap tomorrow anchor → tomorrow's course detail.
- Tap task/exam → its parent course detail or future dedicated surface.
- Tap `3 things to review` → a filtered task list when implemented.
- Ask Classing suggestions: `Tomorrow's first class`, `What homework is due?`, `Which day is lightest?`.

### Motion

- Final class progress completes and compresses into today's completion marker.
- Tomorrow card enters from the future end of the timeline.
- Task cards reveal after the primary completion message settles.

### Edge cases

- Tomorrow has no class: show the next class date, not an empty tomorrow card.
- Midnight rollover while app is open: recompute to the new day's state and announce once.
- Homework data unavailable: do not imply none exists; omit task summary or show source status when relevant.

## State E — No class today

### Purpose

This is a complete academic context, not a dead end or a single `No classes today` label.

### Hierarchy

1. Free-day statement.
2. Next academic anchor.
3. One useful optional focus: homework, exam, reminder, or schedule setup.
4. Ask Classing suggestions.

### Primary island composition

`NoClassIsland` includes:

- `Your day is open` or localized equivalent, `type.display`.
- Supporting statement: `No classes scheduled for Tuesday`.
- A quiet timeline horizon with `Now` and the next known academic event.
- Next anchor such as `Tomorrow · 08:00 Mathematics` or `Thu · 10:20 Biology`.
- Ambient background uses broad cool-neutral light and more whitespace than other states.

### Secondary composition

- If homework/exam exists, show one focused card and an aggregate count.
- If no tasks exist, preserve whitespace and offer contextual prompts; do not fill the screen with statistics.
- If the timetable has no imported data, replace this state with setup content: `Add your timetable` plus import/manual actions.

### Interactions

- Tap next anchor → Course Detail.
- Tap date → Timetable.
- Prompt suggestions: `What should I prepare for tomorrow?`, `When is biology?`, `Show my week`.

### Motion

- Initial composition uses slow ambient reveal and standard content reveal, not celebratory motion.
- When caused by cancellation, the cancelled course first appears as a change state before NoClassIsland settles.

### Edge cases

- Weekend hidden in settings still allows No-class Home to show the true date.
- Holiday and no-schedule are distinguished when holiday data exists.
- Empty semester range shows `No active semester schedule` with settings/import action.
- A pending sync must not temporarily render No-class if local data exists.

## AI-recomposed Home

### Example: `When is biology?`

1. Current/next course compresses to `ContextAnchorCard` at the top.
2. `AiResultCard` becomes the primary island: `Biology · 2 times this week`.
3. Two child cards appear: `Tue 10:20 · Lab 2`, `Thu 14:10 · B104`.
4. Other secondary Home cards leave the visual and accessibility order.
5. An urgent current-class/change anchor remains visible.
6. `Clear` returns to a recomputed Home; `Open timetable` opens the week with Biology highlighted.

This mode is reasonable because the answer is bounded and visual. Queries requiring long explanations open the expanded Assistant surface.

## Interactions

- Vertical scrolling is normally disabled on Home at default font scale; the composition fits one viewport.
- At large font or multiple urgent changes, allow a bounded scroll region while keeping the AI prompt reachable through IME/gesture-safe behavior.
- Pull-to-refresh is not the primary sync model. An explicit refresh appears only when stale/error state is actionable.
- Swiping cards to dismiss is not supported for schedule truth. Acknowledge/dismiss actions are explicit.

## Motion

- Uses relationship-specific motion from `MOTION_SPEC.md`.
- Course occurrence identity persists through Home states and details.
- AI result uses generated placeholders and layout reflow.
- State changes caused solely by time do not animate while the app is backgrounded.

## Accessibility

- First focus: state heading; second: primary island; third: urgent change/next timeline; last: Ask Classing and profile/date shortcuts.
- Announce exact course, time, room, and temporal relation in one concise node.
- Do not announce countdown every minute. Recommended thresholds: 15, 5, 1, start, 10 minutes remaining, end.
- Large font reorders metadata vertically and removes decorative timeline labels before truncating essential data.
- High contrast disables translucency and adds card outlines.
- Course accents always accompany text labels.

## Global edge cases

- Overlapping occurrences and unresolved conflicts.
- Temporary course moves across days.
- Device timezone changes and daylight-saving boundaries.
- Semester week mismatch or date outside semester.
- Cancelled current/next occurrence.
- Missing room, teacher, or end time.
- Local schedule available while cloud is stale/offline.
- AI quota exhausted or assistant offline.
- Process recreation across a time-state boundary.

