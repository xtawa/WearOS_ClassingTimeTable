# Mobile AI Schedule Assistant

## Purpose

Ask Classing converts natural-language schedule questions into temporary, structured UI compositions. It should answer simple questions in the current surface and reserve an expanded assistant view for clarification, comparison, or longer reasoning.

It is not a generic chatbot and does not replace direct timetable navigation.

## Hierarchy

1. Protected current academic context.
2. User query and interpreted date/time range.
3. Structured answer.
4. Child course, day, task, or free-window cards.
5. Follow-up actions and quick prompts.
6. Source freshness, offline, quota, or uncertainty state.

## Layout

### Inline assistant on Home

| Zone | Content |
|---|---|
| Top context anchor | Current/next course or imminent change |
| Query/processing field | User query or semantic processing state |
| Generated parent | `AiResultCard` |
| Generated children | 1–5 bounded result cards/placeholders |
| Composer | Expanded `AiPromptBar` and quick prompts |

Inline mode is preferred for answers that fit within one parent and up to five children.

### Expanded assistant surface

Used when:

- clarification is required;
- the result spans more than one week;
- the answer combines courses, tasks, and changes;
- the user asks a follow-up that needs previous answer context;
- error/source explanation is necessary.

The expanded surface still uses cards and context anchors. It does not become a long left/right bubble transcript. Previous turns compress into small context summaries.

## Components

- `ContextAnchorCard`
- `AiPromptBar`
- `QuickPromptChip`
- `GeneratedPlaceholderCard`
- `AiResultCard`
- `CurrentCourseCard` / `NextCourseCard`
- `CourseTimeline`
- `FreeWindowCard`
- `HomeworkCard`
- `ExamCard`
- Clarification choice cards
- Source/freshness label

## States

### Idle

- `Ask Classing` prompt remains bottom-anchored.
- Contextual suggestions depend on Home state:
  - Upcoming: `Where is my next class?`
  - In class: `What's after this?`
  - Break: `How much time do I have?`
  - Finished: `Tomorrow morning`
  - No class: `Show my week`

### Focused / composing

- Current Home hero contracts to top anchor.
- Keyboard opens; quick prompts wrap above it.
- Placeholder: `Ask about your schedule`.
- The interpreted context is not shown until submit, avoiding unnecessary clutter.

### Processing

- Keyboard exits unless the user keeps editing.
- Processing field forms at the future result location.
- Status text is semantic:
  - `Reading today's schedule`
  - `Checking schedule changes`
  - `Comparing this week`
- No fake determinate percentage.

### Result — next class

Query: `What's next?`

- Parent: `Next class`.
- Child: one `NextCourseCard` with countdown, time, room, teacher.
- Action: `Open course`; optional `Show today`.

### Result — day summary

Query: `What's my afternoon like?`

- Parent: `This afternoon · 2 classes`.
- Children: cropped timeline from 12:00 onward.
- Free windows appear as explicit segments.
- Action: `Open timetable` with selected range.

### Result — course occurrences

Query: `When is biology?`

- Parent: `Biology · 2 times this week`.
- Children: `Tue 10:20 · Lab 2`, `Thu 14:10 · B104`.
- If more than five, show first useful set plus `View all in timetable`.
- Current/imminent course remains a compact protected anchor.

### Result — free window

Query: `Do I have time for lunch?`

- Parent: `Lunch window` with interpreted minimum duration if supplied.
- Child: `12:05–13:40 · 1 h 35 min free`.
- Boundary cards: preceding and following courses.
- If travel feasibility is unknown, copy says `Free in your schedule`; do not claim location feasibility.

### Result — workload

Query: `What homework is due today?`

- Parent: count and due window.
- Children: `HomeworkCard`s ordered by due time.
- Missing homework integration is stated explicitly; never infer that no homework exists from no local records.

### Clarification

- Show one concise question and 2–4 choice cards/chips.
- Examples: which Biology course, which week, minimum free duration.
- User may edit the original query instead.

### Partial result

- Parent remains usable.
- Successful child cards reveal.
- Failed child slot becomes `Couldn't load this item · Retry` without collapsing siblings.

### Offline

- Local deterministic questions may be answered without remote AI if a supported on-device resolver exists.
- Otherwise prompt states `Ask Classing needs a connection`; direct timetable actions remain available.

### Quota/rate limit

- Preserve the exact server state and reset information when available.
- Offer direct shortcuts (`Open today`, `Find Biology`) where the app can answer without AI.
- Do not obscure the timetable or existing result.

## Interactions

- Tap prompt → focus composer and compress Home.
- Tap quick prompt → submit only when its scope is unambiguous; otherwise populate for editing.
- Submit → show interpreted date/time on result.
- Tap result course → Course Detail.
- Tap `Open timetable` → Timetable with matching date/range/course highlighted.
- Tap `Clear` → recompute Home at current time, retaining useful accepted summary only when explicitly chosen.
- Follow-up prompt inherits the visible result and date range; the inherited context is inspectable and removable.
- Destructive schedule edits require a separate preview/confirmation flow and cannot occur from a single generated card tap.

## Motion

- Query focus uses Home layout reflow, not a route slide.
- Processing orb occupies the future parent-card center.
- Parent result appears before child placeholders.
- Child results reveal independently blur-to-sharp within fixed geometry.
- Selecting a result card uses shared expansion into its direct destination.
- Clearing result returns through recomposition, not restoration of a stale Home snapshot.
- Reduce motion uses short crossfades and static processing indicator.

## Accessibility

- Composer label: `Ask Classing about your schedule`.
- Processing state is announced once and remains a live region with restrained updates.
- Result parent is a heading; children form an ordered collection.
- Interpreted date/time is included in the accessible answer.
- Quick prompts wrap and remain at least 48 dp high.
- Voice input has separate start/stop labels and visible listening state when implemented.
- AI-generated uncertainty or partial data is announced before actions.

## Edge cases

- `Tomorrow` near midnight or after timezone change.
- Course names shared by multiple recurring courses.
- Query date outside active semester.
- Schedule changes arrive during generation.
- Current course begins while a result is open; protected context updates in place.
- User edits/deletes a course referenced by the result.
- AI returns unsupported component schema; fall back to a safe summary card with direct timetable link.
- Malformed or adversarial generated content; render only whitelisted component schemas and escaped text.
- No result, partial result, timeout, rate limit, expired session, and offline.

## Product constraints for later implementation

- Generated UI must use a versioned, whitelisted schema.
- Model output cannot select arbitrary Compose code, colors, icons, navigation routes, or destructive actions.
- Schedule facts are resolved from authoritative Classing data; AI may interpret intent and choose composition but cannot invent occurrences.
- Every answer records the data range and freshness used for composition.

