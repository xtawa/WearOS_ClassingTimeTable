# Prototype Instructions

Run the local server yourself and open the preview in the browser available to this environment. Do not give the user server-start instructions when you can run it.

Before making substantial visual changes, use the Product Design plugin's `get-context` skill when the visual source is unclear or no longer matches the current goal. When the user gives durable prototype-specific design feedback, preferences, or decisions, record them in `AGENTS.md`.

When implementing from a selected generated mock, treat that image as the source of truth for layout, component anatomy, density, spacing, color, typography, visible content, and hierarchy.

## Durable Classing Website Direction

- The user selected ideation option 2: contemporary campus poster plus Swiss timetable grid.
- Preserve the warm paper, Classing cobalt, ink black, and minimal signal-orange palette documented in `brand-spec.md`.
- Keep the visible structure focused on phone import, Wear OS sync, and timely class reminders. Do not add fabricated metrics, testimonials, pricing, university logos, or awards.
- Use the real app icon, product UI references, generated device assets, and Phosphor Icons. Do not replace them with CSS silhouettes, handcrafted SVG, emoji, or generic placeholders.

- 2026-10-10：品牌图标采用用户选定的 F 时间页：暖白/炭黑双主题，两个课程槽与陶土色时间点。所有图标使用 doc/brand 的位图资产，不重绘。
- 图标按 `@phosphor-icons/react/dist/csr/<Icon>` 单独导入，避免打包工具扫描整个图标库；本机前端构建保持 256 MB 内存上限。
