# API：账户、认证、密码重置

## 1. 基础约束
- Base URL：`https://api-classing.underflo.ink`
- 所有响应建议统一：
  - `code`
  - `message`
  - `data`
  - `requestId`
- 所有时间统一使用 Unix epoch milliseconds。

## 2. 注册

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

## 3. 登录

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
- 登录和注册页面必须先通过 `GET /api/v1/auth/registration/config` 获取 `legalAgreementUrls`，并展示“您已阅读并同意《隐私政策》《用户协议》和《个人数据跨境传输协议》”复选框。
- 未勾选三项协议或协议 URL 未成功下发时，不得提交登录、发送注册验证码、确认注册验证码或重发验证码。
- 服务端返回 `AUTH_CONSENT_REQUIRED` 时，客户端展示协议确认错误并保持在当前页面。

## 4. 刷新 Token

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
- 仅在服务端明确返回 `401 AUTH_REFRESH_REVOKED` 且本地仍保存本次尝试的旧 token 时清除会话；网络错误、超时与 5xx 不应清除凭据。

## 5. 登出

### `POST /api/v1/auth/logout`
请求头：
- `Authorization: Bearer <accessToken>`

请求体：
```json
{
  "refreshToken": "opaque-refresh-token"
}
```

说明：
- 后端需撤销 refresh token。
- 若使用黑名单方案，可同时短时拉黑 access token。

## 6. 当前用户资料

### `GET /api/v1/account/me`
请求头：
- `Authorization: Bearer <accessToken>`

响应体建议：
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

## 7. 密码重置申请

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

## 8. 密码重置确认

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

## 9. 错误码建议
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
- `IP_RATE_LIMITED` — 同一 IP 对敏感接口的请求超过 60 次/分钟（HTTP 429，携带 `Retry-After: 60`）
- `ACCOUNT_RATE_LIMITED` — 同一账户密码修改超过 10 次/分钟（HTTP 429，携带 `Retry-After: 60`）

## 10. 账号注销

### `POST /api/v1/account/delete`
请求头：
- `Authorization: Bearer <accessToken>`

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
