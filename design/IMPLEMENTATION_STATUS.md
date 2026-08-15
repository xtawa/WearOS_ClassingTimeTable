# Classing Mobile UI Implementation Status

## Active implementation

- Branch: `ai/ui/mobile-ai-first-redesign`
- Design source: `design/`
- Executable UI source: `mobile/src/main/java/com/xtawa/classingtime/ui/`
- Production adapters: `screen/MobileDashboardLayer.kt`, `screen/MobileLayersMain.kt`
- Local commits: complete on `ai/ui/mobile-ai-first-redesign`
- GitHub publish: blocked because the current environment has no HTTPS credentials; no remote branch or PR has been claimed

## Phase status

| Phase | Status | Notes |
|---|---|---|
| Design tokens and Theme | Implemented, build verification pending | Classing light/dark palette, type, shape, spacing, motion and edge-to-edge entry |
| Home components | Implemented, build verification pending | Primary course island, timeline, ambient field and AI prompt |
| Home static states | Implemented, build verification pending | Upcoming, In class, Break, Finished and No classes |
| Home motion | Implemented with current Compose APIs | Ambient state transition, stable course island reflow, progress animation, AI focus compression and prompt expansion |
| Timetable | Implemented, build verification pending | Selected-day context strip, summary island, effective occurrence timeline, directional date transitions, empty/setup states and calendar/edit handoffs |
| Course Detail | Implemented, build verification pending | Occurrence-specific immersive destination, live status/progress, source-aware back navigation and existing scoped editor handoff |
| Schedule Changes | Implemented, build verification pending | Exception history, moved/cancelled/added comparison cards, bounded filters and empty state |
| AI Assistant UI | Implemented, build verification pending | Home query handoff, contextual anchor, in-place processing, result islands, quick prompts, model/history controls and existing API/account integration |
| Responsive and accessibility review | Implemented statically, screenshot review pending | 360/390 dp, dark and 200% font previews across core screens; large-font layout adaptations, edge-to-edge insets, semantic headings/progress/live regions and 48 dp actions |

## Implementation conflicts

### Build and screenshot verification unavailable

- **Design requirement:** build and render each major screen before progressing.
- **Environment limitation:** Google Maven is unreachable and the environment has no cached `com.android.tools.build:gradle:8.5.0` artifact.
- **Current handling:** code receives static syntax and diff checks only. No build, Preview render or screenshot result is claimed.
- **Required follow-up:** rerun `:mobile:assembleDebug`, unit tests and Preview screenshot validation in a network-enabled Android build environment.

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
