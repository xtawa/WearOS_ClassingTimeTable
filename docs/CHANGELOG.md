# Classing 客户端变更日志

## 2026-07-17

### Mobile Ask AI Markdown 富文本

- Mobile Ask AI 回答改为 Markdown 富文本显示，支持标题、段落、列表、引用、代码块、粗体、斜体、删除线和链接样式。
- Markdown 解析保持客户端本地安全展示，不执行模型返回的原始 HTML。

### 同步诊断与 Wear 提醒保护界面收敛

- Mobile 普通模式下的同步诊断只保留“同步到 Wear”和“立即云同步”；诊断详情、刷新 Wear、测试云连接仅在 Dev mode 显示。
- Mobile 云同步页的“最近同步变更”仅在 Dev mode 显示。
- Wear 将精确闹钟与电池优化白名单整合到单一“提醒保护设置”按钮展开的菜单中。

### Mobile Ask AI 导航修复

- Dashboard 的 Ask AI 入口现在会同步切换到底部设置层并打开 Ask AI 页面，不再只更新子页状态后闪屏。
- 从其他主层打开设置子页时会保留来源层，系统返回导航仍可回到原来的主页面。

### Wear 二维码登录

- Wear 云同步页在未收到手机登录状态且本机无有效会话时提供二维码登录。
- 已登录 Mobile 的账号页可使用 Google Code Scanner 扫描 Classing Wear 二维码，并在二次确认后批准登录。
- Wear 只把公开授权编号放进二维码，轮询密钥仅保存在手表；成功后独立会话使用 Android 加密存储。
- 二维码 5 分钟失效且只能兑换一次；手表可单独退出二维码建立的会话。
- 非会员登录账号的官方云设置同步现在正确显示为可用，课表域仍受会员权限限制。
- 修复二维码登录成功后只保存会话、未触发实际同步的问题：Wear 现在会立即直连官方云同步 `wear.settings`，并覆盖设置变更、手动同步、应用启动与后台周期同步。
- Wear 独立会话新增 access token 单飞刷新与 401 重试；官方云写入使用 ETag、幂等键、最多 3 次 CAS 退避重试，并限制变更日志与设备元数据大小。
- 二维码轮询遇到短暂网络错误时会继续等待至授权过期，不再直接终止；账号切换时会隔离本地云同步基线。
- 详细契约见 [API-Wear二维码登录.md](./API-Wear二维码登录.md)。

## 2026-07-13

### 账号注销、协议确认与会员过期同步规则

- Mobile 登录页和注册页新增三项协议确认，必须勾选《隐私政策》《用户协议》《个人数据跨境传输协议》后才允许登录、发送注册验证码、确认注册验证码或重发验证码。
- 协议链接由后端 `GET /api/v1/auth/registration/config` 下发；缺少链接时登录/注册提交保持禁用，避免用户在未展示协议的情况下继续。
- Mobile 账户页新增账号注销入口，要求当前密码、确认文本和二次确认；成功后清理本地凭据、账户状态和会员缓存并回到未登录状态。
- 官方云同步在会员过期后继续同步设置域，但不再同步课表域；设置项仍可跨 Mobile/Web/Wear 保持一致。
- 服务端统一使用 `cloud-document` SSE 事件和云文档版本游标，客户端收到事件后拉取文档并按 V2 规则合并。

### 后端 Code review 适配：更新通道、会话撤销与可靠重试

- Mobile 关于页新增持久化更新通道选择，默认 Stable，可切换 Beta；latest 请求显式携带 `channel`，响应模型保留实际通道和 `forceUpdate`。
- 公告、更新、下载和账户请求解析 `Retry-After`，服务端 429 会建立本地冷却窗口并报告剩余秒数，避免冷却期间重复请求。
- 官方云首次写入发送 `If-Match: "0"`，后续发送带引号的 ETag 版本；继续使用 128 字节以内 UUID 作为 `Idempotency-Key`。
- 账户刷新遇到旧会话、`AUTH_SESSION_REVOKED`、`AUTH_REFRESH_REVOKED` 或认证必需错误时清理本地凭据并要求重新登录。
- APK 下载先检查缓存分区容量；网络中断保留 `.part` 并使用 Range 续传，完整长度或 SHA-256 不匹配时删除损坏文件，Stable/Beta 共用相同安装流程。
- Mobile 单元测试、Mobile/Wear Kotlin 编译作为回归门禁。

## 2026-07-12

### 安全加固：请求头长度限制

**影响端：** Android Mobile、Android Wear、Web Admin

**后端版本要求：** classing-backend ≥ 2026-07-12

**变更原因：**
`X-Request-ID` 与 `Idempotency-Key` 头无长度限制，攻击者可发送数 MB 的头值导致日志、上下文与 `idempotency_keys` 表膨胀。

**主要变更：**

- `X-Request-ID` 超过 128 字节时，服务端忽略该值并生成新的 `req_` ID；128 字节以内原样回显。对客户端透明。
- `Idempotency-Key` 超过 128 字节时，`PUT /api/v1/cloud/official/document` 返回 `400 IDEMPOTENCY_KEY_TOO_LONG`。
- 128 字节上限覆盖 UUID（36 字符）、nanoid（21 字符）等所有常见标识格式。

**客户端适配要点：**

- 使用 UUID 作为 `Idempotency-Key` 的客户端无需改动。
- 若客户端使用非标准长 key 格式，需确保 `Idempotency-Key` ≤ 128 字节。
- 收到 `400 IDEMPOTENCY_KEY_TOO_LONG` 时应缩短 key 后重试，不应使用相同 key 重试。
- `X-Request-ID` 变更对客户端透明，无需改动。

**详细文档：**

- [API-官方云同步与同步项目.md](./API-官方云同步与同步项目.md)

---

## 2026-07-12

### 安全加固：敏感接口账户维度限流

**影响端：** Android Mobile、Android Wear、Web Admin

**后端版本要求：** classing-backend ≥ 2026-07-12

**变更原因：**
此前兑换、简报测试、官方云写入、密码修改、邮箱变更确认等敏感写接口无任何限流，持有有效 access token 的攻击者可高频调用进行兑换码爆破、邮件轰炸或云文档打满。所有限流器仅有 IP 维度，通过 IP 轮换即可绕过。

**主要变更：**

- 上述敏感写接口新增双维度限流：IP 维度（60 次/分钟/IP，敏感接口共享）与账户维度（5–30 次/分钟/账户，按接口风险分级）。
- 账户维度键为 `user:<userId>`，IP 轮换（代理池 / 设备农场）无法绕过。
- 超限分别返回 `429 IP_RATE_LIMITED` 或 `429 ACCOUNT_RATE_LIMITED`，均携带 `Retry-After: 60` 响应头。
- 公开下载接口 `GET /client/releases/{id}/download` 纳入公共客户端限流（3 次/分钟/IP+路径），与公告/版本查询共享 `CLIENT_RATE_LIMITED` 预算。

**客户端适配要点：**

- 所有受影响接口的 429 响应应读取 `Retry-After` 头并按该值退避，不应立即重试。
- 兑换按钮收到 `ACCOUNT_RATE_LIMITED` 后禁用 60 秒并展示倒计时。
- 简报测试按钮点击后进入 60 秒冷却（该接口限流最严格，5 次/分钟）。
- 下载失败重试应指数退避（30s → 60s → 120s），1 分钟内不超过 3 次。
- 429 不应视为致命错误，不应触发退出登录或崩溃。
- 邮箱变更的 60 秒重发冷却（`ACCOUNT_EMAIL_RATE_LIMITED`）与账户维度限流（`ACCOUNT_RATE_LIMITED`）独立计数，可能先后触发。

**详细文档：**

- [客户端影响-敏感接口限流.md](./客户端影响-敏感接口限流.md)

---

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
