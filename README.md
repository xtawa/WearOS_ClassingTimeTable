# WearOS Classing TimeTable

一个基于 **Material Design 3 + Jetpack Compose** 的课程表示例工程，包含：

- 手机端课程编辑与一键同步到手表
- WearOS 主应用展示课程列表
- WearOS Tile 磁贴显示“下节课”
- WearOS 小组件（Glance AppWidget）展示关键课程信息

## 模块说明

- `mobile`：手机端应用，负责课程维护与 Data Layer 同步
- `wear`：手表端应用，含主界面、Tile、Widget、DataListener
- `shared`：共享课程模型与序列化编解码

## 关键能力

1. **手机-手表同步**
   - 手机端 `WearSyncClient` 通过 `DataClient.putDataItem` 推送课程数据。
   - 手表端 `WearDataListenerService` 监听 `/courses/sync` 并写入本地存储。

2. **Tile 磁贴**
   - `ScheduleTileService` 从本地读取课程并展示“下节课”。

3. **小组件**
   - `ScheduleAppWidget` 使用 Glance 渲染课程卡片式信息。

4. **Material Design 3**
   - 手机端与手表端均使用 Compose + Material3 组件。

## 运行方式

1. 使用 Android Studio 打开项目。
2. 同步 Gradle。
3. 分别运行 `mobile` 到手机/模拟器、`wear` 到 WearOS 设备/模拟器。
4. 在手机端点击“同步到手表”，查看手表应用、Tile 与 Widget。

> 备注：本仓库是最小可扩展版本，可继续接入 Room、WorkManager、账号体系、课表导入等能力。
