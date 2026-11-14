# Apple preview design references

- Source of truth for behavior: `../iOS`, `../watchOS`, `../Widgets` SwiftUI sources.
- App identity: existing Android `mobile/src/main/res/drawable-nodpi/classing_icon_light.png`, resized without redrawing into `assets/icon.png` and `../Assets.xcassets`.
- Existing design tokens: `mobile/src/main/java/com/xtawa/classingtime/ui/theme/Color.kt` (light accent `#3E5ED7`, dark `#B8C4FF`, pale neutral surfaces). The Apple adaptation uses an indigo Material/Liquid Glass treatment and system typography.
- Positioning: an interactive layout/workflow reference at laptop/phone viewing distance; quiet, functional, readable. This is not a marketing page or native device screenshot.
- Typography: native system stack, 31px phone title / 15px course title / 12px body. 24px radius, 4/8pt spacing. Respect reduced motion, reduced transparency and font sizing.
- Shared synthetic data: `../Fixtures/demo.json`. Three inspection states: normal, empty, conflicting schedule. No actual accounts, tokens, receipts, credentials or cloud responses are fabricated.
- Native screenshot artifacts: generated only by the macOS GitHub Actions job; not included or claimed to exist until that job runs.
