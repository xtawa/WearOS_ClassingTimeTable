# Classing Website Brand Spec

## Product

- Product: Classing Timetable for Android phones and Wear OS.
- Core promise: arrange the timetable on the phone and see the next class on the wrist.
- Verified capabilities used in website copy: ICS/JSON import, manual entry, class reminders, phone-to-watch sync, Google Drive/WebDAV/official cloud options, backup and restore.

## Selected Direction

- Source visual truth: `public/assets/classing-brand/selected-landing-reference.png`.
- Direction: contemporary campus poster meets Swiss timetable grid.
- Narrative: prevent the next class from appearing as a surprise; keep courses, classrooms, reminders, and sync in one rhythm.

## Visual System

- Warm paper: `#F4F0E7`.
- Ink: `#111111`.
- Classing cobalt: `#123DB6`.
- Cobalt dark: `#0A2A89`.
- Signal orange: `#FF6A1A`, reserved for the current/next-class signal.
- Grid line: `rgba(17, 17, 17, 0.14)`.
- Typography: Noto Sans SC for Chinese display/body, Barlow Condensed for section numbers and utility labels.
- Spacing: 8px base grid.
- Radius: mostly square; pills reserved for CTA buttons and compact status chips.
- Motion: short directional wipes, schedule-marker movement, and restrained 180-700ms transitions.

## Brand Assets

- App icon: `public/assets/classing-brand/classing-app-icon.png`.
- Original mobile import reference: `public/assets/classing-brand/classing-import-reference.png`.
- Original Wear sync reference: `public/assets/classing-brand/classing-wear-sync-reference.png`.
- Generated hero devices: `public/assets/classing-brand/hero-devices.png`.
- Generated import device: `public/assets/classing-brand/import-phone.png`.
- Generated Wear device: `public/assets/classing-brand/wear-watch.png`.

## Interaction Rules

- Primary CTA points to the public GitHub project.
- In-page navigation uses explicit `window.scrollTo` positioning and never `scrollIntoView`.
- Reduce or remove motion for `prefers-reduced-motion`.
- Icons come from Phosphor Icons; no handcrafted SVG or emoji stand-ins.

