# Wear 端账号密码手动登录 Spec

## Why
当前 Wear 端（`app` 模块）仅支持「手机扫描 Wear 二维码」授权登录（`WearQrAuthApiClient` 的 start/poll 流程）。当用户身边没有已登录的手机、或手机端无法扫码时，手表无法独立完成登录，官方云同步与独立模式均不可用。需要在 Wear 端新增「手动输入账号 + 密码」的登录入口，与现有 QR 登录并存，让手表可以脱离手机独立完成账号登录。

## What Changes
- 在 Wear 账号 API 客户端中新增 `login(identifier, password)` 方法，调用后端 `POST /api/v1/auth/login`，复用与 Mobile 端 `AccountApiClient.login` 一致的请求契约（identifier + password + consent）。
- 为 Wear 账号请求接入客户端签名校验（`ClientIntegrity`）：登录、刷新属于后端签名保护路由，release 构建必须完成 `POST /api/v1/client/signature/check` 预检并注入 `X-Classing-Client-Platform: ANDROID_WEAR` 等签名头，与 `WearOfficialCloudHttpClient` 现有做法对齐。
- 登录成功后会话写入现有 `WearDirectAccountStore`（加密存储），并复用 `WearDirectAccountSessionManager` 做后续刷新与失效处理，与 QR 登录产物完全一致。
- 在 `CloudSyncScreen` 未登录态下新增「账号登录」入口，提供 identifier / password 输入表单与提交、错误反馈；登录成功后自动触发一次官方云同步（与 QR 登录一致）。
- 新增输入校验（identifier 非空、密码长度 ≥ 8），参考 Mobile 端 `AccountInputValidation` 规则。
- 新增 en / zh-rCN / zh-rTW 三语字符串资源。

## Impact
- Affected specs: 账号认证、Wear 云同步登录入口。
- Affected code:
  - `app/src/main/java/com/classing/wear/timetable/account/WearQrAuth.kt`（新增 login、接入签名头与 context）
  - `app/src/main/java/com/classing/wear/timetable/ui/screen/settings/CloudSyncScreen.kt`（新增账号登录表单入口）
  - `app/src/main/java/com/classing/wear/timetable/sync/WearOfficialCloudSyncCoordinator.kt`（构造 `WearQrAuthApiClient` 时传入 context，使签名头生效）
  - `app/src/main/res/values/strings.xml`、`values-zh-rCN/strings.xml`、`values-zh-rTW/strings.xml`（新增字符串）
- 参考但不修改：Mobile 端 `AccountApiClient`、`AccountInputValidation`、`AccountSessionManager`；ideashell「Classing API 文档」笔记中的登录/签名契约。

## ADDED Requirements

### Requirement: Wear 端账号密码登录
系统 SHALL 在 Wear 端提供通过账号标识符（用户名或邮箱）与密码手动登录的能力，调用后端 `POST /api/v1/auth/login`，并在登录成功后获得与 QR 登录等价的会话。

#### Scenario: 登录成功
- **WHEN** 用户在 Wear 云同步页未登录态下选择「账号登录」，输入合法 identifier 与密码并提交
- **THEN** 客户端向 `/api/v1/auth/login` 发送 `{identifier, password, consent}` 请求（release 构建先完成签名预检并注入 `ANDROID_WEAR` 签名头）
- **AND** 返回 `session` 含 `accessToken / refreshToken / accessExpiresAt / refreshExpiresAt`
- **AND** 会话写入 `WearDirectAccountStore`，UI 进入已登录态
- **AND** 自动触发一次官方云同步，成功后展示「登录成功并已同步」

#### Scenario: 登录失败凭据错误
- **WHEN** 后端返回 401（`AUTH_INVALID_CREDENTIALS` 或同类错误码）
- **THEN** 不写入任何会话，UI 展示后端 `message`（或默认「账号或密码错误」），保留输入以便重试

#### Scenario: 签名校验失败
- **WHEN** release 构建签名预检 `POST /api/v1/client/signature/check` 返回非 2xx
- **THEN** 登录请求不发出，UI 展示签名异常提示，禁止在线功能

#### Scenario: 登录后刷新与登出
- **WHEN** 手动登录产生的会话 access token 过期
- **THEN** `WearDirectAccountSessionManager` 通过 `POST /api/v1/auth/refresh` 轮换 token，逻辑与 QR 登录会话完全一致
- **AND** 用户点击「退出手表账号」时，调用 `POST /api/v1/auth/logout` 并清空本地会话，与 QR 登录登出一致

### Requirement: 登录输入校验
系统 SHALL 在提交前校验 identifier 非空、密码长度 ≥ 8，校验不通过时禁用提交按钮并提示原因。

## MODIFIED Requirements

### Requirement: Wear 账号 API 客户端签名
`WearQrAuthApiClient` 的登录与刷新请求 SHALL 在 release 构建下完成 `ClientIntegrity.ensureTrusted` 预检并通过 `ClientIntegrity.applyHeaders` 注入 `ANDROID_WEAR` 平台签名头；DEBUG 构建跳过签名校验（与现有 `WearOfficialCloudHttpClient` 行为一致）。登出沿用现有实现（后端未强制签名）。
