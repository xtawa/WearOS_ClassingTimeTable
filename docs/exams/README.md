# Exam / Schedule

2026-10-11：手机与 Wear OS 的考试适配。OPPO 版本及其专有同步通道按用户最新要求暂不开发；没有添加 `oppo` 模块。

## 已实现的行为

- 首页胶囊式「课表 / 考试」切换，分别保留设备本地的选择。手机添加、编辑、删除考试，支持名称、起止日期与时间、时区、地点、备注、提前提醒、完成状态。日期和时间使用系统选择器；删除先确认，取消编辑不写入。
- Wear 显示同步的考试列表、进行中/已结束状态及倒计时，支持触摸和表冠滚动、离线缓存。考试在手机编辑；Wear 的 AskAI 可以查询考试。
- 手机 AskAI 每次请求包含当前考试数据，可提出添加/修改/删除草稿。展示草稿后由用户确认，确认前检查当前数据指纹；过期草稿和无效批次不写入。Wear 每次查询也附带最新快照，不能声称已保存考试修改。推荐问题只在有有效考试数据时使用考试信息。
- 备份包含考试；旧备份缺少 `exams` 时保留现有考试，显式空数组会清除考试。只含考试、没有课程的备份也可预览恢复。
- 复用 Cloud Sync V2 的逻辑版本和删除记录，新增 `timetable.exams` 域。手机 Data Layer 的 FULL/DELTA 包都携带完整考试数组。旧包缺少考试字段时保留考试，空数组清除。Wear 课程与考试在同一 Room 事务内应用；只有考试的云文档不会覆盖现有课程。
- Wear Room 4 → 5 新增考试表，保留旧表和记录；账户切换时清除上一账户的考试。
- 手机 Widget、Wear Tile 和表盘组件随本设备所选模式显示下一课程或考试，并保留现有显示选项。
- 考试提醒遵守全局提醒开关，每次触发前校验考试仍存在、时间仍匹配且未完成；同一考试时间只提醒一次。一次安排一个最近提醒，避免大批考试耗尽系统闹钟数量。未获精确闹钟权限时使用系统非精确闹钟，提醒可能延迟；未获通知权限时不安排通知闹钟。
- 考试保存 UTC 毫秒及原始时区，旅行不改变考试发生时间；编辑地点等字段不会改变重复夏令时中的第二次时间，也不会丢失毫秒。新建时遇到不存在的夏令时时间会拒绝保存；重复时间默认第一次出现。

## 查看与本地验证

[交互预览](preview.html) · [浏览器预览截图](exam-preview.png) · [最新检查记录](verification-summary.json) · [实机验收表](acceptance.csv)

预览为合成数据 HTML，截图是浏览器原型，不能作为 Android 真机显示证明。实际 Android UI 使用 Compose 和系统日期/时间选择器。

```powershell
# 不启动模拟器
python scripts/validate-exam-migration.py
node scripts/validate-exam-preview.cjs
```

完整 Gradle 检查覆盖 shared、手机 CN/Global、Wear CN/Global 的单元测试、lint 和发布包；具体结果与包的 SHA-256 以检查记录为准。后端执行 `go test ./...`、`go vet ./...`、Windows 构建及 Linux amd64 交叉编译。交叉编译不等于执行 Linux 测试。

## 待条件具备后的验收

当前没有连接手机/手表、Play 测试环境或真实 LMS 测试账户。不会把本地测试等同于购买、真实 AI 生成、LMS 或实机验收。

1. 手机在没有任何课程时添加一场考试，切换、重启、编辑、取消、删除并检查离线缓存；跨午夜结束时间正常，结束早于开始时拒绝保存。
2. 在夏令时地区测试不存在时间和重复时间；改设备时区后，考试原始时间和时区应保持准确。
3. 手机与 Wear 使用相同市场及签名的包，测试首次 FULL、之后 DELTA、离线后重连；只有接收端成功应用的 ACK 才算完成。测试删除最后一场考试及旧客户端没有考试字段的情况。
4. 云同步测试考试新增/更新/删除、逻辑版本冲突、恢复删除记录、考试单独文档、账户切换；不得因缺少域而清除无关课程。
5. 修改时间、完成、删除考试及关闭全局提醒后，旧闹钟不能继续提醒；重复广播不能重复通知。另测准点提醒、权限拒绝/恢复、重启、调时间和省电模式。
6. 检查 Widget、Tile、表盘组件的切换、倒计时、长标题、地点、无考试、进行中和过期状态；圆屏边缘、表冠、TalkBack 与大字体需要实机检查。
7. 部署本次后端代码后，连接真实 AskAI 服务测试考试查询和草稿确认：无课程但有考试、继续旧对话时更新考试、数据变化后拒绝旧草稿、无效第二条操作不得部分保存。后端代码本次未部署。

## 安装包

- [手机 CN](../../mobile/build/outputs/apk/cn/release/mobile-cn-release.apk)
- [Wear CN](../../app/build/outputs/apk/cn/release/app-cn-release.apk)
- [手机 Global](../../mobile/build/outputs/apk/global/release/mobile-global-release.apk)
- [Wear Global](../../app/build/outputs/apk/global/release/app-global-release.apk)

签名校验只证明本地产物签名有效，不能证明 Play App Signing、购买或实机已验收。原有 Windows FileProvider 路径测试仍由 Linux CI 执行。

Todoist 的 Classing 项目已勾选 8 项经核实完成的任务，具体对应见 [追踪表](../global-adaptation/traceability.md)；依赖外部环境或用户测试的任务保持未完成。
