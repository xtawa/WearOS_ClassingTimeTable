import io
import sys

NL = chr(10)
PATH = "mobile/src/main/java/com/classing/mobile/timetable/screen/MobileLayersMain.kt"

REQUIRED_IMPORTS = [
    "androidx.compose.foundation.layout.BoxWithConstraints",
    "androidx.compose.ui.unit.Dp",
]

CONST_OLD = (
    "private val WeekGridGutterWidth = 54.dp" + NL +
    "private val WeekGridColumnWidth = 96.dp" + NL
)
CONST_NEW = (
    "private val WeekGridGutterWidth = 54.dp" + NL +
    "private val WeekGridMinColumnWidth = 64.dp" + NL
)

COMMENT_OLD = (
    "// Grid metrics for the weekly board. The row height fits a two-line course name plus a" + NL +
    "// classroom line, and the column width keeps five to six weekdays reachable with one swipe." + NL
)
COMMENT_NEW = (
    "// Grid metrics for the weekly board. The row height fits a two-line course name plus a" + NL +
    "// classroom line. Day columns are measured at layout time, so the value below is only the" + NL +
    "// point where a column stops being readable and the board falls back to scrolling." + NL
)

ROW_ANCHOR = (
    "        Row(" + NL +
    "            modifier = Modifier" + NL +
    "                .fillMaxWidth()" + NL +
    "                .horizontalScroll(horizontalScrollState)" + NL +
    "                .padding(vertical = 8.dp)," + NL +
    "        ) {" + NL
)

WRAP_HEAD = (
    "        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {" + NL +
    "            // Day columns stretch to fill the width that is actually available, so a wide" + NL +
    "            // screen no longer leaves dead space beside a grid frozen at 96 dp per day." + NL +
    "            // Horizontal scrolling only engages when the visible days genuinely cannot fit" + NL +
    "            // at the minimum readable column width." + NL +
    "            val fittedColumnWidth = if (visibleDays.isEmpty()) {" + NL +
    "                WeekGridMinColumnWidth" + NL +
    "            } else {" + NL +
    "                (maxWidth - WeekGridGutterWidth) / visibleDays.size" + NL +
    "            }" + NL +
    "            val needsHorizontalScroll = fittedColumnWidth < WeekGridMinColumnWidth" + NL +
    "            val columnWidth = if (needsHorizontalScroll) {" + NL +
    "                WeekGridMinColumnWidth" + NL +
    "            } else {" + NL +
    "                fittedColumnWidth" + NL +
    "            }" + NL
)

# Anchors below are written at the indentation the file uses BEFORE the block is
# shifted one level deeper. The shift happens last so every anchor stays literal.
SCROLL_OLD = "                .horizontalScroll(horizontalScrollState)" + NL
SCROLL_NEW = (
    "                .then(" + NL +
    "                    if (needsHorizontalScroll) {" + NL +
    "                        Modifier.horizontalScroll(horizontalScrollState)" + NL +
    "                    } else {" + NL +
    "                        Modifier" + NL +
    "                    }," + NL +
    "                )" + NL
)

CELL_CALL_OLD = (
    "                        WeekGridCell(" + NL +
    "                            lessons = matches," + NL +
    "                            onLongPressLesson = onLongPressLesson," + NL +
    "                        )" + NL
)
CELL_CALL_NEW = (
    "                        WeekGridCell(" + NL +
    "                            lessons = matches," + NL +
    "                            columnWidth = columnWidth," + NL +
    "                            onLongPressLesson = onLongPressLesson," + NL +
    "                        )" + NL
)

CELL_SIG_OLD = (
    "private fun WeekGridCell(" + NL +
    "    lessons: List<LessonUi>," + NL +
    "    onLongPressLesson: (LessonUi) -> Unit," + NL +
    ") {" + NL
)
CELL_SIG_NEW = (
    "private fun WeekGridCell(" + NL +
    "    lessons: List<LessonUi>," + NL +
    "    columnWidth: Dp," + NL +
    "    onLongPressLesson: (LessonUi) -> Unit," + NL +
    ") {" + NL
)

CELL_BOX_OLD = (
    "            .size(width = WeekGridColumnWidth, height = WeekGridRowHeight)" + NL +
    "            .padding(horizontal = 3.dp, vertical = 3.dp)," + NL
)
CELL_BOX_NEW = (
    "            .size(width = columnWidth, height = WeekGridRowHeight)" + NL +
    "            .padding(4.dp)," + NL
)


def need_unique(text, anchor, label):
    n = text.count(anchor)
    if n != 1:
        print("ANCHOR NOT UNIQUE for %s: found %d" % (label, n))
        return False
    return True


def add_import(lines, imp):
    full = "import " + imp
    if full in lines:
        return
    idxs = [i for i, l in enumerate(lines) if l.startswith("import ")]
    if not idxs:
        print("NO IMPORT BLOCK FOUND")
        raise SystemExit(1)
    pos = idxs[-1] + 1
    for i in idxs:
        if lines[i] > full:
            pos = i
            break
    lines.insert(pos, full)


def main():
    text = io.open(PATH, encoding="utf-8").read()

    if "WeekGridMinColumnWidth" in text:
        print("already adaptive; nothing to do.")
        return 0

    for anchor, label in (
        (CONST_OLD, "grid constants"),
        (ROW_ANCHOR, "grid row"),
        (CELL_SIG_OLD, "WeekGridCell signature"),
        (CELL_BOX_OLD, "WeekGridCell box"),
    ):
        if not need_unique(text, anchor, label):
            return 1

    if not need_unique(text, COMMENT_OLD, "grid metrics comment"):
        return 1
    text = text.replace(COMMENT_OLD, COMMENT_NEW)
    text = text.replace(CONST_OLD, CONST_NEW)

    start = text.find(ROW_ANCHOR)
    brace_open = text.index("{", text.index("        ) {", start))
    depth = 0
    k = brace_open
    while k < len(text):
        c = text[k]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                break
        k += 1
    if depth != 0:
        print("UNBALANCED BRACES while scanning the grid row")
        return 1
    end = k
    block = text[start:end + 1]
    if not block.endswith(NL + "        }"):
        print("BLOCK DOES NOT END AT THE EXPECTED CLOSING BRACE")
        return 1

    for anchor, label in (
        (SCROLL_OLD, "horizontalScroll modifier"),
        (CELL_CALL_OLD, "WeekGridCell call"),
    ):
        if not need_unique(block, anchor, label):
            return 1
    if block.count(".width(WeekGridColumnWidth)") != 1:
        print("UNEXPECTED .width(WeekGridColumnWidth) COUNT: %d" % block.count(".width(WeekGridColumnWidth)"))
        return 1
    if block.count("width = WeekGridColumnWidth, height = WeekGridHeaderHeight") != 1:
        print("UNEXPECTED HEADER CELL WIDTH COUNT")
        return 1

    block = block.replace(SCROLL_OLD, SCROLL_NEW)
    block = block.replace(CELL_CALL_OLD, CELL_CALL_NEW)
    block = block.replace(".width(WeekGridColumnWidth)", ".width(columnWidth)")
    block = block.replace(
        "width = WeekGridColumnWidth, height = WeekGridHeaderHeight",
        "width = columnWidth, height = WeekGridHeaderHeight",
    )

    shifted = []
    for line in block.split(NL):
        shifted.append(("    " + line) if line.strip() else line)
    block = NL.join(shifted)

    text = text[:start] + WRAP_HEAD + block + NL + "        }" + text[end + 1:]

    text = text.replace(CELL_SIG_OLD, CELL_SIG_NEW)
    text = text.replace(CELL_BOX_OLD, CELL_BOX_NEW)

    if "WeekGridColumnWidth" in text:
        print("STALE WeekGridColumnWidth REFERENCE REMAINS")
        return 1

    lines = text.split(NL)
    for imp in REQUIRED_IMPORTS:
        add_import(lines, imp)
    text = NL.join(lines)

    present = set()
    for line in text.split(NL):
        if line.startswith("import "):
            present.add(line[7:].strip())
    missing = [i for i in REQUIRED_IMPORTS if i not in present]
    if missing:
        for m in missing:
            print("MISSING IMPORT: %s" % m)
        return 1

    io.open(PATH, "w", encoding="utf-8", newline="").write(text)
    print("patched %s" % PATH)
    return 0


if __name__ == "__main__":
    sys.exit(main())
