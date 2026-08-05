# API：Wear 二维码登录

> 文档基准：WearOS_ClassingTimeTable `main`，包含已合并 PR #15。  
> Android release 客户端签名契约见 [API-客户端签名校验与证书注册.md](./API-客户端签名校验与证书注册.md)。

## 1. 用途

当 Wear 未从已连接手机收到登录状态，或手机尚未登录时，Wear 可生成短时二维码。用户在 Mobile 的“账号”页面登录后扫码并确认，为该 Wear 创建独立账号会话。

普通情况下仍优先使用手机下发的账号摘要；二维码是无法自动获得登录状态时的备用入口。

二维码兑换成功后，Wear 会立即使用独立会话直连官方云，同步 `wear.settings`。之后 Wear 设置变更、手动同步、应用启动和 30 分钟后台任务都会继续同步该设置域；课表仍由 Mobile 完成 V2 合并后通过 Data Layer 下发。

PR #15 后，Wear release 构建在二维码 start、poll 以及独立会话的官方云在线请求前执行客户端签名预检；Mobile release 构建在 approve、status 前执行签名预检。

## 2. 安全模型

- 二维码只包含公开的 `authorizationId`，不包含 access token、refresh token 或轮询密钥。
- `pollSecret` 只返回给发起请求的 Wear，并使用加密本地存储；后端只保存其哈希。
- 授权 5 分钟过期，只能批准一次、兑换一次。
- Mobile 必须携带当前有效的 Bearer access token 才能批准或读取调试状态。
- Wear 兑换成功后得到独立会话；退出、密码重置、账号停用等现有会话撤销规则继续生效。
- Mobile 扫码后必须显示确认对话框，避免静默批准陌生设备。
- Wear token 不进入 Data Layer 快照或官方云文档。
- Wear 官方云请求使用独立会话，并在 access token 过期或返回 401 时通过 single-flight 刷新后重试一次。
- Wear 当前不依赖二维码接口传递 APK 证书；证书由运行中的应用从安装包签名信息计算，并通过请求头发送。
- PR #15 为 Wear 官方云同步与事件客户端加入签名校验；产品层的数据分工仍是 Wear 直连 `wear.settings`，课表合并由 Mobile 负责。
- 签名头只能用于识别普通重新签名客户端，不能替代 `pollSecret`、Bearer token 或平台证明。

## 3. Android release 签名头

### Wear 请求

```http
X-Classing-Client-Platform: ANDROID_WEAR
X-Classing-Package-Name: com.xtawa.classingtime
X-Classing-Version-Code: <positive-integer>
X-Classing-Signing-Cert-Sha256: <64-hex-certificate-fingerprint>
```

### Mobile 请求

```http
X-Classing-Client-Platform: ANDROID_MOBILE
X-Classing-Package-Name: com.xtawa.classingtime
X-Classing-Version-Code: <positive-integer>
X-Classing-Signing-Cert-Sha256: <64-hex-certificate-fingerprint>
```

release 客户端在调用二维码接口前先请求：

```http
POST /api/v1/client/signature/check
```

成功结果可缓存 5 分钟。签名失败后禁止继续二维码登录，不得仅依赖 `authorizationId` / `pollSecret` 绕过签名门禁。

## 4. 接口

### `POST /api/v1/auth/device/qr/start`

无需用户登录，但属于客户端签名保护路由。

请求头：

```http
Accept: application/json
Content-Type: application/json
X-Classing-Client-Platform: ANDROID_WEAR
X-Classing-Package-Name: com.xtawa.classingtime
X-Classing-Version-Code: <versionCode>
X-Classing-Signing-Cert-Sha256: <certSha256>
```

请求：

```json
{"deviceName":"Google Pixel Watch"}
```

成功：`201 Created`

```json
{
  "authorizationId": "dva_...",
  "pollSecret": "...",
  "qrPayload": "classing://wear-login?authorizationId=dva_...",
  "expiresAt": 1784260000000,
  "intervalSeconds": 5
}
```

客户端规则：
- `pollSecret` 只写入 Wear 加密存储。
- 二维码只使用响应中的 `qrPayload`，不得自行把 `pollSecret` 拼入二维码。
- `deviceName` 应使用用户可识别的设备名称，并遵守服务端长度限制。

### `POST /api/v1/auth/device/qr/approve`

需要 Mobile 登录会话与 Mobile release 签名头：

```http
Authorization: Bearer <mobile-access-token>
Content-Type: application/json
X-Classing-Client-Platform: ANDROID_MOBILE
X-Classing-Package-Name: com.xtawa.classingtime
X-Classing-Version-Code: <versionCode>
X-Classing-Signing-Cert-Sha256: <certSha256>
```

请求：

```json
{
  "authorizationId": "dva_...",
  "debug": false
}
```

成功返回 `200`：

```json
{
  "status": "APPROVED",
  "authorizationId": "dva_...",
  "expiresAt": 1784260000000
}
```

同一账号重复批准是幂等的；其他账号覆盖已批准请求返回 `409 DEVICE_AUTH_ALREADY_APPROVED`。

显式调试时，Mobile 可同时发送：

```http
X-Classing-Debug: true
```

并在请求体中设置：

```json
{"authorizationId":"dva_...","debug":true}
```

服务端可在响应中增加 `debug`，普通用户流程不得默认暴露调试字段。

### `POST /api/v1/auth/device/qr/poll`

Wear 按 `intervalSeconds` 轮询，默认每 5 秒一次。

请求头包含 Wear release 四个签名头。

请求：

```json
{
  "authorizationId": "dva_...",
  "pollSecret": "仅保存在Wear上的密钥"
}
```

未批准：

```http
HTTP/1.1 202 Accepted
```

```json
{
  "status": "PENDING",
  "authorizationId": "dva_...",
  "expiresAt": 1784260000000,
  "intervalSeconds": 5
}
```

批准后首次兑换：`200 APPROVED`，响应包含独立的 `session`、`account` 和 `membership`：

```json
{
  "status": "APPROVED",
  "authorizationId": "dva_...",
  "session": {
    "accessToken": "...",
    "refreshToken": "...",
    "accessExpiresAt": 1784260000000,
    "refreshExpiresAt": 1786852000000
  },
  "account": { ... },
  "membership": { ... }
}
```

再次兑换：

```http
HTTP/1.1 410 Gone
```

```json
{
  "code": "DEVICE_AUTH_CONSUMED",
  "message": "device authorization has already been consumed",
  "requestId": "req_..."
}
```

短暂网络错误时，Wear 保留同一 `authorizationId` 与 `pollSecret`，在 `expiresAt` 前继续按间隔轮询；签名校验失败、凭据无效、已消费或已过期属于终止错误。

### `POST /api/v1/auth/device/qr/status`

用途：Mobile 在显式调试模式下观察批准后的 Wear 登录完成状态。

需要 Mobile Bearer token、Mobile release 四个签名头和调试标记。

请求头：

```http
Authorization: Bearer <mobile-access-token>
Content-Type: application/json
X-Classing-Debug: true
```

请求：

```json
{
  "authorizationId": "dva_...",
  "debug": true
}
```

响应中的 `debug`：

```json
{
  "debug": {
    "stage": "WEAR_LOGIN",
    "terminal": false,
    "loginSucceeded": null,
    "approvalSucceeded": true,
    "code": "",
    "reason": "",
    "authorizationId": "dva_...",
    "serverTimestampMs": 1784260000000
  }
}
```

字段：
- `stage`：当前调试阶段。
- `terminal`：是否已进入最终状态。
- `loginSucceeded`：Wear 独立会话是否建立成功；未知时可为 `null`。
- `approvalSucceeded`：Mobile 批准是否成功。
- `code` / `reason`：终止错误或调试原因。
- `serverTimestampMs`：服务端生成调试状态的时间。

Mobile 当前最多观察 10 次、每次间隔约 1 秒；超出窗口时本地报告 `DEVICE_LOGIN_DEBUG_TIMEOUT`，不代表服务端授权一定已过期。

## 5. 二维码成功后的在线请求

Wear 保存独立 session 后：

1. 先通过签名预检。
2. 使用 Bearer access token 和 Wear 四个签名头连接官方云。
3. 立即同步 `wear.settings`。
4. access token 过期或 401 时 single-flight 刷新一次。
5. refresh 失败或会话被撤销时清理独立会话并重新显示登录入口。
6. 签名预检失败时保留本地 Wear 设置，但禁止官方云在线功能。

## 6. 错误码

设备授权：
- `DEVICE_AUTH_CREDENTIALS_REQUIRED`
- `DEVICE_AUTH_INVALID`
- `DEVICE_AUTH_EXPIRED`
- `DEVICE_AUTH_CONSUMED`
- `DEVICE_AUTH_ALREADY_APPROVED`
- `DEVICE_AUTH_ACCOUNT_UNAVAILABLE`
- `DEVICE_AUTH_NAME_TOO_LONG`

客户端签名：
- `CLIENT_SIGNATURE_INVALID`
- `CLIENT_SIGNATURE_POLICY_MISSING`

认证与限流：
- `AUTH_REQUIRED`
- `AUTH_SESSION_REVOKED`
- `AUTH_REFRESH_REVOKED`
- `AUTH_RATE_LIMITED`

签名错误提示：

```text
签名异常，客户端可能被非法修改，已禁止使用在线功能
```

## 7. 相关文档

- [API-客户端签名校验与证书注册.md](./API-客户端签名校验与证书注册.md)
- [API-账户会员密码重置.md](./API-账户会员密码重置.md)
- [API-官方云同步与同步项目.md](./API-官方云同步与同步项目.md)
