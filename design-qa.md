# ClassingTimeTable UI redesign QA

**Source visual truth**

- Mobile dark reference: `/workspace/scratch/c20875b8cb22/upload/95B66A1F-79BD-4871-84B0-5BA825068615.jpeg`
- Mobile design tokens: `/workspace/scratch/c20875b8cb22/upload/656836BF-4DE7-4A4D-92D6-12FB31280371.jpeg`
- Wear interaction reference: `/workspace/scratch/c20875b8cb22/upload/77CBC67F-D260-48E1-9A6D-1A2090471424.jpeg`
- Product specification: `/workspace/scratch/c20875b8cb22/upload/ClassingTimeTable_完整设计规范_v1.0.md`
- Source image dimensions: 1536 × 864 px at 72 dpi.

**Implementation evidence**

- Intended Mobile viewport: 360–412 dp, dark and light themes.
- Intended Wear viewport: 220 × 220 dp round screen, dark theme.
- Implementation screenshot: unavailable.
- State: Mobile next-class/today timetable and Wear next-class/today list/course detail.
- Density normalization: not applicable because an implementation capture could not be produced.

**Full-view comparison evidence**

Blocked. The project Gradle distribution is available locally, but Android Gradle Plugin and Compose dependencies are not cached and the execution environment cannot reach Maven/Google repositories. The native Compose app therefore cannot be assembled or launched for a same-viewport screenshot comparison.

**Focused region comparison evidence**

Blocked for the same reason. No implementation image exists for the Mobile hero/timeline, Wear hero, Wear lesson rows, or Wear course detail regions.

**Static findings**

- Typography tokens, 4 dp spacing rhythm, surface hierarchy, radii, semantic course colors, and 48 dp Wear action targets were mapped from the supplied specification.
- Wear navigation continues to use `ScalingLazyColumn`, preserving round-screen scaling and rotary scrolling behavior already present in the application.
- Mobile and Wear business state, navigation callbacks, synchronization, and course calculations were left intact.
- XML resource parsing and `git diff --check` pass.

**Comparison history**

- No visual iteration could be completed because the first implementation capture is blocked.

**Remaining checks**

- Assemble Mobile and Wear debug builds in an environment with Android/Compose dependencies.
- Capture Mobile at 390 × 844 dp in dark and light modes.
- Capture Wear at 220 × 220 dp with a round device profile.
- Compare hero proportions, text wrapping, circular safe-area clipping, course-row density, and contrast against the source images.
- Run unit tests and Android lint.

final result: blocked
