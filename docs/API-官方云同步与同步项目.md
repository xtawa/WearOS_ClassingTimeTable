# API：官方云同步与同步项目

## 1. 基本规则
- Provider 名称：`OFFICIAL`
- 固定基址：`https://api-classing.underflo.ink`
- 路径前缀：`/api/v1/cloud/official`
- 客户端不可修改域名。
- 登录账户可同步设置域；有效会员可额外同步课表域。

## 2. 客户端配置模型
```json
{
  "cloudProvider": "OFFICIAL",
  "cloudSyncEnabled": true,
  "officialSyncFrequency": "EVERY_30_MIN",
  "syncScopes": ["TIMETABLE", "MOBILE_SETTINGS", "WEAR_SETTINGS"]
}
```

## 3. 同步项目定义
- `TIMETABLE`
  - 课程表
  - 调课/补课
  - 例外与一次性事件
- `MOBILE_SETTINGS`
  - 周视图设置
  - 提醒设置
  - Dashboard 设置
  - 每日简报配置
- `WEAR_SETTINGS`
  - 手表展示偏好

不进入云同步：
- `accessToken`
- `refreshToken`
- 会员缓存
- WebDAV 密码
- Drive Token

## 4. 接口建议

### `GET /api/v1/cloud/official/document`
- 拉取当前云文档。
- 请求头：
  - `Authorization: Bearer <accessToken>`

### `PUT /api/v1/cloud/official/document`
- 上传合并后的云文档。
- 请求头：
  - `Authorization: Bearer <accessToken>`
  - `If-Match: <etag-or-version>`
  - `Idempotency-Key: <uuid>`

### `POST /api/v1/cloud/official/test`
- 测试账户令牌与官方云连接；`GET /api/v1/cloud/official/ping` 提供相同的轻量连接检测。
- 响应通过 `canSyncSettings` 与 `canSyncTimetable` 区分设置和课表权限。

### `GET /api/v1/cloud/official/config`
- 可选接口，返回服务端下发的限制、限流策略、最大文档大小等。

## 5. 幂等与并发
- 每次写入必须带 `Idempotency-Key`。
- 服务端保存最近一段时间的 key，避免客户端重试重复提交。
- 并发冲突使用：
  - `409 Conflict`
  - 或 `412 Precondition Failed`

## 6. 鉴权与权限失败码
- `OFFICIAL_CLOUD_MEMBERSHIP_REQUIRED`
- `OFFICIAL_CLOUD_ACCOUNT_REQUIRED`
- `OFFICIAL_CLOUD_PERMISSION_DENIED`

客户端收到 access token 的 `401` 后应经 single-flight 刷新一次并重试；`403` 是权限错误，不得当作 token 过期循环刷新。

## 7. Scope 合并规则
- 客户端本地 `syncScopes` 决定参与合并的 Domain。
- 关闭的 Scope：
  - 不向远端推送
  - 不用远端覆盖本地
  - 不触发删除传播

## 8. 自动同步频率
- `MANUAL_ONLY`
- `EVERY_15_MIN`
- `EVERY_30_MIN`
- `EVERY_1_HOUR`
- `EVERY_3_HOURS`

说明：
- Android 最小周期按 15 分钟对齐。
- 频率变更后客户端需重建 WorkManager 周期任务。
