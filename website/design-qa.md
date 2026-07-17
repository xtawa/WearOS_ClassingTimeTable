# Design QA — Classing Landing Page

- Source visual truth path: `public/assets/classing-brand/selected-landing-reference.png`
- Implementation screenshot path: `output/playwright/desktop-full-final.png`
- Viewport: 1440 × 3503 full-page desktop
- State: default landing page at the top of the route
- Full-view comparison evidence: `output/playwright/comparison-final.png`
- Focused region comparison evidence: `output/playwright/comparison-focus-import-final.png`
- Responsive evidence: `output/playwright/mobile-final.png` at 390 × 844, plus a measured 768 × 1024 tablet pass

**Findings**

- No actionable P0, P1, or P2 visual differences remain.
- The implementation preserves the selected direction's poster hierarchy, horizontal section rhythm, hard editorial dividers, warm-paper/cobalt palette, restrained signal orange, real product-device imagery, and phone-to-watch story.

**Required Fidelity Surfaces**

- Fonts and typography: Noto Sans SC gives the Chinese display and body copy a consistent optical weight; Barlow Condensed matches the narrow section numbers and utility labels. Desktop section titles now remain on one line as in the source, while tablet/mobile allow intentional wrapping.
- Spacing and layout rhythm: the 110px numbered rail, poster panels, hard dividers, large hero breathing room, and three-step content sequence match the selected composition. The import phone now crosses the cream/cobalt split and holds the intended visual weight.
- Colors and visual tokens: `#F4F0E7`, `#111111`, `#123DB6`, and `#FF6A1A` are consistently mapped. Orange is limited to current/next-class signals. Text and controls retain strong contrast.
- Image quality and asset fidelity: the hero phone/watch composition, import phone, and Wear watch are high-resolution raster assets generated from the selected visual and real Classing UI references. No placeholder imagery, CSS silhouettes, custom SVG artwork, or emoji stand-ins are present.
- Copy and content: all product-specific copy is grounded in the repository's Android, Wear OS, import, reminder, sync, and backup capabilities. No metrics, testimonials, prices, awards, certifications, or university endorsements were invented.
- Icons: Phosphor icons provide one coherent icon family with consistent weight and alignment.
- Responsiveness: measured `scrollWidth` equals viewport width at 1440, 768, and 390 pixels. No persistent controls, headings, images, or CTA buttons are clipped.
- Accessibility: semantic headings/navigation/footer, descriptive image alt text, 44px mobile menu control, visible focus treatment, reduced-motion handling, and keyboard-operable controls are present.

**Interaction and Runtime Checks**

- Desktop and mobile navigation rendered correctly.
- Mobile menu opened, closed, and automatically dismissed after selecting `功能`.
- In-page navigation landed section 01 at a 72px header offset.
- Primary and support links expose valid GitHub destinations.
- Browser console check: 0 errors, 0 warnings.
- Production build: passed with Vite 6.4.2.

**Comparison History**

- Pass 1 (`output/playwright/comparison-v1.png`): found two P2 issues — desktop section titles wrapped too aggressively, and the import phone was too small.
- Fixes applied: reduced desktop section display scale, reserved single-line desktop titles, enlarged and repositioned the import phone, and preserved separate tablet/mobile behavior.
- Pass 2 (`output/playwright/comparison-v2.png` and `output/playwright/comparison-focus-import-v2.png`): confirmed corrected title rhythm and improved device scale; one P3 composition refinement remained around the phone/split overlap.
- Final refinement: moved the import phone across the cream/cobalt divide and aligned the blueprint lettering to the outer edge.
- Final evidence (`output/playwright/comparison-final.png` and `output/playwright/comparison-focus-import-final.png`): earlier P2 issues are resolved and no new P0/P1/P2 findings were introduced.

**Open Questions**

- None blocking handoff.

**Implementation Checklist**

- [x] Match the selected poster/grid direction.
- [x] Use real logo and product imagery.
- [x] Implement responsive desktop, tablet, and mobile layouts.
- [x] Verify core navigation and CTA behavior.
- [x] Pass production build and browser console checks.

**Follow-up Polish**

- [P3] The real Classing launcher icon is more colorful than the simplified mortarboard in the concept. It is retained intentionally because it is the product's actual brand asset.
- [P3] The generated hero is calmer than the concept's denser timetable matrix, but it preserves the same phone/watch/schedule hierarchy with cleaner responsive behavior.

final result: passed

