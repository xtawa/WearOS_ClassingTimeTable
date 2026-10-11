# Classing native motion integration

Source vocabulary: [React Bits](https://github.com/DavidHDev/react-bits). The Android UI is Jetpack Compose, not WebView/React; these are **purpose-built native analogues** rather than bundled browser components. Existing UI, business events, and accessibility semantics are preserved.

## State-driven motion

| Real product state | React Bits reference | Native Classing interaction | Implementation |
| --- | --- | --- | --- |
| Ask AI: microphone recording | Waves + Orb | Five waveform bars show measured microphone PCM RMS amplitude; recording control halo reacts to level | `AskAiVoiceInput`, `AssistantActivityVisual`, `VoiceInputButton` |
| Ask AI: stopped / transcribing | Circular flow + ShinyText | Rotating short arc; label describes transcription, never simulates speech amplitude | `AssistantActivityVisual.Transcribing` |
| Ask AI: before first output | Orb | Breathing center, two lightweight orbiting arcs; no empty response bubble | `AssistantActivityVisual.Thinking`, `AssistantContent` |
| Ask AI: streaming answer text | Typing / loading dots | Three dots show ongoing generation, alongside actual streaming text | `AssistantActivityVisual.Responding` |
| Ask AI: request finishes / fails | FadeContent | Active indicator disappears with the existing state, without permanent animation | Existing `sending` / `status` |
| Wear OS: Ask AI request in flight | Loading dots | Three subtle dots drawn in one Canvas; no WebGL/shaders or animation after request | `WearAiThinkingDots` |
| Mobile cards / action rows | Animated cards | Size transitions and spring press feedback, keeping Compose semantics | `ClassingCard`, `ClassingPage` |
| Wear cards | Animated cards | Layout change animates only when content changes | `ClassingWearSurface` |
| New timetable onboarding | Stepper | Determinate progress smoothly updates with the real step index | `MobileOnboarding` |

## Timing and resource budgets

- Microphone UI update rate is limited to **12.5 samples/second** (80 ms): one RMS calculation for an already-read 1024-sample PCM block; no audio bytes persist in animation state.
- The visualizer uses a single Compose `Canvas` with a few circles/arcs/rounded rectangles, not OGL/WebGL, external rendering engines or downloadable assets.
- Visual effects exist only while their corresponding UI state is visible; no 24/7 animation on the home screen or watch ambient surface.
- Android's disabled animator state produces a static, legible graphic; all visual states also have text.
- Recording is cancelled and the audio envelope is reset when activity stops; outdated callbacks use the existing recording generation guard.
- State changes must not submit a question, invoke the microphone, mutate the schedule, or alter cloud sync logic.
- Avoid the intensive React Bits shader backgrounds (Orb's OGL implementation, LiquidChrome, Aurora, particles) on Wear OS; translate only the motion grammar.

## Manual acceptance tests

1. Silent microphone => minimal bars; speak softly/loudly => bars change; stop => no further level updates.
2. Cancel microphone / revoke permission / leave screen => recording stops, animated waveform vanishes, no audio draft is sent.
3. Stop recording => transcription arc appears, then transcription inserted into **draft**; no automatic AI submission.
4. Send draft => thinking orb visible; first streamed response characters => generation dots; stream completes => spinner disappears.
5. Network error => status is readable and animated indicators stop.
6. Disable animations in Android developer/accessibility settings => no continuous orbit or wave, all controls usable.
7. Wear OS loading only while `sending`; inspect battery saver and round/small screens; no background animation.
8. Onboarding forwards/backwards/skips => progress updates to correct step and responds to repeated fast clicks.
9. Check both light/dark Material schemes and high font scales.
10. Run `:mobile:testDebugUnitTest`, `:mobile:compileDebugKotlin` and `:app:compileDebugKotlin` (adjust variant names if CI uses flavors).

## OOBE, timetable and shared motion expansion (2026-10-11)

| Area | React Bits pattern | Native implementation |
| --- | --- | --- |
| OOBE welcome | Orb | `ClassingOobeHero`: subtle concentric orbit around the product icon, only while welcome is in view |
| OOBE complete | AnimatedContent / Reveal | The same hero changes to a static confirmation ring with a one-shot scale-in |
| OOBE six-step flow | AnimatedContent + FadeContent | Directional slide/fade between pages; reversing moves backwards; no offscreen infinite animation |
| OOBE setup method cards | FadeContent stagger | Nine import choices enter in order (80 ms stagger); selected card morphs surface tint and scale |
| OOBE import progress | Loading dots | Audio-free, task-scoped, accessible busy motion replaces indefinite generic progress stripe |
| OOBE step indicator | Determinate animated track | Shared `ClassingBitsProgressBar`, driven by actual step index |
| Timetable date paging | AnimatedContent | Shares `ClassingBitsTransitions.horizontal` with OOBE, preventing motion-direction drift |
| Timetable day tabs | Responsive spring cards | Animated selected background/foreground and tab scale |
| Home live-course card | Responsive spring | Touch-state press scale via shared `bitsPress` |
| Home timeline | FadeContent stagger | Course rows reveal in order when the timeline opens |
| Home AI quick prompts | FadeContent stagger | Suggestions enter in order on focus |
| Home course/time bars | Smooth progress | Two custom implementations replaced with shared transform-based `ClassingBitsProgressBar` |
| Calendar sync | Loading dots / FadeContent | Busy state changes to animated dots, returning to last sync status with a crossfade |
| Settings island and action row | Responsive spring | Two duplicate hand-written press animations replaced by one `bitsPress` modifier |

### Internal reuse
- `ClassingBitsTransitions.horizontal(direction, enabled)` standardizes directional navigation animations.
- `Modifier.bitsReveal(index)` standardizes appearance-only alpha/translation, without layout remeasurement.
- `Modifier.bitsPress(pressed)` standardizes tap/press response; it is not a perpetual pulse.
- `ClassingBitsProgressBar(progress)` displays the real accessible percentage while animating a transform-only fill.
- `ClassingBitsLoadingDots()` is attached only to in-flight work, and provides indeterminate progress semantics.
- The native Orb is only mounted on OOBE's welcome page; shader-heavy React Bits effects are intentionally excluded.
- These are Compose-native **adaptations** of React Bits patterns, not copies of the original browser/GSAP/GLSL implementations.

### Additional acceptance
1. Go through OOBE forwards/backwards and skip; step direction and determinate fraction must match.
2. Tap import method cards during their first appearance; selection should never change the requested method.
3. Import JSON/ICS, cancel the chooser, test failure and retry; busy indicator stops when the operation ends.
4. Confirm successful OOBE displays the static completion ring and dashboard action still navigates.
5. Select different days in the timetable rapidly; verify date/content always match.
6. Turn off Android animations and compare OOBE, cards, tabs, and synchronization.
7. Verify timeline and quick prompts are focus/toggle scoped; no animation while offscreen.
8. Confirm TalkBack reading order and large text/layout remain unchanged.
