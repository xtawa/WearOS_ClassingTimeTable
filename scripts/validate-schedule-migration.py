from pathlib import Path
import json, sqlite3, re
root = Path(__file__).resolve().parents[1]
schemas = root / "app/schemas/com.classing.wear.timetable.data.local.AppDatabase"
before = json.loads((schemas / "3.json").read_text())["database"]
after = json.loads((schemas / "4.json").read_text())["database"]
db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
for entity in before["entities"]:
    db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for index in entity["indices"]:
        db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
db.execute("INSERT INTO semesters VALUES(1, 'sem', 'Preserved semester', '2026-02-23', '2026-07-01', 19, 1, 1)")
db.execute("INSERT INTO time_slots VALUES(1,'slot',1,1,'09:00', '09:00','10:00',1)")
db.execute("INSERT INTO courses VALUES(1,'course',1,'Preserved class','Teacher','Room','Note','teal',0,1)")
db.execute("INSERT INTO course_sessions VALUES(1,'session',1,1,1,1,1,19,'ALL',1)")
source = (root / "app/src/main/java/com/classing/wear/timetable/data/local/AppDatabaseMigrations.kt").read_text()
migration = re.search(r"MIGRATION_3_4.*?db.execSQL\(\"([^\"]+)\"\)", source, re.S).group(1)
db.execute(migration)
for entity in after["entities"]:
    columns = {row[1]: (row[2], bool(row[3])) for row in db.execute("PRAGMA table_info(" + entity["tableName"] + ")")}
    expected = {field["columnName"]: (field["affinity"], field["notNull"]) for field in entity["fields"]}
    assert columns == expected, (entity["tableName"], columns, expected)
assert db.execute("SELECT remoteId, scheduleRuleJson FROM course_sessions").fetchone() == ("session", None)
assert db.execute("SELECT name FROM courses").fetchone()[0] == "Preserved class"
assert not list(db.execute("PRAGMA foreign_key_check"))
print("PASS: actual migration 3->4 preserves courses/sessions, schema columns/nullability and foreign keys")
