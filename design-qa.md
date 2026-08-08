# Design QA — mobile & Wear OS 重设计

基准规范：`ClassingTimeTable 完整设计规范 v1.0`
分支：`design/mobile-wear-ui-redesign`

## 已修复

| 项 | 规范 | 处理 |
| --- | --- | --- |
| 课程颜色被关键词推断覆盖 | §5 | `colorLabelToColor` / `courseColorFor` 改为“用户选定色优先，关键词仅作兵底” |
| Mobile / Wear 课程谱不一致 | §43 | 新增 `mobile/.../screen/CourseColors.kt`，与 Wear `Color.kt` 一一对应 |
| 缺少线性代数 / 体育色 | §5 | 补充 `#FACC15`、`#60A5FA` |
| Wear Primary 色值错误 | §40 | `#9895FF` → `#7C79F7` |
| “进行中”占用 Secondary（保留给同步状态） | §2.2 | 新增 `IndigoSuccess/Warning/Info` 语义色，课程状态改用 Success |
| 课程块填充太浅（0.10 / 0.14） | §14 | 统一为 `COURSE_BLOCK_ALPHA = 0.18`、`COURSE_BLOCK_BORDER_ALPHA = 0.32` |
| 缺少“当前进行中”强调 | §14 | 进行中课程添加 2dp 实色描边 |
| 2/3/5/9/10/14dp 破坏 4dp 栅格 | §7 | 全部归位到 4/8/12/16/20dp |
| 硬编码圆角绕过形状 token | §8 | Wear 课程卡 / Hero 改用 `MaterialTheme.shapes.medium/large` |
| 日期格式两套且未本地化（ISO / `MMM d · EEE`） | §45 | 统一为 `formatDateHeader()` 本地化输出 |
| Wear 横向内边距 8dp 低于下限 | §7.2 | 提升到 12dp |
| 死代码与失效引用 | — | 删除 `BrandHeader()`、未使用的 `CourseHeatmapGrid` 引入，修复 `HeaderInfoCard` 错位缩排 |
| “打开日历”入口重复出现两次 | §21 | 保留能力条中的一份，删除标题区重复 chip |

## 待办

- `mobile/.../screen/MobileSettingsAbout.kt` 中的 `LessonCard(lesson: LessonUi)` 仍使用 0.10 / 0.24 透明度，需单独一轮重构后改用共享常量（文件过长，本次未动）。
- Wear 热力图移除后，`R.string.heatmap_*` 可能成为冗余资源，待清理。
- Hero 渐变上 `onPrimaryContainer` 的对比度需实机测量（§33 要求 ≥ 4.5:1）。

## 验证状态

- 静态审阅：已完成。
- Gradle 构建 / Compose Preview / 截图对比：**未执行**（需在本地或 CI 跑 `./gradlew :app:assembleDebug :mobile:assembleDebug`）。
