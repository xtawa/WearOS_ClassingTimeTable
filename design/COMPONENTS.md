# Classing Mobile Component System

## 1. Component contract rules

- Components accept resolved semantic state; they do not calculate schedule truth internally.
- Stable occurrence IDs drive shared transitions and state restoration.
- Every component defines compact, standard, expanded, loading, error, and large-font behavior where applicable.
- Decorative timeline lines, ambient fields, and glass effects are excluded from accessibility traversal.
- All components support baseline, dark, high-contrast, and dynamic-color blend themes.

## 2. Required components

### GreetingHeader

**Purpose:** Establish date, user context, and a calm entry without acting like an AppBar.

**Anatomy:** contextual date/weekday, time-aware greeting, optional weather/context icon, Ask Classing identity orb, profile avatar.

**Variants:** standard, compact during AI/detail return, finished-day, no-class.

**Behavior:** greeting lowers priority when the primary island expands; avatar opens Settings; date opens Timetable at today.

**Accessibility:** date and greeting form one heading; icon-only actions have explicit labels. At large font, context icons move to a second row.

### CurrentCourseCard

**Purpose:** Primary Home island for Upcoming and In-class states.

**Anatomy:** status label, course title, time range, room, teacher, hero countdown/remaining time, course accent, optional progress, change badge.

**Variants:** upcoming-imminent, upcoming-later, in-class, changed, loading skeleton, compact context anchor.

**Behavior:** tap opens occurrence detail; shared identity persists through Home, AI, and detail. It never contains more than one primary action.

**Accessibility:** announce status and temporal relation first. Progress exposes current/maximum time semantics; countdown updates announcements only at meaningful thresholds.

### NextCourseCard

**Purpose:** Lower-priority preview of the next valid occurrence.

**Anatomy:** `Next`, title, start time, room, optional gap duration, change indicator.

**Variants:** compact, break-primary, tomorrow, changed, no-next-course.

**Behavior:** becomes primary through shared reflow when current course finishes; tap opens detail.

**Accessibility:** minimum 48 dp target; metadata remains readable without course color.

### CourseTimeline

**Purpose:** Show local temporal sequence without a dense timetable grid.

**Anatomy:** timeline spine, current-time marker, 2–5 `CourseTimelineItem`s, break/free-window segments, `View full day` action.

**Variants:** Home window, full-day Timetable, AI-cropped range, no-class next-anchor.

**Behavior:** window follows current time; item selection opens detail; full-day action opens Timetable.

**Accessibility:** expose as ordered list. Spine and connector nodes are decorative.

### CourseTimelineItem

**Purpose:** Represent one effective course occurrence or time segment.

**Anatomy:** time, status node, title, room, duration, optional change label.

**Variants:** past, current, future, cancelled, moved, break, free window, exam.

**Behavior:** current expands slightly; past collapses; moved item retains before/after access.

**Accessibility:** cancelled item reads `Cancelled` before original time; strike-through is never the only signal.

### CourseStatusIndicator

**Purpose:** Communicate temporal/exception status consistently.

**Anatomy:** shape/icon, status label, optional small course accent.

**States:** starts soon, in class, break, finished, moved, cancelled, added, delayed, conflict.

**Behavior:** may animate between states but does not pulse continuously except urgent, user-configured reminders.

**Accessibility:** color-independent label and content description.

### ClassProgress

**Purpose:** Show elapsed/remaining class time.

**Anatomy:** start label, continuous progress path, current node, end label, remaining-time text.

**Variants:** standard, compact, overtime/unknown end, paused data update.

**Behavior:** updates once per minute; smooths only the visual delta. On app resume, jumps to authoritative progress.

**Accessibility:** semantic progress value plus explicit `32 minutes remaining` text.

### ScheduleChangeCard

**Purpose:** Surface a schedule exception with enough context to act safely.

**Anatomy:** change type, affected course/date, before value, after value, effective time, source/sync state, actions.

**Variants:** time moved, room changed, teacher changed, cancelled, added/make-up, conflict, acknowledged.

**Behavior:** imminent unresolved change can pre-empt secondary Home content; tap opens Schedule Changes; acknowledgement lowers emphasis.

**Accessibility:** before/after values use explicit labels. Do not rely on arrows alone.

### HomeworkCard

**Purpose:** Represent a course-linked task without turning Home into a task manager.

**Anatomy:** course, title, due time/date, completion state, optional priority/reminder.

**Variants:** due today, upcoming, overdue, completed, compact summary.

**Behavior:** Home shows at most two urgent cards; full list belongs to course detail or future task surface.

**Accessibility:** due relation is human-readable and includes absolute date on focus.

### ExamCard

**Purpose:** Surface an upcoming exam in temporal relation to the schedule.

**Anatomy:** subject, exam label, date/time, room, countdown in days, optional preparation status.

**Variants:** upcoming, today, changed, completed, conflict.

**Behavior:** does not replace an active class hero unless the exam is the active occurrence.

**Accessibility:** announce exact date/time, not countdown alone.

### AiPromptBar

**Purpose:** Persistent low-friction intent entry.

**Anatomy:** Ask Classing orb/icon, placeholder or query, add/context action if supported, submit/voice action.

**Variants:** idle, focused, filled, listening, offline, processing, quota-limited/error.

**Behavior:** expands in place and compresses Home context; does not immediately navigate to a chat transcript.

**Accessibility:** label `Ask Classing about your schedule`; voice and submit actions are separate targets.

### AiResultCard

**Purpose:** Parent surface for a structured answer.

**Anatomy:** interpreted scope/date, result title, short summary, source badge, confidence/clarification state when needed, child slot area, actions.

**Variants:** next class, day summary, course occurrences, free window, workload, comparison, clarification, partial/error.

**Behavior:** generated at the processing anchor; can spawn child cards; clear/accept returns relevant information to Home.

**Accessibility:** heading announces answer type and interpreted time range. Child cards follow in logical order.

### QuickPromptChip

**Purpose:** Offer likely schedule questions without making AI the only path.

**Examples:** `What's next?`, `My afternoon`, `When is biology?`, `Lunch window`, `Tomorrow morning`.

**Variants:** suggested, recent, context-specific, disabled offline.

**Behavior:** tap populates and submits only when intent is unambiguous; otherwise populates composer for editing.

**Accessibility:** full question is the label; chips wrap instead of horizontal clipping at large font.

## 3. Supporting components

### ContextAnchorCard

Compact representation of current/next course or selected date while AI, detail, or changes are primary. Contains only title, temporal status, and one location/time line. It is never smaller than 64 dp high.

### BreakIsland

Combines break duration and the next course. It is not a standalone empty state. The next course title/room remains actionable, while break countdown is the dominant numeric element.

### FinishedDayIsland

Contains `Classes finished`, a completion marker, tomorrow's first course, and optional summary count. Tasks/exams remain separate children.

### NoClassIsland

Contains free-day statement, next academic anchor, optional focused task/exam, and a contextual Ask Classing suggestion. It must distinguish no effective classes from no timetable data.

### AmbientBackground

Non-interactive, 2–3 radial fields driven by semantic Home state. Has solid-color fallback and no accessibility semantics.

### InformationIsland

Foundation wrapper for milky surfaces. Parameters: emphasis level, tint role, radius, border fallback, content padding, and shared transition key. Avoid arbitrary per-screen surface styling.

### TimeContextHeader

Used outside Home to replace a conventional AppBar. Contains back/close when needed, date or selected context, and one contextual action. It does not carry multiple global destinations.

### WeekContextStrip

Compact week number/date range with previous/next controls and seven day indicators. Selected day is textual and shaped, not color-only.

### FreeWindowCard

AI/timetable component showing a bounded free interval, duration, and adjacent courses. It may include a reminder action but must not infer travel feasibility without relevant data.

### ChangeComparison

Two labeled rows, `Before` and `Now`, for time/room/teacher changes. In compact mode it shows only the changed field; full detail shows all values.

### GeneratedPlaceholderCard

Stable geometry for asynchronous AI results. Uses subtle luminance sweep or static skeleton under reduce motion. Placeholder count must reflect the known composition; do not generate fake empty cards indefinitely.

## 4. Component composition by Home state

| Home state | Primary | Secondary | Persistent bottom |
|---|---|---|---|
| Upcoming | `CurrentCourseCard(upcoming)` | Short `CourseTimeline`, urgent change | `AiPromptBar` |
| In class | `CurrentCourseCard(inClass)` + `ClassProgress` | `NextCourseCard(compact)` | `AiPromptBar(compact)` |
| Break | `BreakIsland` + promoted `NextCourseCard` | Remaining-day timeline | `AiPromptBar` |
| Finished | `FinishedDayIsland` | Homework, exam, reminder/change | `AiPromptBar` |
| No class | `NoClassIsland` | Next anchor, one task/exam group | `AiPromptBar` |
| AI result | `AiResultCard` | Generated children | Expanded composer/clear action |

## 5. Component sizing guidance

| Component | Minimum | Typical | Maximum |
|---|---:|---:|---:|
| Primary Home island | 220 dp h | 260–300 dp h | 340 dp h |
| Secondary card | 72 dp h | 88–116 dp h | content-driven |
| Compact context anchor | 64 dp h | 72–84 dp h | 96 dp h |
| AI prompt | 56 dp h | 60 dp h | content-driven focused state |
| Quick prompt chip | 48 dp h touch target | 48–52 dp | wraps |
| Timeline item | 64 dp h | 72–88 dp h | large-font content-driven |

## 6. Error and empty distinctions

- `No classes today` is a valid schedule state.
- `No timetable imported` is a setup state.
- `Schedule unavailable` is a data error.
- `AI unavailable` affects only Ask Classing, not timetable access.
- `No results for biology` is an AI/search result and includes the interpreted date range.

These states must use different components and actions.

