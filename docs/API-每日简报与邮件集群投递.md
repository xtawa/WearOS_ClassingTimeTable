# API：每日简报与邮件集群投递

> 文档基准：WearOS_ClassingTimeTable `main`，包含已合并 PR #15。  
> Android release 客户端签名契约见 [API-客户端签名校验与证书注册.md](./API-客户端签名校验与证书注册.md)。

## 1. 客户端设置模型
```json
{
  "enabled": true,
  "channel": "BOTH",
  "time": "20:00"
}
```

`channel` 枚举：
- `APP_NOTIFICATION`
- `EMAIL`
- `BOTH`

规则：
- `APP_NOTIFICATION` 可离线工作。
- `EMAIL` 与 `BOTH` 要求用户已登录。
- 本地通知配置不需要后端在线请求。
- 读取、保存、删除或测试后端简报订阅属于在线功能，Android release 客户端必须先通过签名预检。

## 2. Android release 客户端签名要求

调用本文件中的 `/api/v1/briefings/daily` 接口前：

```http
POST /api/v1/client/signature/check
```

成功后，后续请求携带：

```http
X-Classing-Client-Platform: ANDROID_MOBILE
X-Classing-Package-Name: com.xtawa.classingtime
X-Classing-Version-Code: <positive-integer>
X-Classing-Signing-Cert-Sha256: <64-hex-certificate-fingerprint>
```

签名预检失败时禁止提交、读取、删除或测试邮件简报配置，不得降级为无签名请求。

## 3. 客户端接口

### `GET /api/v1/briefings/daily`

用途：读取当前账号的每日简报订阅。

请求头：

```http
Authorization: Bearer <accessToken>
Accept: application/json
```

Android release 客户端同时携带四个签名头。

当账号没有订阅时，客户端应按服务端实际响应处理为空配置或未启用配置，不得使用其他账号的本地缓存。

### `PUT /api/v1/briefings/daily`
请求头：
- `Authorization: Bearer <accessToken>`
- `Content-Type: application/json`
- Android release 客户端四个签名头

请求体：
```json
{
  "enabled": true,
  "channel": "BOTH",
  "time": "20:00",
  "timezone": "Asia/Shanghai"
}
```

当前 Android `AccountApiClient` 提交 `enabled`、`channel` 与 `time`；服务端或其他客户端需要时可同时提交 `timezone`。客户端不得假设服务端一定采用设备当前时区，应以接口响应或已保存配置为准。

### `DELETE /api/v1/briefings/daily`
- 取消邮件订阅。
- 请求头包含 Bearer token 与 Android release 签名头。
- 删除后本地邮件订阅状态应同步置为未启用；本地 `APP_NOTIFICATION` 调度是否取消由用户当前渠道配置决定。

### `POST /api/v1/briefings/daily/test`
- 试发或预览。
- 请求头包含 Bearer token 与 Android release 签名头。
- 响应可返回：
  - 纯文本预览
  - HTML 预览
  - 本次将使用的发件邮箱标识
- 该接口账户限流为 5 次/分钟；客户端测试按钮应建立冷却，读取 `Retry-After`，不得连续自动重试。

## 4. 后端任务模型
- `briefing_subscription`
  - `userId`
  - `channel`
  - `time`
  - `timezone`
  - `enabled`
  - `lastScheduledFor`
- `briefing_job`
  - `jobId`
  - `userId`
  - `targetDate`
  - `channel`
  - `status`
  - `providerMailboxId`
  - `retryCount`

## 5. SMTP 邮箱池设计
- `mailbox`
  - `mailboxId`
  - `smtpHost`
  - `smtpPort`
  - `username`
  - `passwordSecretRef`
  - `dailyQuota`
  - `usedToday`
  - `enabled`
- 调度规则：
  1. 从启用邮箱中选择 `usedToday < dailyQuota` 的邮箱。
  2. 优先选当天发送量最低者。
  3. 达到上限后自动切换到下一邮箱。
  4. 所有邮箱都满额时，任务转入待重试或次日补发队列。

## 6. 集群投递要求
- 投递服务无状态化。
- 配置与计数放数据库/Redis。
- 同一用户同一天同一频道只能生成一个正式任务。
- 通过分布式锁或唯一索引防止重复发送。
- 签名校验只作用于客户端 API 请求，不替代 worker 对任务、用户、订阅状态的服务端校验。

## 7. 邮件内容
- 今日总课数
- 今日剩余课时
- 下一节课
- 特殊调课/例外提醒
- Dashboard 摘要链接或打开 App 深链

## 8. 限流

| 接口 | IP 维度 | 账户维度 |
|---|---|---|
| `GET /api/v1/briefings/daily` | 按服务端通用策略 | 按服务端通用策略 |
| `PUT /api/v1/briefings/daily` | 敏感接口共享 60 次/分钟 | 30 次/分钟 |
| `DELETE /api/v1/briefings/daily` | 敏感接口共享 60 次/分钟 | 30 次/分钟 |
| `POST /api/v1/briefings/daily/test` | 敏感接口共享 60 次/分钟 | 5 次/分钟 |

429 响应读取 `Retry-After`。429 不得触发退出登录或清除账户凭据。

## 9. 错误码
- `BRIEFING_LOGIN_REQUIRED`
- `BRIEFING_EMAIL_CHANNEL_DISABLED`
- `BRIEFING_INVALID_TIME`
- `BRIEFING_MAILBOX_POOL_EXHAUSTED`
- `BRIEFING_TEST_SEND_FAILED`
- `IP_RATE_LIMITED` — 同一 IP 对敏感接口的请求超过 60 次/分钟（HTTP 429，携带 `Retry-After: 60`）
- `ACCOUNT_RATE_LIMITED` — 同一账户：简报测试 5 次/分钟、简报配置变更 30 次/分钟（HTTP 429，携带 `Retry-After: 60`）
- `CLIENT_SIGNATURE_INVALID` — Android 客户端签名无效或不受信任
- `CLIENT_SIGNATURE_POLICY_MISSING` — 服务端缺少可信 release 签名策略
