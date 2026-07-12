# 客户端影响：限流与 XFF 安全硬化

> 适用版本：classing-backend ≥ 2026-07-11
> 影响端：Android Mobile / Android Wear / Web Admin（登录、注册、验证码、密码重置流程）

## 变更原因

后端 `clientIP()` 原无条件取 `X-Forwarded-For` 首项，且 Nginx 配置使用 `$proxy_add_x_forwarded_for` 保留客户端可伪造的首项。攻击者可轮换伪造 IP 绕过登录、注册验证码、密码重置的限流，并可通过高基数伪造键消耗内存与 CPU。

## 主要变更

1. **可信代理校验**：仅当 `RemoteAddr` 属于 `TRUSTED_PROXIES` 配置的可信 CIDR 时，才解析 `X-Forwarded-For` / `X-Real-IP`。
2. **从右向左剥离可信代理**：`X-Forwarded-For` 按链从右向左剥离可信代理 IP，返回第一个非可信 IP；伪造的最左侧项无法覆盖真实客户端 IP。
3. **Nginx 纵深防御**：部署教程已将 Nginx 的 `X-Forwarded-For` 设为 `$remote_addr`，覆盖客户端传入值。
4. **登录双维度限流**：
   - IP 维度：`/api/v1/auth/*` 统一 20 次/分钟/IP。
   - 标识维度：同一 identifier（邮箱/用户名）5 次登录失败/15 分钟触发锁定。
   - 登录成功后重置失败计数。
5. **验证码/邮箱变更/密码重置失败次数上限**：同一 challenge/request/token 错误 10 次后锁定，后续正确码也无效。
6. **限流器有界化**：内部 map 上限 8192，超过时清理过期项并驱逐最旧 25%，防止高基数伪造键的内存/CPU 消耗。

## 客户端适配要点

- **登录页**：连续 5 次密码错误后，后端返回 `429 AUTH_LOGIN_LOCKED` 与 `Retry-After: 900`。客户端应展示“登录尝试次数过多，请 15 分钟后重试”，并在倒计时期间禁用登录按钮。
- **验证码输入（注册、邮箱变更、密码重置）**：错误输入累计 10 次后，该 challenge/request 锁定。客户端应：
  - 记录当前错误次数并在达到 10 次时提示“验证码已失效，请重新申请”；
  - 不自动无限重试；
  - 锁定后引导用户重新发送验证码或重新发起流程。
- **不要伪造 `X-Forwarded-For`**：客户端不应自行添加或修改该头；直连源站或不可信代理时，限流键为连接源地址。
- **代理/ CDN 场景**：若客户端流量经过 CDN 或多级代理，需由运维将上游网段加入后端 `TRUSTED_PROXIES`，客户端无需额外改动。

## 错误码

| HTTP | code | 触发场景 | 客户端处理 |
|---|---|---|---|
| 429 | `AUTH_LOGIN_LOCKED` | 同一 identifier 登录失败 5 次/15 分钟 | 展示锁定提示，按 `Retry-After` 倒计时 |
| 429 | `AUTH_RATE_LIMITED` | IP 维度 20 次/分钟触发 | 稍后重试 |
| 400 | `AUTH_EMAIL_VERIFICATION_INVALID` | 验证码错误/过期/已锁定 | 增加错误计数，达到 10 次后引导重新申请 |
| 400 | `AUTH_RESET_TOKEN_INVALID` | 重置 token 错误/过期/已锁定 | 引导重新申请密码重置 |
| 400 | `ACCOUNT_EMAIL_VERIFICATION_INVALID` | 邮箱变更验证码错误/过期/已锁定 | 同注册验证码 |

## 相关文件

- 后端实现：`internal/httpapi/helpers.go`、`internal/httpapi/middleware.go`、`internal/httpapi/handlers_auth.go`、`internal/store/users.go`
- 部署配置：`docs/部署教程-Linux.md`
