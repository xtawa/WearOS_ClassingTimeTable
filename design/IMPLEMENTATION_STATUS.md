# Classing Mobile UI Implementation Status

## Active implementation

- Branch: `ai/ui/mobile-ai-first-redesign`
- Design source: `design/`
- Executable UI source: `mobile/src/main/java/com/xtawa/classingtime/ui/`
- Production adapters: `screen/MobileDashboardLayer.kt`, `screen/MobileLayersMain.kt`
- Local commits: active on `ai/ui/mobile-ai-first-redesign`
- GitHub publish: branch is tracked by pull request #26; each coherent UI batch is pushed to the same review branch

## Phase status

| Phase | Status | Notes |
|---|---|---|
| Design tokens and Theme | Implemented | Classing light/dark palette, type, shape, spacing, motion, edge-to-edge entry and persisted System/Light/Dark appearance preference |
| Home components | Implemented, CI verified | Primary course island, timeline, ambient field and AI prompt |
| Home static states | Implemented, CI verified | Upcoming, In class, Break, Finished and No classes |
| Home motion | Implemented with current Compose APIs | Ambient state transition, stable course island reflow, progress animation, AI focus compression and prompt expansion |
| Timetable | Implemented, CI verified | Selected-day context strip, summary island, effective occurrence timeline, directional date transitions, empty/setup states and calendar/edit handoffs |
| Course Detail | Implemented, CI verified | Occurrence-specific immersive destination, live status/progress, source-aware back navigation and existing scoped editor handoff |
| Schedule Changes | Implemented, CI verified | Exception history, moved/cancelled/added comparison cards, bounded filters and empty state |
| AI Assistant UI | Implemented, CI verified | Home query handoff, contextual anchor, in-place processing, result islands, quick prompts, model/history controls and existing API/account integration |
| Settings and secondary navigation | Implemented, CI verified | Contextual headers, grouped information islands, source-aware back behavior, Appearance, account, import, reminder, sync and About routes without a persistent app bar or bottom navigation |
| Legacy-surface visual unification | Implemented, CI verification pending | Onboarding, import, settings/account/sync surfaces and shared pills now use Classing information-island, radius and spacing tokens instead of local one-off geometry |
| Responsive and accessibility review | Implemented statically, screenshot review pending | 360/390 dp, light/dark and large-font previews across core screens and Settings; large-font layout adaptations, edge-to-edge insets, semantic headings/progress/live regions and 48 dp actions |

## Schedule visibility correction

- New courses now default to the full valid week range rather than ending at week 30.
- Exact legacy natural-calendar defaults of weeks 1–30 are migrated to the full range when loaded or projected.
- User-defined ranges and semester week mode remain unchanged.
- Regression coverage verifies that a newly created course remains visible during natural calendar week 33.

## Implementation conflicts

### Local build and screenshot verification unavailable

- **Design requirement:** build and render each major screen before progressing.
- **Environment limitation:** Google Maven is unreachable and the environment has no cached `com.android.tools.build:gradle:8.5.0` artifact.
- **Current handling:** local code receives static syntax and diff checks. GitHub Actions provides clean Android compilation, unit-test, lint and release-build verification after each pushed batch. No local Preview screenshot result is claimed.
- **Latest verified functional head:** Android CI run 122 and Release hardening run 64 completed successfully for PR #26 head `f6f84bd`.
- **Current visual-unification batch:** local static diff checks pass; clean Android compilation and release verification must complete in GitHub Actions after publication.
- **Required follow-up:** render and compare Compose Preview screenshots in an Android Studio or screenshot-test environment with the required Android/Compose artifacts available.

### Dynamic color blend

- **Design requirement:** dynamic color uses a controlled Blend and preserves Classing warning, success and course identity roles.
- **Current Compose limitation:** Material 3 dynamic schemes replace the full color scheme rather than exposing a semantic partial-blend primitive.
- **Current handling:** Appearance exposes an explicit opt-in **System color** setting and keeps it off by default. Course identity colors remain explicit; the baseline Classing light/dark palettes remain the default.
- **Recommended follow-up:** add a tested role-by-role blend function before changing the default or describing the setting as Classing Blend.

### Shared transition API version

- **Design requirement:** preserve course identity with shared bounds between Home, Timetable and Course Detail.
- **Current limitation:** the repository uses Compose BOM `2024.06.00`; no dependency upgrade can be validated in the current environment.
- **Current handling:** Home uses a stable persistent course island, `AnimatedContent`, `animateContentSize`, spring progress and animated accent instead of claiming a cross-destination shared transition.
- **Required follow-up:** validate a controlled Compose upgrade before implementing Course Detail shared bounds.

### Missing academic task models

- Homework and Exam cards are defined by the design but have no production domain data in the current repository.
- Home does not invent empty or completed task states.
- UI models and Preview states will remain optional until an authoritative data source is introduced in a later non-UI phase.

### Localization cleanup

- The new executable design copy currently follows the approved English design specification while existing legacy areas retain their resource-backed locale coverage.
- Before release, the new Home, Timetable, Course Detail, Schedule Changes and Assistant copy must move into base, Simplified Chinese and Traditional Chinese resources; this was not hidden behind fabricated translations during the unbuilt phase.

### AI result composition

- The existing AI service returns Markdown text and does not expose a versioned generated-UI schema.
- Home supports focus/recomposition motion and transfers the submitted query into the expanded assistant, where it is submitted after model metadata is available.
- Existing Markdown replies render inside a bounded `AiResultCard`-style island. Course/free-window child components are intentionally not inferred from arbitrary prose.
- Fully structured inline results still require a whitelisted UI schema or deterministic local schedule resolver in a later data/API phase.
