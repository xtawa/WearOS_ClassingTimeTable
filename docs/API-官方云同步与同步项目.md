# API：官方云同步与同步项目

> 文档基准：WearOS_ClassingTimeTable `main`，包含已合并 PR #15。  
> 客户端签名校验后端接口来自 `classing_backend` PR #6，需在该后端变更合并并部署后生效。

## 1. 基本规则
- Provider 名称：`OFFICIAL`
- 固定基址：`https://api-classing.underflo.ink`
- 路径前缀：`/api/v1/cloud/official`
- 客户端不可修改域名。
- 登录账户可同步设置域；有效会员可额外同步课表域。
- 已启用官方云同步的用户在会员过期后仍保持设置同步；Mobile/Wear/Web 不应继续上传或应用课表域。
- 服务端对过期会员的 `GET /document` 只返回 `mobile.settings`、`wear.settings`、`cloud.config`、`app.commands`，对 `PUT /document` 只合并这些设置/命令域并忽略 `timetable.lessons` 与 `timetable.exceptions`。
- 客户端本地仍可保留既有课表和设置；会员恢复后再按 V2 合并规则重新参与课表同步。
- PR #15 后，本地持久化的 `MembershipSummary.isMember` 不能作为课表域在线授权依据。
- Mobile 每次执行可能读取、上传、应用 `TIMETABLE` 域的官方云同步前，必须通过当前会话实时调用 `GET /api/v1/membership/status`。
- 在线会员校验失败时采用 fail-closed：保留本地课表，不执行会员限定课表域同步；设置域是否继续同步由当前有效登录会话与服务端响应决定。

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
- APK 签名证书指纹缓存
- 签名预检信任缓存

## 4. Android release 客户端统一请求头

Mobile/Wear release 构建在调用官方云接口前必须先调用：

```http
POST /api/v1/client/signature/check
```

成功后，官方云请求携带：

```http
X-Classing-Client-Platform: ANDROID_MOBILE
X-Classing-Package-Name: com.xtawa.classingtime
X-Classing-Version-Code: <positive-integer>
X-Classing-Signing-Cert-Sha256: <64-hex-certificate-fingerprint>
```

Wear 独立会话使用 `ANDROID_WEAR`。

签名预检成功结果可按 `baseUrl + packageName + platform + versionCode + certificate` 在进程内缓存 5 分钟。缓存过期后重新预检。

## 5. 接口

### `GET /api/v1/cloud/official/document`
- 拉取当前云文档。
- 请求头：
  - `Authorization: Bearer <accessToken>`
  - Android release 客户端四个签名头
- Mobile 在准备应用远端课表域之前，必须使用同一有效会话实时请求 `GET /api/v1/membership/status`。
- 在线会员状态无效时，只处理服务端允许的设置与命令域。

### `PUT /api/v1/cloud/official/document`
- 上传合并后的云文档。
- 请求头：
  - `Authorization: Bearer <accessToken>`
  - `If-Match: <etag-or-version>`
  - `Idempotency-Key: <uuid>`（≤ 128 字节，超长返回 `400 IDEMPOTENCY_KEY_TOO_LONG`）
  - Android release 客户端四个签名头
- 上传正文包含 `timetable.lessons` 或 `timetable.exceptions` 前，客户端必须完成实时会员校验。
- 校验失败时不得沿用缓存中的会员状态构造课表域上传正文。

### `POST /api/v1/cloud/official/test`
- 测试账户令牌与官方云连接。
- 请求头包含 Bearer token 与 Android release 签名头。
- 响应通过 `canSyncSettings` 与 `canSyncTimetable` 区分设置和课表权限。
- `canSyncTimetable` 只能作为本次服务端响应中的权限信息，不应长期替代会员状态刷新。

### `GET /api/v1/cloud/official/ping`
- 提供与 test 接口对应的轻量连接检测。
- Android release 客户端需要签名预检与签名头。

### `GET /api/v1/cloud/official/config`
- 返回服务端下发的限制、限流策略、最大文档大小等。
- Android release 客户端需要签名预检与签名头。

### `GET /api/v1/cloud/official/events`
- 前台近实时变更通知流，使用 `Authorization: Bearer <accessToken>` 与 `text/event-stream`。
- Android release 客户端需要四个签名头；建立 SSE 连接前先执行签名预检。
- `Last-Event-ID` 是最后已应用的非负整数文档版本，与文档 `ETag` 的版本一致。
- 事件名固定为 `cloud-document`，事件 ID 为最新文档版本，`data` 仅包含 `version` 与 `updatedAt`，不携带设置或课表正文。
- 未提供游标时服务端立即发送当前版本；游标落后时仅通知最新版本，客户端随后通过文档 GET 拉取并执行 V2 合并。
- Mobile 仅在应用前台保持连接，401 经 single-flight 刷新后重连；后台由 WorkManager 兜底，恢复前台时立即补拉。
- Web 登录会话期间保持连接。
- Wear PR #15 代码已为独立官方云事件客户端加入签名校验与签名头；当前产品数据分工仍应以 Mobile 负责课表合并、Wear 直连同步 `wear.settings` 为准。

## 6. 实时会员门禁流程

Mobile 执行官方云同步时：

1. 对 release 构建执行 `POST /api/v1/client/signature/check`。
2. 确保 access token 可用；若已过期，使用 single-flight 刷新。
3. 调用 `GET /api/v1/membership/status`。
4. 根据本次在线响应确定 `TIMETABLE` 是否参与同步。
5. 非会员只同步 `MOBILE_SETTINGS`、`WEAR_SETTINGS` 与服务端允许的设置/命令域。
6. 会员才允许读取、合并、上传和应用 `TIMETABLE`。
7. 网络失败、签名失败、401 刷新失败或会员响应解析失败时，不执行课表域同步。
8. 失败不得删除本地课表，也不得把“无权同步”解释为本地课表应被清空。

## 7. 幂等与并发
- 每次写入必须带 `Idempotency-Key`。
- `Idempotency-Key` 长度上限 128 字节；超长返回 `400 IDEMPOTENCY_KEY_TOO_LONG`，客户端应生成新的合规 key 后重试。
- 服务端保存最近一段时间的 key，避免客户端重试重复提交。
- 首次写入可使用 `If-Match: "0"`。
- 后续写入使用服务端返回的带引号 ETag 或版本。
- 并发冲突使用：
  - `409 Conflict`
  - 或 `412 Precondition Failed`
- 客户端 CAS 重试次数不超过 3 次，并使用退避。

## 8. 鉴权、会员与签名失败码
- `OFFICIAL_CLOUD_MEMBERSHIP_REQUIRED`
- `OFFICIAL_CLOUD_ACCOUNT_REQUIRED`
- `OFFICIAL_CLOUD_PERMISSION_DENIED`
- `CLIENT_SIGNATURE_INVALID`
- `CLIENT_SIGNATURE_POLICY_MISSING`

客户端收到 access token 的 `401` 后应经 single-flight 刷新一次并重试；`403` 是权限错误，不得当作 token 过期循环刷新。

签名预检或官方云请求返回签名错误时：

```text
签名异常，客户端可能被非法修改，已禁止使用在线功能
```

客户端不得在签名失败后降级为无签名请求。

## 9. Scope 合并规则
- 客户端本地 `syncScopes` 决定参与合并的 Domain。
- 服务端在线权限是更高优先级门禁。
- `syncScopes` 包含 `TIMETABLE` 但在线会员校验未通过时，`TIMETABLE` 仍不得参与同步。
- 关闭的 Scope：
  - 不向远端推送
  - 不用远端覆盖本地
  - 不触发删除传播

## 10. 自动同步频率
- `MANUAL_ONLY`
- `EVERY_15_MIN`
- `EVERY_30_MIN`
- `EVERY_1_HOUR`
- `EVERY_3_HOURS`

说明：
- Android 最小周期按 15 分钟对齐。
- 频率变更后客户端需重建 WorkManager 周期任务。
- 周期任务每次实际执行时仍需重新满足 token、签名和在线会员门禁；调度时间未超过 5 分钟签名缓存时可复用预检结果。

## 11. 限流

`PUT /api/v1/cloud/official/document` 受双维度限流保护：

| 维度 | 限制 | 错误码 |
|---|---|---|
| IP（敏感接口共享） | 60 次/分钟 | `429 IP_RATE_LIMITED` |
| 账户 | 30 次/分钟 | `429 ACCOUNT_RATE_LIMITED` |

429 响应携带 `Retry-After: 60`。正常同步频率（≤ `EVERY_15_MIN`）远低于账户限制；若因并发冲突（409/412）触发自动重试，重试次数应 ≤ 3 并带退避。

签名预检接口位于认证限流组；客户端应复用 5 分钟成功缓存，避免每个子请求重复预检。

## 12. 相关文档

- [API-客户端签名校验与证书注册.md](./API-客户端签名校验与证书注册.md)
- [API-兑换码与会员状态.md](./API-兑换码与会员状态.md)
- [API-Wear二维码登录.md](./API-Wear二维码登录.md)
