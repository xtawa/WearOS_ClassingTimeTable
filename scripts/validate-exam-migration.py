"""Check the real Room 4 -> 5 SQL against exported schemas, without an emulator."""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
schema_dir = root / "app/schemas/com.classing.wear.timetable.data.local.AppDatabase"
old = json.loads((schema_dir / "4.json").read_text())['database']
new = json.loads((schema_dir / "5.json").read_text())['database']
source = (root / "app/src/main/java/com/classing/wear/timetable/data/local/AppDatabaseMigrations.kt").read_text(encoding="utf-8")
section = source.split("val MIGRATION_4_5:", 1)[1].split("val MIGRATION_3_4:", 1)[0]
statements = re.findall(r'db\.execSQL\("([^"\n]+)"\)', section)
assert statements, "Migration SQL missing"
db = sqlite3.connect(":memory:")
for entity in old['entities']:
    db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    for index in entity.get('indices', []):
        db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
before = {entity['tableName']: list(db.execute(f"PRAGMA table_info('{entity['tableName']}')")) for entity in old['entities']}
db.execute("INSERT INTO semesters(localId, remoteId, name, startDate, endDate, totalWeeks, isActive, version) VALUES (1, 'retained', 'Retained', '2026-10-01', '2027-07-01', 40, 1, 1)")
for sql in statements:
    db.execute(sql)
for name, columns in before.items():
    assert list(db.execute(f"PRAGMA table_info('{name}')")) == columns, f"Existing schema changed: {name}"
assert db.execute("SELECT remoteId FROM semesters WHERE localId=1").fetchone() == ('retained',)
exam_entity = next(entity for entity in new['entities'] if entity['tableName'] == 'exams')
reference = sqlite3.connect(":memory:")
reference.execute(exam_entity['createSql'].replace('${TABLE_NAME}', 'exams'))
assert list(db.execute("PRAGMA table_info('exams')")) == list(reference.execute("PRAGMA table_info('exams')"))
db.execute("INSERT INTO exams(id,payload) VALUES ('e1','{}')")
try:
    db.execute("INSERT INTO exams(id,payload) VALUES ('e1','{}')")
    raise AssertionError("Exam primary key did not reject duplicate IDs")
except sqlite3.IntegrityError:
    pass
print("PASS: Room 4 -> 5 preserves existing tables and rows; exam schema matches Room export; duplicate IDs rejected")
