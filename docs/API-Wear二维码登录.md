# API：Wear 二维码登录

## 1. 用途

当 Wear 未从已连接手机收到登录状态，或手机尚未登录时，Wear 可生成短时二维码。用户在 Mobile 的“账号”页面登录后扫码并确认，为该 Wear 创建独立账号会话。

普通情况下仍优先使用手机下发的账号摘要；二维码是无法自动获得登录状态时的备用入口。

## 2. 安全模型

- 二维码只包含公开的 `authorizationId`，不包含 access token、refresh token 或轮询密钥。
- `pollSecret` 只返回给发起请求的 Wear，并使用加密本地存储；后端只保存其哈希。
- 授权 5 分钟过期，只能批准一次、兑换一次。
- Mobile 必须携带当前有效的 Bearer access token 才能批准。
- Wear 兑换成功后得到独立会话；退出、密码重置、账号停用等现有会话撤销规则继续生效。
- Mobile 扫码后必须显示确认对话框，避免静默批准陌生设备。
- Wear Token 不进入 Data Layer 快照或官方云文档。

## 3. 接口

### `POST /api/v1/auth/device/qr/start`

无需登录。

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

### `POST /api/v1/auth/device/qr/approve`

需要 Mobile 登录会话：

```http
Authorization: Bearer <mobile-access-token>
```

```json
{"authorizationId":"dva_..."}
```

成功返回 `200` 和 `APPROVED`。同一账号重复批准是幂等的；其他账号覆盖已批准请求返回 `409 DEVICE_AUTH_ALREADY_APPROVED`。

### `POST /api/v1/auth/device/qr/poll`

Wear 每 5 秒轮询：

```json
{
  "authorizationId": "dva_...",
  "pollSecret": "仅保存在Wear上的密钥"
}
```

未批准：`202 PENDING`。

批准后首次兑换：`200 APPROVED`，响应包含独立的 `session`、`account` 和 `membership`。

再次兑换：`410 DEVICE_AUTH_CONSUMED`。

## 4. 错误码

- `DEVICE_AUTH_CREDENTIALS_REQUIRED`
- `DEVICE_AUTH_INVALID`
- `DEVICE_AUTH_EXPIRED`
- `DEVICE_AUTH_CONSUMED`
- `DEVICE_AUTH_ALREADY_APPROVED`
- `DEVICE_AUTH_ACCOUNT_UNAVAILABLE`
- `DEVICE_AUTH_NAME_TOO_LONG`
