# API：邮箱变更安全流程

> 适用版本：classing-backend ≥ 2026-07-11
> 影响端：Android Mobile / Android Wear / Web Admin（账户设置页）

## 变更概述

为修复「access token 被盗后可直接修改恢复邮箱并接管账户」的高危漏洞，后端对 `PATCH /api/v1/account/me` 的邮箱修改逻辑做了安全加固：

1. **修改邮箱必须提供当前密码**（`currentPassword`）。
2. **新邮箱不会立即生效**，而是进入 `pending` 状态。
3. 后端向**新邮箱**发送 6 位验证码；向**旧邮箱**发送变更通知邮件。
4. 用户需要调用 `POST /api/v1/account/email/confirm` 提交验证码，确认后邮箱才真正替换。
5. 确认变更后，**所有会话被撤销**（`auth_epoch` 提升 + 所有 refresh token 作废），用户必须重新登录。

在确认完成前，登录、密码重置等流程仍然使用**旧邮箱**。

---

## 接口变化

### 1. PATCH /api/v1/account/me

用途：修改用户名 / 发起邮箱变更。

#### 请求体

```json
{
  "username": "alice",
  "email": "alice.new@example.com",
  "currentPassword": "UserPass123!"
}
```

- `username`：必填，规则不变。
- `email`：如与当前邮箱不同，则视为**发起邮箱变更**。
- `currentPassword`：当 `email` 与当前邮箱不一致时，后端会校验当前密码。**不传或错误均返回 403。**

#### 仅修改用户名（email 不变或与当前邮箱一致）

```json
HTTP/1.1 200 OK
{
  "account": {
    "userId": "usr_xxx",
    "identifier": "alice@example.com",
    "username": "alice",
    "email": "alice@example.com",
    "role": "USER",
    "status": "ACTIVE",
    "emailVerified": true,
    "createdAt": 1700000000000,
    "updatedAt": 1700000000000
  }
}
```

#### 发起邮箱变更

```json
HTTP/1.1 202 Accepted
{
  "account": { ... },
  "emailChange": {
    "requestId": "ecr_xxx",
    "expiresAt": 1700003600000,
    "resendAfterSeconds": 60
  }
}
```

- 返回 `202`，表示已接受申请，等待验证码确认。
- `emailChange.requestId`：客户端需要保存，用于下一步确认接口。
- `emailChange.expiresAt`：验证码过期时间（毫秒时间戳）。
- `emailChange.resendAfterSeconds`：距离可再次发起邮箱变更的冷却时间，当前固定 60 秒。

在 `test` / `development` 环境且 `ExposeVerificationCode=true` 时，响应会额外包含：

```json
"devVerificationCode": "123456"
```

生产环境**不会**返回该字段。

#### 错误码

| HTTP | code | 含义 |
|---|---|---|
| 400 | `ACCOUNT_USERNAME_INVALID` | 用户名不合法 |
| 400 | `ACCOUNT_EMAIL_INVALID` | 邮箱格式不合法 |
| 403 | `ACCOUNT_PASSWORD_CURRENT_INVALID` | 当前密码错误 |
| 409 | `ACCOUNT_EMAIL_CONFLICT` | 新邮箱已被其他账户占用 |
| 429 | `ACCOUNT_EMAIL_RATE_LIMITED` | 60 秒内重复发起，需等待 `Retry-After` |
| 503 | `AUTH_EMAIL_DELIVERY_FAILED` | 验证码邮件入队失败 |

---

### 2. POST /api/v1/account/email/confirm

用途：确认新邮箱。

#### 请求体

```json
{
  "requestId": "ecr_xxx",
  "verificationCode": "123456"
}
```

#### 成功响应

```json
HTTP/1.1 200 OK
{
  "account": {
    "userId": "usr_xxx",
    "identifier": "alice.new@example.com",
    "username": "alice",
    "email": "alice.new@example.com",
    ...
  },
  "sessionsRevoked": true
}
```

- `sessionsRevoked: true` 表示当前 access token 与所有 refresh token 已失效。
- 客户端**必须立即清除本地 token 缓存，并跳转登录页**。

#### 错误码

| HTTP | code | 含义 |
|---|---|---|
| 400 | `ACCOUNT_EMAIL_VERIFICATION_INVALID` | 验证码错误、过期或已使用 |
| 409 | `ACCOUNT_EMAIL_CONFLICT` | 确认时邮箱已被他人占用（并发冲突） |

---

### 3. GET /api/v1/account/me

用途：查询当前账户信息，现增加 pending 邮箱变更状态。

#### 响应示例

```json
{
  "account": { ... },
  "pendingEmailChange": {
    "newEmail": "alice.new@example.com",
    "expiresAt": 1700003600000
  }
}
```

- 当没有 pending 的邮箱变更时，`pendingEmailChange` 字段**不存在**。

---

## 客户端改造建议

### Android Mobile / Wear

1. **账户设置页「邮箱」项**
   - 点击后进入新页面：输入新邮箱 + 当前密码。
   - 提交成功后显示「验证邮件已发送」提示，并保存 `requestId`。

2. **验证码输入页**
   - 6 位数字输入框。
   - 调用 `POST /api/v1/account/email/confirm`。
   - 成功后弹窗告知用户「邮箱已变更，请重新登录」，并清空 token 跳转登录页。

3. **未确认前回显**
   - 每次进入账户页调用 `GET /api/v1/account/me`。
   - 如果存在 `pendingEmailChange`，在邮箱旁显示「待验证：alice.new@example.com」及倒计时。

4. **Token 失效处理**
   - 确认成功后 `sessionsRevoked: true`，所有 API 后续会返回 401。
   - 客户端全局 401 处理器应引导重新登录，避免停留在旧页面。

### Web Admin

- 账户设置表单增加「当前密码」输入框。
- 提交邮箱变更后进入验证码确认步骤。
- 确认成功后退出登录并提示重新登录。

---

## 兼容性说明

- 旧版客户端如果只发送 `username` / `email` 而不带 `currentPassword`，在**发起邮箱变更**时会收到 `403 ACCOUNT_PASSWORD_CURRENT_INVALID`。
- 仅修改用户名时，`currentPassword` 非必填，旧逻辑保持兼容。
- 邮箱变更从「一步完成」变为「两步确认」，客户端 UI 流程必须同步改造。

---

## 相关后端文件

- `internal/httpapi/handlers_account.go`：`updateAccount` / `confirmEmailChange` / `accountMe`
- `internal/httpapi/server.go`：`POST /api/v1/account/email/confirm` 路由
- `internal/store/users.go`：`CreateEmailChangeRequest` / `ConsumeEmailChangeRequest` / `PendingEmailChange`
- `internal/store/briefings.go` / `internal/worker/worker.go`：验证码与通知邮件投递
- `internal/store/migrations.go`：`email_change_requests` 表
