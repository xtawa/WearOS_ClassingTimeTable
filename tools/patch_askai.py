"""Align the Ask AI page with the shared secondary header and card language."""
import pathlib
import sys

P = pathlib.Path("mobile/src/main/java/com/classing/mobile/timetable/screen/AskAiSettingsPage.kt")

REQUIRED_IMPORTS = [
    "androidx.compose.foundation.layout.Arrangement",
    "androidx.compose.foundation.layout.Column",
    "androidx.compose.foundation.layout.PaddingValues",
    "androidx.compose.foundation.layout.Row",
    "androidx.compose.foundation.layout.fillMaxSize",
    "androidx.compose.foundation.layout.fillMaxWidth",
    "androidx.compose.foundation.layout.padding",
    "androidx.compose.foundation.rememberScrollState",
    "androidx.compose.foundation.verticalScroll",
]

IMPORT_OLD = '''import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
'''

IMPORT_NEW = '''import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
'''

HEADER_OLD = '''        TextButton(onClick = onBack) { Text("\\u2190 \\u8fd4\\u56de") }
        Text("Ask AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
'''

HEADER_NEW = '''        SecondaryPageHeader(
            title = "Ask AI",
            onBack = onBack,
            backLabel = "\\u8fd4\\u56de",
            modifier = Modifier.fillMaxWidth(),
        )
'''

CARD_MEMBER_OLD = '''                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
'''

CARD_MEMBER_NEW = '''                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
'''

CARD_MODEL_OLD = '''                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
'''

CARD_MODEL_NEW = '''                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
'''

CARD_HISTORY_OLD = '''                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
'''

CARD_HISTORY_NEW = '''                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
'''

CARD_MESSAGE_OLD = '''                    Card(colors = CardDefaults.cardColors(containerColor = if (item.role == "USER") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
'''

CARD_MESSAGE_NEW = '''                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = if (item.role == "USER") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
'''

CARD_ACCESS_OLD = '''    Card { Column(Modifier.padding(16.dp)'''

CARD_ACCESS_NEW = '''    Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)'''

EDITS = [
    ("RoundedCornerShape import", IMPORT_OLD, IMPORT_NEW),
    ("shared secondary header", HEADER_OLD, HEADER_NEW),
    ("membership notice card", CARD_MEMBER_OLD, CARD_MEMBER_NEW),
    ("model picker card", CARD_MODEL_OLD, CARD_MODEL_NEW),
    ("conversation history card", CARD_HISTORY_OLD, CARD_HISTORY_NEW),
    ("message bubble card", CARD_MESSAGE_OLD, CARD_MESSAGE_NEW),
    ("access card", CARD_ACCESS_OLD, CARD_ACCESS_NEW),
]


def main() -> int:
    text = P.read_text(encoding="utf-8")

    missing = [i for i in REQUIRED_IMPORTS if ("import " + i + "\\n") not in text]
    if missing:
        for i in missing:
            print("MISSING IMPORT: " + i)
        return 1
    print("all %d required imports present" % len(REQUIRED_IMPORTS))

    if "SecondaryPageHeader(" in text:
        print("already aligned; nothing to do.")
        return 0

    for name, old, new in EDITS:
        n = text.count(old)
        if n != 1:
            print("ANCHOR NOT UNIQUE for %s: found %d" % (name, n))
            return 1
        text = text.replace(old, new)
        print("applied: " + name)

    P.write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
