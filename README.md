# Classing Timetable (Phone + Wear OS)

增量更新后，工程拆分为：

- `mobile`：手机端（课程管理、导入、提醒设置、同步主控）
- `app`：Wear OS 端（课表展示、下一节课、轻量交互、同步结果消费）
- `shared`：共享模型与核心业务逻辑（ICS 导入、提醒推算、同步契约）

## 关键增量

- 新增 ICS 导入链路：`ImportParser` / `IcsImportParser` / `ImportResult` / `ParsedSchedulePayload` / `ScheduleImportAdapter`。
- 新增本地提醒链路：`ReminderCalculator` / `ReminderScheduler` / `ReminderRepository` / `ReminderRebuildUseCase`。
- 新增本地提醒恢复机制：Boot/TimeZone 变化广播触发重建。
- 同步层补全：全量/增量、最后同步游标、失败重试、冲突策略抽象。

## 运行

1. Android Studio 打开项目。
2. 选择 `mobile` 运行手机端，选择 `app` 运行 Wear 端。
3. 共享逻辑单测：`./gradlew :shared:test`。

## 真实替换点

- `shared.sync.SyncGateway`：替换为 Data Layer/MessageClient 等真实跨设备实现。
- `app.data.sync.MockSyncDataSource`：替换为真实手机端数据源。
- `app.data.repository.PlaceholderCourseImportRepository`：将导入草稿映射到真实 Room 落库。
