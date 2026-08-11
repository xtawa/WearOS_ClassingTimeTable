"""Merge the mobile week header and the legacy sync status bar into a single top bar.

The mobile app used to draw two stacked headers: the concept header in MainActivity
and the Scaffold top bar in Screens.kt. This script rewrites the Scaffold top bar so
that it carries the concept header design plus the Bluetooth and cloud sync
indicators, and removes the MainActivity header so only one bar is left.
"""
import pathlib
import sys

BASE = pathlib.Path("mobile/src/main/java/com/classing/mobile/timetable")
SCREENS = BASE / "screen" / "Screens.kt"
MAIN_ACTIVITY = BASE / "MainActivity.kt"

NEW_TOP_BAR = '''        topBar = {
            val headerLocale = Locale.getDefault()
            val headerToday = remember(headerLocale) { LocalDate.now() }
            val headerWeekNumber = resolveAnchorWeek(
                anchorDate = headerToday,
                weekNumberMode = weekNumberMode,
                semesterWeekStartDate = semesterWeekStartDate,
            )
            val headerDateLabel = remember(headerToday, headerLocale) {
                headerToday.format(DateTimeFormatter.ofPattern("MM.dd EEE", headerLocale))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .heightIn(min = 76.dp)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(999.dp),
                            ),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = "CLASSING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "WEEK $headerWeekNumber",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    SyncStatusGroup(
                        bluetoothState = bluetoothSyncState,
                        cloudState = cloudSyncState,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = headerDateLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },'''

OLD_APP_BODY = '''        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                MobileConceptHeader()
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    MobileTimetableScreen()
                }
            }
        }
'''

NEW_APP_BODY = '''        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            MobileTimetableScreen()
        }
'''

DEAD_IMPORTS = (
    "import androidx.compose.foundation.layout.heightIn\n",
    "import androidx.compose.material3.HorizontalDivider\n",
    "import java.time.temporal.WeekFields\n",
)


def patch_screens() -> int:
    text = SCREENS.read_text(encoding="utf-8")
    if "ic_launcher_foreground" not in text:
        print("Screens.kt: top bar already merged.")
        return 0
    lines = text.split("\n")
    start, end = 1516, 1563  # zero based: '        topBar = {' .. '        },'
    if lines[start] != "        topBar = {":
        print("Screens.kt: unexpected line %d: %r" % (start + 1, lines[start]))
        return 1
    if lines[end] != "        },":
        print("Screens.kt: unexpected line %d: %r" % (end + 1, lines[end]))
        return 1
    block = "\n".join(lines[start:end + 1])
    if "ic_launcher_foreground" not in block or "SyncStatusGroup(" not in block:
        print("Screens.kt: top bar block does not look like the legacy header.")
        return 1
    lines[start:end + 1] = NEW_TOP_BAR.split("\n")
    SCREENS.write_text("\n".join(lines), encoding="utf-8")
    print("Screens.kt: merged the top bar.")
    return 0


def patch_main_activity() -> int:
    text = MAIN_ACTIVITY.read_text(encoding="utf-8")
    if "MobileConceptHeader" not in text:
        print("MainActivity.kt: header already removed.")
        return 0
    if text.count(OLD_APP_BODY) != 1:
        print("MainActivity.kt: MobileApp body anchor is not unique.")
        return 1
    text = text.replace(OLD_APP_BODY, NEW_APP_BODY)

    marker = "@Composable\nprivate fun MobileConceptHeader() {"
    tail = "\nprivate fun classingDarkColorScheme(): ColorScheme {"
    if marker not in text or tail not in text:
        print("MainActivity.kt: header function anchors not found.")
        return 1
    start = text.index(marker)
    end = text.index(tail, start)
    text = text[:start] + text[end + 1:]
    if "MobileConceptHeader" in text:
        print("MainActivity.kt: header function still present.")
        return 1

    for dead in DEAD_IMPORTS:
        if text.count(dead) != 1:
            print("MainActivity.kt: import anchor missing: %r" % dead)
            return 1
        simple = dead.strip().split(".")[-1]
        body = "\n".join(
            l for l in text.split("\n") if not l.startswith("import ")
        )
        if simple in body:
            print("MainActivity.kt: %s is still referenced; keeping import." % simple)
            continue
        text = text.replace(dead, "")

    MAIN_ACTIVITY.write_text(text, encoding="utf-8")
    print("MainActivity.kt: removed the duplicate header.")
    return 0


def main() -> int:
    return patch_screens() or patch_main_activity()


if __name__ == "__main__":
    sys.exit(main())
