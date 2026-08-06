# Tasks

- [x] Task 1: 扩展 WearQrAuthApiClient 支持 login 与签名头
  - [ ] SubTask 1.1: 为 `WearQrAuthApiClient` 构造函数新增可选 `context: Context?` 参数（与 `WearOfficialCloudHttpClient` 一致），保留默认 `baseUrl`。
  - [ ] SubTask 1.2: 在私有 `request` 中接入 `ClientIntegrity.ensureTrusted` 预检与 `ClientIntegrity.applyHeaders`（release 生效，DEBUG 跳过），覆盖登录与刷新路径。
  - [ ] SubTask 1.3: 新增 `suspend fun login(identifier, password): Result<WearDirectAccountSession>`，POST `/api/v1/auth/login`，body 为 `{identifier, password, consent}`，解析返回 `session` + `account` + `membership` 映射为 `WearDirectAccountSession`（与 QR poll 的 Approved 分支解析逻辑一致）。
  - [ ] SubTask 1.4: 为 `WearOfficialCloudSyncCoordinator` 中 `authApiClient` 默认构造传入 `context`，使刷新路径签名头生效。
- [x] Task 2: 新增登录输入校验
  - [ ] SubTask 2.1: 在 Wear account 包下新增 `WearLoginInputValidation.kt`，提供 `isValidIdentifier(value)`（非空且长度合理）与 `isValidPassword(value)`（≥ 8），参考 Mobile `AccountInputValidation`。
- [x] Task 3: CloudSyncScreen 新增账号登录表单
  - [ ] SubTask 3.1: 在未登录且无 QR 授权进行中的状态下，于「QR sign in」按钮旁新增「账号登录」按钮；点击后展开 identifier / password 输入框与「登录」提交按钮。
  - [ ] SubTask 3.2: 提交时调用 `qrAuthApi.login(...)`，成功则写入 `WearDirectAccountStore`、更新 `directSession`、自动触发 `directCloudSync.sync(TRIGGER_APP_START)`，复用现有同步状态文案。
  - [ ] SubTask 3.3: 失败时根据 `WearQrAuthException` 的 `message`/`errorCode` 展示错误（401 → 账号或密码错误；签名异常 → 签名提示；其他 → 网络错误），保留输入。
  - [ ] SubTask 3.4: 提交按钮根据校验结果启用/禁用，登录中显示 loading 并禁用输入。
- [x] Task 4: 新增三语字符串资源
  - [ ] SubTask 4.1: 在 `values/strings.xml`、`values-zh-rCN/strings.xml`、`values-zh-rTW/strings.xml` 新增账号登录相关字符串（按钮、输入提示、成功/失败文案、密码错误、签名异常等）。
- [x] Task 5: 单元测试
  - [ ] SubTask 5.1: 新增 `WearQrAuthApiClientTest`（或扩展现有测试），使用本地 mock HTTP 验证 login 成功解析 session、401 失败抛出 `WearQrAuthException`。
  - [ ] SubTask 5.2: 新增 `WearLoginInputValidationTest` 验证 identifier / password 校验边界。

# Task Dependencies
- Task 2 依赖 Task 1（校验供 UI 与 API 调用使用，可与 Task 1 并行）。
- Task 3 依赖 Task 1 与 Task 2。
- Task 4 可与 Task 1/2 并行，但 Task 3 引用字符串需 Task 4 完成。
- Task 5 依赖 Task 1 与 Task 2。
