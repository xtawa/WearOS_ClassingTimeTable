import io
import sys

NL = chr(10)

SCREENS = "mobile/src/main/java/com/classing/mobile/timetable/screen/Screens.kt"
LAYERS = "mobile/src/main/java/com/classing/mobile/timetable/screen/MobileLayersMain.kt"

# The bottom navigation bar carried a hard 82 dp height. It holds an icon above a
# label, so at a large system font scale the label was clipped. A minimum height
# keeps the same resting size while letting the bar grow when it has to.
NAV_OLD = (
    "                NavigationBar(" + NL +
    "                    modifier = Modifier" + NL +
    "                        .fillMaxWidth()" + NL +
    "                        .navigationBarsPadding()" + NL +
    "                        .height(82.dp)," + NL
)
NAV_NEW = (
    "                NavigationBar(" + NL +
    "                    modifier = Modifier" + NL +
    "                        .fillMaxWidth()" + NL +
    "                        .navigationBarsPadding()" + NL +
    "                        .heightIn(min = 82.dp)," + NL
)

# Week grid cell padding was 7 dp horizontally against 6 dp vertically, which is
# off the spacing scale and made the accent block look optically off centre.
CELL_PAD_OLD = "                    .padding(horizontal = 7.dp, vertical = 6.dp)," + NL
CELL_PAD_NEW = "                    .padding(6.dp)," + NL


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


def assert_imports(text, required, path):
    present = set()
    for line in text.split(NL):
        if line.startswith("import "):
            present.add(line[7:].strip())
    missing = [i for i in required if i not in present]
    for m in missing:
        print("MISSING IMPORT in %s: %s" % (path, m))
    return len(missing) == 0


def patch_screens():
    text = io.open(SCREENS, encoding="utf-8").read()
    if ".heightIn(min = 82.dp)" in text:
        print("screens already patched")
        return 0
    if not need_unique(text, NAV_OLD, "navigation bar height"):
        return 1
    text = text.replace(NAV_OLD, NAV_NEW)

    lines = text.split(NL)
    add_import(lines, "androidx.compose.foundation.layout.heightIn")
    text = NL.join(lines)
    if not assert_imports(text, ["androidx.compose.foundation.layout.heightIn"], SCREENS):
        return 1

    if ".height(82.dp)" in text:
        print("STALE FIXED NAV HEIGHT REMAINS")
        return 1

    io.open(SCREENS, "w", encoding="utf-8", newline="").write(text)
    print("patched %s" % SCREENS)
    return 0


def patch_layers():
    text = io.open(LAYERS, encoding="utf-8").read()
    if CELL_PAD_NEW in text:
        print("layers already patched")
        return 0
    if not need_unique(text, CELL_PAD_OLD, "week grid cell padding"):
        return 1
    text = text.replace(CELL_PAD_OLD, CELL_PAD_NEW)

    io.open(LAYERS, "w", encoding="utf-8", newline="").write(text)
    print("patched %s" % LAYERS)
    return 0


def main():
    rc = patch_screens()
    if rc != 0:
        return rc
    return patch_layers()


if __name__ == "__main__":
    sys.exit(main())
