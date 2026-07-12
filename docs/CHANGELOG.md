# Classing 客户端变更日志

## 2026-07-11

### 安全加固：邮箱变更两步验证

**影响端：** Android Mobile、Android Wear、Web Admin

**后端版本要求：** classing-backend ≥ 2026-07-11

**变更原因：**
修复高危账户接管漏洞。旧版 `PATCH /api/v1/account/me` 可直接修改恢复邮箱，被盗 access token 的攻击者可将邮箱改为自己的地址，再通过密码重置接管账户。

**主要变更：**

- `PATCH /api/v1/account/me` 修改邮箱时要求提供当前密码（`currentPassword`）。
- 新邮箱不再立即生效，进入 `pending` 状态，后端向新邮箱发送 6 位验证码。
- 新增 `POST /api/v1/account/email/confirm` 接口用于确认新邮箱。
- 确认完成后，所有会话被撤销（`auth_epoch` 提升 + refresh token 全部作废），用户必须重新登录。
- 确认完成前，登录与密码重置仍使用旧邮箱。
- 旧邮箱会收到变更通知邮件。
- `GET /api/v1/account/me` 新增 `pendingEmailChange` 字段，用于展示待验证邮箱。

**客户端适配要点：**

- 账户设置页修改邮箱时，需增加「当前密码」输入框。
- 提交后进入验证码确认页，保存并使用 `emailChange.requestId`。
- 确认成功后必须清空本地 token 并跳转登录页。
- 账户页需展示 pending 邮箱及倒计时。

**详细文档：**

- [API-邮箱变更安全流程.md](./API-邮箱变更安全流程.md)

---

## 2026-07-11

### 安全加固：IP 代理与限流硬化

**影响端：** Android Mobile、Android Wear、Web Admin

**后端版本要求：** classing-backend ≥ 2026-07-11

**变更原因：**
修复 X-Forwarded-For 伪造绕过限流漏洞。旧版 `clientIP()` 无条件信任 XFF 首项，攻击者可轮换伪造 IP 绕过登录、验证码与密码重置限流，并可通过高基数伪造键消耗服务端资源。

**主要变更：**

- 仅当 `RemoteAddr` 属于 `TRUSTED_PROXIES` 可信 CIDR 时才解析 `X-Forwarded-For` / `X-Real-IP`。
- `X-Forwarded-For` 从右向左剥离可信代理，伪造的最左侧项无法改变限流键。
- Nginx 配置使用 `$remote_addr` 覆盖客户端 XFF。
- 登录新增 identifier 维度限流：5 次失败/15 分钟。
- 验证码/邮箱变更/密码重置 10 次错误后锁定该 challenge/request/token。
- 限流器 map 上限 8192，超过时清理过期并驱逐最旧 25%。

**客户端适配要点：**

- 登录页处理 `429 AUTH_LOGIN_LOCKED`，按 `Retry-After` 倒计时。
- 验证码输入累计 10 次错误后需引导用户重新申请。
- 客户端不应伪造 `X-Forwarded-For`。
- 如经 CDN/多级代理，需将上游网段加入后端 `TRUSTED_PROXIES`。

**详细文档：**

- [客户端影响-限流与XFF安全硬化.md](./客户端影响-限流与XFF安全硬化.md)

---

## 模板（后续新增条目时请按此格式填写）

### YYYY-MM-DD

#### 标题

**影响端：**

**后端版本要求：**

**变更原因：**

**主要变更：**

**客户端适配要点：**

**详细文档：**
