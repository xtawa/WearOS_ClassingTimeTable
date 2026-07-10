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

### `POST /api/v1/auth/register`
请求体：
```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "plain-or-prehashed"
}
```

成功响应：
```json
{
  "session": {
    "accessToken": "jwt-or-opaque",
    "refreshToken": "opaque-refresh-token"
  }
}
```

约束：
- `username` 全局唯一。
- `email` 全局唯一。
- 后端必须记录邮箱验证状态字段，哪怕 MVP 阶段不强制验证。

## 3. 登录

### `POST /api/v1/auth/login`
请求体：
```json
{
  "identifier": "alice@example.com",
  "password": "plain-or-prehashed"
}
```

说明：
- `identifier` 可为邮箱或用户名。

## 4. 刷新 Token

### `POST /api/v1/auth/refresh`
请求体：
```json
{
  "refreshToken": "opaque-refresh-token"
}
```

说明：
- 刷新成功后建议轮换 refresh token。
- 旧 refresh token 立即失效，防止并发复用。

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
- `AUTH_REFRESH_EXPIRED`
- `AUTH_REFRESH_REVOKED`
- `AUTH_RESET_TOKEN_INVALID`
- `AUTH_RESET_TOKEN_EXPIRED`
- `AUTH_RESET_TOKEN_USED`
- `AUTH_EMAIL_ALREADY_EXISTS`
- `AUTH_USERNAME_ALREADY_EXISTS`
