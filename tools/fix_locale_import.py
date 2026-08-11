"""Add the missing java.util.Locale import to Screens.kt.

The merged mobile top bar formats the header date with an explicit locale, but
Screens.kt never imported java.util.Locale, so the module failed to compile.
"""
import pathlib
import sys

SCREENS = pathlib.Path(
    "mobile/src/main/java/com/classing/mobile/timetable/screen/Screens.kt"
)
ANCHOR = "import java.time.format.DateTimeFormatter\n"
IMPORT = "import java.util.Locale\n"


def main() -> int:
    text = SCREENS.read_text(encoding="utf-8")
    if IMPORT in text:
        print("Screens.kt: Locale import already present.")
        return 0
    if text.count(ANCHOR) != 1:
        print("Screens.kt: import anchor is not unique.")
        return 1
    SCREENS.write_text(text.replace(ANCHOR, ANCHOR + IMPORT), encoding="utf-8")
    print("Screens.kt: added the Locale import.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
