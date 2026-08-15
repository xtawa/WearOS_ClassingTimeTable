# Mobile Settings

## Purpose

Settings manages timetable behavior, reminders, appearance, sync/devices, Ask Classing, account, and maintenance without becoming a generic Material settings list. It uses progressive disclosure and large contextual islands.

## Hierarchy

1. Account/device identity and sync health when actionable.
2. Schedule behavior.
3. Reminders and daily briefing.
4. Appearance and accessibility.
5. Sync and Wear OS communication.
6. Ask Classing preferences/quota.
7. Import, backup, restore, and diagnostics.
8. About and destructive account/data actions.

## Layout

### Settings home

| Zone | Content |
|---|---|
| `TimeContextHeader` | Back, `Settings`; no global navigation tabs |
| Identity island | Profile/account, membership when relevant, sync summary |
| Context islands | Schedule, Reminders, Appearance, Sync & devices, Ask Classing |
| Maintenance islands | Import/backup, diagnostics, about |
| Destructive region | Clear timetable, sign out, delete account behind subpages/confirmation |

Each island contains a title, one-sentence status summary, and at most one trailing action. Detailed toggles live inside the island's subpage.

### Subpage

- Contextual back header.
- One hero summary of current setting state.
- Related controls grouped in tonal surfaces.
- Explanatory text appears only where behavior is non-obvious.
- Save is immediate for reversible preferences; credential/destructive changes require explicit confirmation.

## Components

- `TimeContextHeader`
- Profile/identity island
- Setting category island
- State summary label
- Switch row
- Segmented/chip selection
- Slider with explicit numeric value
- Time picker field
- Account/sync status card
- Warning/confirmation sheet
- Snapshot/history card
- High-risk action card

## States and sections

### Schedule

- Week start day.
- Weekend visibility.
- Natural/semester week numbering.
- Semester start and active range.
- Course import/manual management entry.
- Recent schedule snapshots and restore.

Preview affected week/date formatting in the hero summary before committing broad changes.

### Reminders

- Enable course reminders.
- Reminder lead time.
- Keep-alive level and exact-alarm/battery status.
- Daily briefing enable, channel, and time.
- Explain offline/on-device versus account/email behavior.

System-permission gaps use actionable status cards. Do not imply authorization from a toggle alone.

### Appearance

- Theme: System / Light / Dark.
- Color: Classing / Dynamic blend.
- Course color customization.
- High contrast.
- Reduce motion follows system with optional app override only if required.
- Preview island shows Home surface, type, and course accent together.

Current executable scope:

- System / Light / Dark applies immediately and persists locally.
- `System color` is an explicit opt-in and is disabled by default.
- The baseline Classing light/dark palettes remain the default executable design.
- Course colors, high contrast, and a role-by-role dynamic Blend remain pending; Material 3's full dynamic scheme is not presented as the approved Blend behavior.

### Sync & devices

- Wear OS connection and last acknowledgement.
- Cloud provider and scopes.
- Last successful sync and pending local changes.
- Manual sync action.
- Diagnostics behind a secondary disclosure.

Do not place raw protocol/build detail on the main settings surface unless developer mode is enabled.

### Ask Classing

- Enable/disable contextual suggestions.
- Data sources included in AI context.
- Conversation/context retention controls when backend supports them.
- Monthly compute/quota status and reset date.
- Clear assistant history/context when supported.

Disabling AI removes prompt suggestions but preserves direct timetable navigation and deterministic schedule search.

### Account

- Signed-in identity, membership status, email/username.
- Sign in/register/redeem flows.
- Change email and password recovery.
- Device QR approval.
- Sign out.
- Delete account separated behind its own high-risk flow.

### Import, backup, and restore

- ICS, JSON, manual entry.
- Preview/conflict resolution before replace/append.
- Export and restore snapshots.
- Clear timetable with confirmation and recoverability explanation.

### About

- Version/update status.
- Legal agreements and privacy.
- Website/help links.
- Developer mode and build details.

### Loading / error

- Local settings render immediately.
- Remote account/quota/sync cards show scoped loading states.
- One failed remote section does not block other settings.
- Errors remain inside their owning island with retry.

## Interactions

- Tap profile avatar on Home → Settings home.
- Tap category island → shared expansion into subpage.
- Reversible toggles apply immediately and show concise confirmation only when needed.
- Sliders expose current value and reset/default action.
- Broad schedule changes preview affected behavior before apply.
- High-risk actions use explicit confirmation with target and consequence.
- Predictive back collapses category into its Settings island, then returns to launching screen.

## Motion

- Category island shared-expands into subpage hero.
- Toggle/selection uses `motion.micro`; dependent rows reveal with `motion.contentReveal`.
- Appearance preview updates surface color/type with a controlled 220–300 ms transition.
- Sync progress remains local to the Sync island; the whole page does not enter loading state.
- Destructive confirmation enters as a stable modal/bottom surface without ambient motion.
- Reduce motion replaces island morph with short crossfade.

## Accessibility

- Category island announces title and current summary, e.g. `Reminders, on, 15 minutes before`.
- Switch rows use one combined target; label and control states are not duplicated by screen readers.
- Sliders have increment/decrement alternatives and exact value text.
- Dynamic color preview meets contrast before allowing save.
- Destructive confirmations name exact data/account target.
- At large font, summaries wrap and trailing icons move below rather than overlap.

## Edge cases

- Settings changed on another device during local edit.
- Exact alarm or battery whitelist becomes unavailable after OS update.
- Dynamic color unsupported or produces insufficient contrast.
- Cloud provider credentials expire.
- Wear device disconnected or multiple watches connected.
- Membership/quota state unavailable offline.
- Import/restore conflicts with unsynced local changes.
- Account session expires inside a settings subpage.
- Developer mode exposes diagnostics without leaking credentials or tokens.
