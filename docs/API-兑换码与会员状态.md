# API：兑换码与会员状态

> 文档基准：WearOS_ClassingTimeTable `main`，包含已合并 PR #15。  
> Android release 客户端签名契约见 [API-客户端签名校验与证书注册.md](./API-客户端签名校验与证书注册.md)。

## 1. 客户端接口

### `GET /api/v1/membership/status`
请求头：
- `Authorization: Bearer <accessToken>`
- Android release 客户端同时携带：
  - `X-Classing-Client-Platform`
  - `X-Classing-Package-Name`
  - `X-Classing-Version-Code`
  - `X-Classing-Signing-Cert-Sha256`

响应：
```json
{
  "membership": {
    "isMember": true,
    "tier": "MONTHLY",
    "expiresAt": 1782470400000,
    "lastCheckedAt": 1780000000000
  }
}
```

Android release 客户端调用本接口前必须先通过：

```http
POST /api/v1/client/signature/check
```

### `POST /api/v1/membership/redeem`
请求头：
- `Authorization: Bearer <accessToken>`
- Android release 客户端同时携带四个签名头。

请求体：
```json
{
  "code": "SUMMER-2026-XXXX"
}
```

响应：
```json
{
  "membership": {
    "isMember": true,
    "tier": "REDEEMED",
    "expiresAt": 1782470400000,
    "lastCheckedAt": 1780000000000
  }
}
```

## 2. 兑换码模型

### `UNIQUE`
- 一码一记录。
- 仅允许核销一次。
- 字段建议：
  - `code`
  - `grantDays`
  - `expiresAt`
  - `redeemedBy`
  - `redeemedAt`
  - `revokedAt`

### `CAMPAIGN`
- 同一码可配置额度。
- 字段建议：
  - `code`
  - `maxRedemptions`
  - `currentRedemptions`
  - `grantDays`
  - `expiresAt`
  - `perUserLimit`
  - `revokedAt`

固定规则：
- `perUserLimit=1`
- 吊销只影响未来核销，不追溯已生效会员。

## 3. 后端原子事务要求
兑换时必须单事务完成：
1. 校验兑换码存在且未过期未吊销。
2. 校验用户是否已达到个人限制。
3. 校验全局额度是否还有余额。
4. 扣减额度或标记已核销。
5. 计算新会员有效期。
6. 写入会员权益变更记录。
7. 写入兑换审计记录。

客户端不得拆成“先验证再升级”的两步流程。

## 4. 管理端接口建议
- `POST /api/v1/admin/redeem-codes/generate`
- `POST /api/v1/admin/redeem-codes/revoke`
- `GET /api/v1/admin/redeem-codes/query`
- `POST /api/v1/admin/membership/revoke`

说明：
- 本仓库不实现这些后台页面。
- `membership/revoke` 与兑换码吊销分离。

## 5. 会员状态缓存与在线授权规则

客户端可缓存展示摘要：
- `isMember`
- `tier`
- `expiresAt`
- `lastCheckedAt`

刷新时机：
- App 启动
- 登录成功
- 兑换成功
- 用户手动刷新
- 进入或执行会员限定在线功能之前

PR #15 后的授权规则：

1. 本地缓存仅用于界面占位和离线展示，不是会员限定在线功能的授权依据。
2. Mobile 官方云同步在允许 `TIMETABLE` 域上传、下载或应用之前，必须通过当前 access token 调用 `GET /api/v1/membership/status`。
3. 在线查询返回 `isMember=false` 时，只允许官方云设置域继续同步，不得上传、下载或应用课表域。
4. 在线查询失败、token 无法刷新、签名预检失败或响应无法解析时，不得沿用本地 `isMember=true` 放行课表同步。
5. 兑换成功后客户端应立即保存响应中的会员摘要，并再次执行在线会员校验后解锁会员限定同步。
6. 会员状态的 `lastCheckedAt` 应取服务端返回值，不应使用本地修改后的时间伪造有效状态。

## 6. Android release 客户端签名要求

以下接口属于后端签名保护路由：

- `GET /api/v1/membership/status`
- `POST /api/v1/membership/redeem`

Android Mobile/Wear release 请求需要完整携带：

```http
X-Classing-Client-Platform: ANDROID_MOBILE
X-Classing-Package-Name: com.xtawa.classingtime
X-Classing-Version-Code: <positive-integer>
X-Classing-Signing-Cert-Sha256: <64-hex-certificate-fingerprint>
```

签名预检失败时，客户端禁止继续会员接口调用和会员限定在线功能，不得只显示警告后继续请求。

## 7. 错误码
- `MEMBERSHIP_REDEEM_CODE_INVALID`
- `MEMBERSHIP_REDEEM_CODE_EXPIRED`
- `MEMBERSHIP_REDEEM_CODE_REVOKED`
- `MEMBERSHIP_REDEEM_QUOTA_EXHAUSTED`
- `MEMBERSHIP_REDEEM_USER_LIMIT_REACHED`
- `IP_RATE_LIMITED` — 同一 IP 对敏感接口的请求超过 60 次/分钟（HTTP 429，携带 `Retry-After: 60`）
- `ACCOUNT_RATE_LIMITED` — 同一账户兑换超过 10 次/分钟（HTTP 429，携带 `Retry-After: 60`）
- `CLIENT_SIGNATURE_INVALID` — Android 客户端签名头无效或证书不受信任
- `CLIENT_SIGNATURE_POLICY_MISSING` — 服务端未配置可信 release 证书

客户端收到签名错误时，应展示：

```text
签名异常，客户端可能被非法修改，已禁止使用在线功能
```
