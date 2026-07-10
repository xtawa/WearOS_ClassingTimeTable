# API：兑换码与会员状态

## 1. 客户端接口

### `GET /api/v1/membership/status`
请求头：
- `Authorization: Bearer <accessToken>`

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

### `POST /api/v1/membership/redeem`
请求头：
- `Authorization: Bearer <accessToken>`

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

## 5. 会员状态缓存策略
- 客户端缓存摘要：
  - `isMember`
  - `tier`
  - `expiresAt`
  - `lastCheckedAt`
- 刷新时机：
  - App 启动
  - 登录成功
  - 兑换成功
  - 用户手动刷新

## 6. 错误码建议
- `MEMBERSHIP_REDEEM_CODE_INVALID`
- `MEMBERSHIP_REDEEM_CODE_EXPIRED`
- `MEMBERSHIP_REDEEM_CODE_REVOKED`
- `MEMBERSHIP_REDEEM_QUOTA_EXHAUSTED`
- `MEMBERSHIP_REDEEM_USER_LIMIT_REACHED`
