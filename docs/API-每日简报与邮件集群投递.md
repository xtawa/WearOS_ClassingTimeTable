# API：每日简报与邮件集群投递

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

## 2. 客户端接口建议

### `PUT /api/v1/briefings/daily`
请求头：
- `Authorization: Bearer <accessToken>`

请求体：
```json
{
  "enabled": true,
  "channel": "BOTH",
  "time": "20:00",
  "timezone": "Asia/Shanghai"
}
```

### `DELETE /api/v1/briefings/daily`
- 取消邮件订阅。

### `POST /api/v1/briefings/daily/test`
- 试发或预览。
- 响应可返回：
  - 纯文本预览
  - HTML 预览
  - 本次将使用的发件邮箱标识

## 3. 后端任务模型
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

## 4. SMTP 邮箱池设计
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

## 5. 集群投递建议
- 投递服务无状态化。
- 配置与计数放数据库/Redis。
- 同一用户同一天同一频道只能生成一个正式任务。
- 通过分布式锁或唯一索引防止重复发送。

## 6. 邮件内容建议
- 今日总课数
- 今日剩余课时
- 下一节课
- 特殊调课/例外提醒
- Dashboard 摘要链接或打开 App 深链

## 7. 错误码建议
- `BRIEFING_LOGIN_REQUIRED`
- `BRIEFING_EMAIL_CHANNEL_DISABLED`
- `BRIEFING_INVALID_TIME`
- `BRIEFING_MAILBOX_POOL_EXHAUSTED`
- `BRIEFING_TEST_SEND_FAILED`
