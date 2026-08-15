# Classing Mobile AI-first Redesign

## Summary

Turns the approved Classing mobile design specification into the production Jetpack Compose UI source of truth. The branch keeps existing schedule projection, exception, persistence, account and AI API behavior while replacing core mobile presentation and navigation.

## UI changes

- Introduces Classing light/dark color, typography, shape, spacing and motion tokens.
- Makes the context-first Home the default mobile destination.
- Replaces the dense weekly Home presentation with a selected-day Timetable timeline.
- Adds immersive Course Detail and Schedule Changes destinations.
- Rebuilds Ask Classing as a contextual assistant surface while retaining existing API/account integration.
- Removes the persistent root app bar and bottom navigation in favor of contextual, source-aware page headers.
- Unifies Settings, import, account, reminder, sync, Wear, cloud and confirmation surfaces with shared information-island geometry.
- Extends the same information-island, spacing and pill-radius tokens through onboarding and import method surfaces so secondary flows no longer fall back to isolated Material card geometry.
- Adds persisted Appearance controls for System / Light / Dark and explicit opt-in Android system color.
- Enables edge-to-edge rendering.
- Corrects legacy week-range defaults so newly created courses remain visible in Home, Timetable and Calendar throughout the natural calendar year.

## Screens

- Home
- Timetable
- Calendar
- Course Detail
- Schedule Changes
- Ask Classing Assistant
- Settings home and secondary settings routes
- Appearance
- Import / backup / restore
- Account / reminder / sync / Wear / cloud / About

## States implemented

- Home: Upcoming, In class, Break, Classes finished, No classes
- Timetable: Today, other day, empty day, no imported timetable, past/current/future courses
- Course Detail: Upcoming, In class with progress, Finished, missing optional metadata
- Schedule Changes: Moved, Cancelled, Added/make-up, filtered empty states
- Assistant: Signed out, no timetable, idle, composing, processing, safe result fallback, history, API/status error
- Appearance: Follow system, Light, Dark, system color off/on

## Motion

- Semantic ambient Home fields and state color morphs
- Stable Home course island with internal content replacement and spring progress
- AI focus compression and expanding prompt composition
- Directional Timetable and route-depth transitions
- Source-aware route depth and predictive-back-compatible return state
- Staggered Course Detail fact reveal
- Bounded Schedule Changes filter reflow
- Assistant processing anchor and send-state morph
- Immediate whole-app theme recomposition for Appearance changes

## Accessibility

- 48 dp minimum primary touch targets
- Screen headings, course progress semantics, selected date collection semantics and AI live regions
- Explicit icon descriptions and long-press edit labels
- Large-font adaptations for week cells, time columns, comparison rows, settings actions and AI suggestions
- Light, dark, large-font and 360 dp Previews across core screens and Settings

## Screenshots

Not attached from this environment. The local runner cannot resolve the Android Gradle Plugin from Google Maven, so Compose Preview rendering is not available here. GitHub Actions is used for clean compilation, unit tests, lint and release-build verification; no unrendered Preview is represented as a verified screenshot.

## Known differences from design

- Cross-destination `sharedBounds` is represented by stable identity, route-depth transitions and geometry-preserving internal motion because the repository's current Compose BOM predates the validated shared-transition implementation.
- Material 3 dynamic schemes replace the full scheme. The setting is therefore labeled **System color**, is opt-in and defaults off; the approved role-by-role Classing Blend remains pending.
- AI replies remain Markdown inside a bounded result island. Structured course/free-window child cards require a versioned whitelisted response schema and are not inferred from arbitrary model prose.
- Homework and Exam cards remain absent from production states because no authoritative domain models exist.

## Pending work

- Render every major Preview and perform screenshot comparison/correction in an Android Studio or screenshot-test environment.
- Finish locale extraction for remaining executable-design copy.
- Validate a controlled Compose upgrade before cross-screen shared bounds.
- Attach verified screenshots to this PR.
- Add structured AI result components only after a safe schema is available.
