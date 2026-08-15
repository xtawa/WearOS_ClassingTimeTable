# Classing Mobile Motion Specification

## 1. Motion principle

Motion explains which information object persisted, which one became primary, and which one was generated. It is not decorative page transition.

The reference concept relies on card morphing, layout reflow, stable context anchors, asynchronous placeholders, and media/detail expansion. Classing maps those relationships to courses, schedule answers, changes, and tasks.

## 2. Motion tokens

| Token | Duration | Visual curve | Compose-oriented guidance |
|---|---:|---|---|
| `motion.micro` | 120–160 ms | Fast ease-out | Icon/label state, pressed response |
| `motion.contentReveal` | 220–300 ms | Ease-out | Text and content appearing inside stable card |
| `motion.sharedMorph` | 340–460 ms | Ease-in-out, strong end deceleration | Course card to detail, child result to focus |
| `motion.layoutReflow` | 480–620 ms | Smooth ease-in-out | Home state reorganization and AI composition |
| `motion.exit` | 180–240 ms | Ease-in | De-prioritized or completed secondary card |
| `motion.stagger` | 70–110 ms | Per-child delay | Related metadata, result cards |
| `motion.asyncStagger` | Variable | Result-dependent | Independent AI or sync results |
| `motion.pulse` | 900–1200 ms | Soft ease-in-out loop | AI processing and current-time node |

Suggested spring families for later implementation:

- **Settled:** damping ratio `0.86`, stiffness `420` for layout reflow.
- **Responsive:** damping ratio `0.82`, stiffness `560` for card morphs.
- **Soft:** damping ratio `0.92`, stiffness `280` for ambient/placeholder motion.

These are starting values, not API contracts. Validate on 60 Hz and 120 Hz devices.

## 3. Component transitions

### Card enter

- New contextual card starts at 96% scale and 0% opacity.
- Surface reaches full opacity before tertiary metadata.
- Title and primary time reveal first; supporting metadata follows by `motion.stagger`.
- Do not translate every card upward from the same distance. Generated children originate from their parent connector or placeholder.

### Card exit

- A card that becomes irrelevant reduces contrast and scale before leaving.
- A completed item may collapse into a timeline node or summary card.
- A dismissed error/change card exits toward its owning section, not off-screen arbitrarily.

### Primary ↔ secondary priority

- The persistent object keeps its identity key.
- Primary to secondary: scale down, reduce internal density, move toward the context anchor, then lower tonal contrast.
- Secondary to primary: move first, then reveal additional fields. Do not crossfade two unrelated card instances.

## 4. Home time-state transitions

### Upcoming → In class

- **Trigger:** current time reaches scheduled start, after authoritative schedule/change resolution.
- **From:** countdown-focused `CurrentCourseCard` with `Starts in 1 min`.
- **To:** in-progress card with progress and `In class · 45 min remaining`.
- **Motion:** countdown number contracts into remaining-time position; start marker travels to the progress line origin; background gains 8–12% course accent; next-course card moves down and decreases contrast.
- **Duration:** `480–620 ms` layout reflow, followed by `220 ms` content reveal.
- **Purpose:** show that the same course changed state, not that a new card replaced it.

### In class → Break

- **Trigger:** current time reaches scheduled end.
- **Motion:** progress completes to 100%; `In class` changes to a short success check; the current card collapses into the previous timeline item; `Break` time and next course move into the hero.
- **Duration:** progress completion `240 ms`; shared reflow `520 ms`.
- **Relationship:** the next course keeps its card identity while changing from secondary to primary.

### Break → Upcoming / In class

- During break, the break countdown and next course share one island.
- At 2 minutes remaining, room and walking relevance increase through opacity/position, not a new color alarm.
- At start time, the course title remains fixed while the break label exits and progress enters.

### Final course → Finished

- Current progress completes, the card compresses into a “Today complete” node, and tomorrow’s first class enters from the future end of the timeline.
- Homework/exam/reminder cards reveal only after the completion message settles.
- Ambient background shifts from course accent toward warm peach over `900–1200 ms`; no continuous hue animation.

### Any state → No class

- This is normally an initial composition, not a transition.
- When caused by a cancellation, the cancelled course first morphs into a `ScheduleChangeCard`; after acknowledgement it yields to the no-class composition.

## 5. AI query sequence

### Query focus

- **Trigger:** tap `AiPromptBar` or invoke its action.
- Current Home primary island scales to a compact top context anchor.
- Secondary cards dim to 35–50% opacity and then leave the accessibility traversal order.
- Composer expands upward; keyboard follows system motion.
- Suggested prompts appear with 70 ms stagger.
- Total UI reflow: `480–620 ms`.

### Submit → processing

- Query text remains visible briefly as confirmation.
- Keyboard exits with system animation.
- A bounded processing field forms where results will appear; center orb pulses and status text describes the current stage (`Reading today’s schedule`, `Comparing the week`).
- The top course/date context anchor remains stable.
- Never show a fake percentage unless the backend provides determinate progress.

### Processing → result

- Create final result geometry first.
- A parent `AiResultCard` emerges from the processing field at 96% scale.
- Child cards use same-size placeholders and replace content blur-to-sharp as data resolves.
- Result title appears before child cards; actions appear after at least one useful result exists.
- Partial results are allowed; failed children become explicit retry cards without collapsing the layout.

### Result → Home recomposition

- Clearing or accepting a result does not restore an old Home snapshot.
- Relevant result becomes a compact summary or highlights matching timeline items.
- The previously active course expands from context anchor back to its time-appropriate Home form.
- Layout reflow: `520–620 ms`.

## 6. Layout recomposition patterns

### `When is biology?`

1. Current Physics island contracts to top context.
2. `Biology` parent card enters center.
3. Two occurrence placeholders appear below.
4. `Tuesday 10:20` and `Thursday 14:10` reveal independently.
5. Other Home cards leave or dim; urgent current-class information remains as a compact, readable anchor.

### `What's my afternoon like?`

1. Greeting compresses.
2. Today timeline expands and crops to 12:00 onward.
3. Courses, breaks, and a free window reflow along one vertical line.
4. A summary card (`2 classes · 1h 35m free`) appears as parent above the timeline.

### `Do I have time for lunch?`

1. Today's course context remains top.
2. A free-window card is generated with start/end time and duration.
3. Adjacent courses appear as smaller boundary cards.
4. If no sufficient window exists, the UI shows constraints rather than a generic negative message.

## 7. Expanded detail

- Course card uses a shared transition into `Course Detail`.
- Course title, accent marker, time, and room preserve identity.
- Card radius interpolates from `radius.large` to the destination surface geometry.
- Detail-only sections reveal after the shared elements settle.
- Predictive back reverses progress when available; otherwise use the same shared morph in reverse.

## 8. Schedule change motion

- A changed field animates from old value to new value using spatial replacement: old value moves up/fades, new value moves into its exact position.
- The before/after comparison remains visible until acknowledged.
- Imminent changes may push the next-course card down, but never cover it.
- Cancellation collapses the course card into a struck-through timeline item and promotes the next valid course.

## 9. Reduce motion

When system reduce-motion preference is active:

- Replace shared transforms with `120–180 ms` crossfades.
- Disable ambient field movement, processing scale pulse, and current-location pulse; retain static emphasis.
- Do not stagger more than 40 ms.
- Preserve final focus destination and semantic announcement.

## 10. Motion accessibility and correctness

- State announcements occur after geometry settles, not on every animation frame.
- Time updates do not animate every minute unless the displayed unit changes meaningfully.
- Background work must not restart animations on recomposition.
- App resume computes the correct final state immediately; it does not replay missed class transitions.
- All shared identities are keyed by stable course occurrence ID, not course title.

