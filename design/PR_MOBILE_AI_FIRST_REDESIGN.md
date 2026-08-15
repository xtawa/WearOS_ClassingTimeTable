# Classing Mobile AI-first Redesign

## Summary

Turns the approved Classing mobile design specification into the production Jetpack Compose UI source of truth. The branch keeps existing schedule projection, exception, persistence, account and AI API behavior while replacing core mobile presentation and navigation.

## UI changes

- Introduces Classing light/dark color, typography, shape, spacing and motion tokens.
- Makes the context-first Home the default mobile destination.
- Replaces the dense weekly Home presentation with a selected-day Timetable timeline.
- Adds immersive Course Detail and Schedule Changes destinations.
- Rebuilds Ask Classing as a contextual assistant surface while retaining existing API/account integration.
- Enables edge-to-edge rendering and optional Android dynamic color compatibility.

## Screens

- Home
- Timetable
- Course Detail
- Schedule Changes
- Ask Classing Assistant
- Existing Settings destination retained

## States implemented

- Home: Upcoming, In class, Break, Classes finished, No classes
- Timetable: Today, other day, empty day, no imported timetable, past/current/future courses
- Course Detail: Upcoming, In class with progress, Finished, missing optional metadata
- Schedule Changes: Moved, Cancelled, Added/make-up, filtered empty states
- Assistant: Signed out, no timetable, idle, composing, processing, safe result fallback, history, API/status error

## Motion

- Semantic ambient Home fields and state color morphs
- Stable Home course island with internal content replacement and spring progress
- AI focus compression and expanding prompt composition
- Directional Timetable day transitions
- Source-aware route depth and predictive-back-compatible return state
- Staggered Course Detail fact reveal
- Bounded Schedule Changes filter reflow
- Assistant processing anchor and send-state morph

## Accessibility

- 48 dp minimum primary touch targets
- Screen headings, course progress semantics, selected date collection semantics and AI live regions
- Explicit icon descriptions and long-press edit labels
- 200% font adaptations for week cells, time columns, comparison rows and AI suggestions
- Dark, large-font and 360 dp Previews across core screens

## Screenshots

Not attached from this environment. Android Gradle Plugin resolution is blocked because Google Maven is unavailable and the required plugin is not cached. No unrendered Preview is represented as a verified screenshot.

## Known differences from design

- Cross-destination `sharedBounds` is represented by stable identity, route-depth transitions and geometry-preserving internal motion because the repository's current Compose BOM predates the validated shared-transition implementation.
- AI replies remain Markdown inside a bounded result island. Structured course/free-window child cards require a versioned whitelisted response schema and are not inferred from arbitrary model prose.
- Homework and Exam cards remain absent from production states because no authoritative domain models exist.
- New executable-design copy still requires base/Simplified Chinese/Traditional Chinese resource extraction before release.

## Pending work

- Restore Google Maven access, then run `:mobile:assembleDebug` and unit tests.
- Render every major Preview and perform screenshot comparison/correction.
- Move new copy into locale resources.
- Validate a controlled Compose upgrade before cross-screen shared bounds.
- Attach verified screenshots to this PR.
- Add structured AI result components only after a safe schema is available.
