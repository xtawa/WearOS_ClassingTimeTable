# API：账户、认证、密码重置

> 文档基准：WearOS_ClassingTimeTable `main`，包含已合并 PR #15。  
> Android release 客户端签名契约见 [API-客户端签名校验与证书注册.md](./API-客户端签名校验与证书注册.md)。

## 1. 基础约束
- Base URL：`https://api-classing.underflo.ink`
- 所有响应建议统一：
  - `code`
  - `message`
  - `data`
  - `requestId`
- 所有时间统一使用 Unix epoch milliseconds。
- Android Mobile/Wear release 构建执行在线账户操作前必须完成客户端签名预检。
- 当前 Android Mobile 仅对 `GET /api/v1/auth/registration/config` 跳过主动预检；其他 `AccountApiClient` 请求会先执行签名预检并注入签名头。

## 2. Android release 客户端签名要求

预检：

```http
POST /api/v1/client/signature/check
```

后续账户请求头：

```http
X-Classing-Client-Platform: ANDROID_MOBILE
X-Classing-Package-Name: com.xtawa.classingtime
X-Classing-Version-Code: <positive-integer>
X-Classing-Signing-Cert-Sha256: <64-hex-certificate-fingerprint>
```

Wear 独立会话使用：

```http
X-Classing-Client-Platform: ANDROID_WEAR
```

后端签名保护路由包括注册、登录、刷新、设备二维码授权、账户资料、邮箱确认、密码修改、账号注销、会员状态与兑换。密码重置申请、密码重置确认、登出当前未由后端签名中间件强制，但 PR #15 后的 Android `AccountApiClient` 仍会主动预检并发送签名头。

签名失败时禁止继续在线账户操作，不得改为无签名重试。

## 3. 注册

直注册接口 `POST /api/v1/auth/register` 已停用，服务端返回 `AUTH_EMAIL_VERIFICATION_REQUIRED`。客户端必须使用邮箱验证注册流程：

- `GET /api/v1/auth/registration/config`
- `POST /api/v1/auth/register/email/request`
- `POST /api/v1/auth/register/email/confirm`

注册申请与确认请求均必须携带 `consent`：

```json
{
  "privacyPolicy": true,
  "termsOfService": true,
  "crossBorderTransfer": true,
  "acceptedAt": 1783698400000,
  "client": "android-mobile"
}
```

约束：
- `username` 全局唯一。
- `email` 全局唯一。
- 后端必须记录邮箱验证状态字段，未验证用户不能登录或取得 token。
- Android release 的注册申请与确认需要签名头；注册配置读取当前不需要签名预检。

## 4. 注册安全配置

### `GET /api/v1/auth/registration/config`

响应包含：

```json
{
  "turnstileRequired": false,
  "turnstileSiteKey": "",
  "legalAgreementUrls": {
    "privacyPolicy": "https://...",
    "termsOfService": "https://...",
    "crossBorderTransfer": "https://..."
  }
}
```

客户端规则：
- 协议 URL 未完整下发时，不允许提交登录或注册验证流程。
- 本接口是当前 Android `AccountApiClient` 唯一不主动执行签名预检的账户接口。

## 5. 登录

### `POST /api/v1/auth/login`
请求体：
```json
{
  "identifier": "alice@example.com",
  "password": "plain-or-prehashed",
  "consent": {
    "privacyPolicy": true,
    "termsOfService": true,
    "crossBorderTransfer": true,
    "acceptedAt": 1783698400000,
    "client": "android-mobile"
  }
}
```

说明：
- `identifier` 可为邮箱或用户名。
- 登录和注册页面必须先通过 `GET /api/v1/auth/registration/config` 获取 `legalAgreementUrls`。
- 未勾选三项协议或协议 URL 未成功下发时，不得提交登录、发送注册验证码、确认注册验证码或重发验证码。
- 服务端返回 `AUTH_CONSENT_REQUIRED` 时，客户端展示协议确认错误并保持在当前页面。
- Android release 登录前必须通过签名预检，登录请求携带四个签名头。

成功响应中的 `session`：

```json
{
  "session": {
    "accessToken": "opaque-access-token",
    "refreshToken": "opaque-refresh-token",
    "accessExpiresAt": 1783698400000,
    "refreshExpiresAt": 1786290400000
  }
}
```

## 6. 刷新 Token

### `POST /api/v1/auth/refresh`
请求体：
```json
{
  "refreshToken": "opaque-refresh-token"
}
```

说明：
- 后端在刷新成功后一次性轮换 refresh token，客户端必须保存响应中的新 token。
- 同一 refresh token、IP 与 User-Agent 在 5 秒内的并发请求会重放完全相同的 replacement session；客户端仍必须用 single-flight 串行化刷新。
- 仅在服务端明确返回 `401 AUTH_REFRESH_REVOKED` 且本地仍保存本次尝试的旧 token 时清除会话。
- 网络错误、超时与 5xx 不应清除凭据。
- Android release 刷新请求需要签名预检与签名头。

## 7. 登出

### `POST /api/v1/auth/logout`
请求头：
- `Authorization: Bearer <accessToken>`
- Android release 客户端会携带签名头。

请求体：
```json
{
  "refreshToken": "opaque-refresh-token"
}
```

说明：
- 后端需撤销 refresh token。
- 若使用黑名单方案，可同时短时拉黑 access token。
- 当前后端签名中间件未强制保护登出接口，但 Android PR #15 客户端仍会先预检。

## 8. 当前用户资料

### `GET /api/v1/account/me`
请求头：
- `Authorization: Bearer <accessToken>`
- Android release 客户端四个签名头

响应体：
```json
{
  "account": {
    "userId": "u_123",
    "identifier": "alice@example.com",
    "username": "alice",
    "email": "alice@example.com"
  }
}
```

存在待确认邮箱变更时可同时返回：

```json
{
  "pendingEmailChange": {
    "newEmail": "alice.new@example.com",
    "expiresAt": 1783698400000
  }
}
```

邮箱变更完整契约见 [API-邮箱变更安全流程.md](./API-邮箱变更安全流程.md)。

## 9. 密码重置申请

### `POST /api/v1/auth/password/reset/request`
请求体：
```json
{
  "email": "alice@example.com"
}
```

规则：
- 永远返回泛化成功文案，避免枚举邮箱存在性。
- 后端生成一次性 reset token，写入重置表。
- token 必须带：
  - `userId`
  - `email`
  - `expiresAt`
  - `usedAt`
  - `requestIp`
  - `requestUa`
- 当前后端签名中间件未强制保护本接口；Android release 客户端仍会主动预检并携带签名头。

## 10. 密码重置确认

### `POST /api/v1/auth/password/reset/confirm`
请求体：
```json
{
  "token": "reset-token",
  "newPassword": "new-password"
}
```

规则：
- token 只能使用一次。
- 成功后必须：
  - 更新密码哈希
  - 标记 token 已使用
  - 撤销该用户全部 refresh token
  - 记录审计日志
- 当前后端签名中间件未强制保护本接口；Android release 客户端仍会主动预检并携带签名头。

## 11. 修改当前密码

### `PUT /api/v1/account/password`

请求头：
- `Authorization: Bearer <accessToken>`
- Android release 客户端四个签名头

请求体应包含当前密码与新密码，字段以服务端实现为准。该接口受敏感接口 IP 与账户双维度限流保护。

## 12. 会员状态

### `GET /api/v1/membership/status`

客户端登录后应单独请求会员状态，不应把 `GET /account/me` 的本地组合结果当作永久会员授权。

PR #15 后，会员限定官方云课表同步必须在同步执行时实时调用本接口；本地持久化的 `isMember` 仅用于界面占位和离线展示。

完整契约见 [API-兑换码与会员状态.md](./API-兑换码与会员状态.md)。

## 13. Wear 二维码设备授权

相关接口：

- `POST /api/v1/auth/device/qr/start`
- `POST /api/v1/auth/device/qr/approve`
- `POST /api/v1/auth/device/qr/poll`
- `POST /api/v1/auth/device/qr/status`

这些接口属于后端签名保护路由。Mobile approval/status 使用 `ANDROID_MOBILE`；Wear start/poll 使用 `ANDROID_WEAR`。

完整契约见 [API-Wear二维码登录.md](./API-Wear二维码登录.md)。

## 14. 账号注销

### `POST /api/v1/account/delete`
请求头：
- `Authorization: Bearer <accessToken>`
- Android release 客户端四个签名头

请求体：
```json
{
  "currentPassword": "plain-password",
  "confirm": "DELETE"
}
```

客户端行为：
- 账户页展示危险操作入口，要求输入当前密码和确认文本，并弹出二次确认对话框。
- 成功后立即清理本地 access/refresh token、账户摘要、会员缓存和待验证邮箱状态，并回到未登录状态。
- 其他设备会在下一次接口请求、token 刷新或前台补拉时被服务端撤销。

## 15. 设备标识与调试头

Mobile 配置了本地设备 ID 时，账户请求可携带：

```http
X-Classing-Device-ID: <最多 128 字符>
```

Wear 登录调试流程在显式调试模式下可携带：

```http
X-Classing-Debug: true
```

调试字段不得替代身份认证、设备授权密钥或客户端签名校验。

## 16. 错误码
- `AUTH_INVALID_CREDENTIALS`
- `AUTH_ACCOUNT_DISABLED`
- `AUTH_CONSENT_REQUIRED`
- `AUTH_REFRESH_EXPIRED`
- `AUTH_REFRESH_REVOKED`
- `AUTH_RESET_TOKEN_INVALID`
- `AUTH_RESET_TOKEN_EXPIRED`
- `AUTH_RESET_TOKEN_USED`
- `AUTH_EMAIL_ALREADY_EXISTS`
- `AUTH_USERNAME_ALREADY_EXISTS`
- `AUTH_RATE_LIMITED`
- `AUTH_LOGIN_LOCKED`
- `AUTH_SESSION_REVOKED`
- `IP_RATE_LIMITED`
- `ACCOUNT_RATE_LIMITED`
- `CLIENT_SIGNATURE_INVALID`
- `CLIENT_SIGNATURE_POLICY_MISSING`

签名失败时客户端展示：

```text
签名异常，客户端可能被非法修改，已禁止使用在线功能
```
