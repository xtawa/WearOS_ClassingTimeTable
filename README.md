# Classing Timetable for Wear OS

A production-style Wear OS timetable app built with Kotlin + Jetpack Compose.

## Highlights
- Today lessons, next lesson card, week view, course detail, search.
- Room local cache + DataStore preferences.
- Pluggable sync architecture (`SyncDataSource`, `PhoneSyncManager`, `SyncRepository`).
- Mock phone sync implemented for first-run demo.
- Supports semester/week/parity/exception scheduling model.
- Background sync worker and reminder worker placeholders.
- Tile/Complication contracts reserved for future integration.

## Run
1. Open this folder in Android Studio.
2. Let Gradle sync and download dependencies.
3. Run the `app` module on Wear OS emulator/device.

## Replace Points
- Real phone sync: replace `MockSyncDataSource` with Data Layer/MessageClient based implementation.
- Tile: implement `TileDataProvider` + Wear Tiles service.
- Complication: implement `ComplicationDataProvider` + DataSource service.
- Import parser: implement `CourseImportRepository`.
