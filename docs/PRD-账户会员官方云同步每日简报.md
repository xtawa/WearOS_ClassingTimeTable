# PRD：账户、会员、官方云同步、每日简报

> 文档基准：WearOS_ClassingTimeTable `main`，包含已合并 PR #15。  
> 客户端签名后端配套：classing_backend PR #6。

## 1. 目标
- 为 `mobile` 提供完整账号体系入口：登录、邮箱验证注册、退出、密码重置、账号注销、会员状态、兑换码。
- 为 `mobile` 提供官方云同步 `OFFICIAL`，固定基址 `https://api-classing.underflo.ink`；登录账户同步设置，有效会员额外同步课表。
- 将移动端第二主页面定义为 `Dashboard`，统一承接今日概览、下一节课、热力图与 Ask AI 入口。
- 为 `mobile` 提供每日简报设置，支持系统通知、邮件、双通道。
- `wear` 默认展示手机下发的账号与云同步摘要；未能自动获得登录状态时，可通过短时二维码由已登录 Mobile 批准并持有独立加密会话。
- Mobile 与 Wear release APK 启用 R8 混淆和资源压缩。
- Android release 客户端在线功能使用 APK 签名证书 SHA-256 进行服务端预检和请求门禁。
- 会员限定官方云课表同步使用服务端实时会员状态，不信任可修改的本地持久化会员布尔值。

## 2. 范围

### 2.1 客户端内实现

#### Mobile
- 设置页：`Account`、`Daily Briefing`、`Cloud Sync`、Ask AI。
- 账户：登录、邮箱验证注册、刷新、退出、密码重置、资料读取、邮箱两步变更、账号注销、会员刷新、兑换码。
- 云同步：`OFFICIAL`、同步项目、官方频率、SSE 前台事件、WorkManager 后台兜底。
- Dashboard：
  - 今日总课数
  - 今日剩余课时
  - 下一节课
  - 当前状态
  - 热力图
  - Ask AI 入口
- release 在线请求：计算安装包签名证书 SHA-256，执行签名预检，注入签名头。
- 会员限定同步：执行前在线请求 `/membership/status`。

#### Wear
- About 页面与版本信息。
- 手机下发账号、会员、Provider 与官方云状态摘要。
- 未登录时二维码设备授权入口。
- 独立会话加密存储、token 单飞刷新和官方云 `wear.settings` 直连同步。
- release 在线请求签名预检与签名头。

#### CI
- 构建、验证并上传 Mobile/Wear release APK。
- 从 APK 提取包名、versionCode 与签名证书 SHA-256。
- 使用受保护的 workflow token 向后端登记可信证书。

### 2.2 后端配套
- 账户、会员、兑换码、密码重置、官方云、每日简报、Ask AI 和设备二维码接口。
- 可信 Android 客户端签名证书持久化。
- `POST /api/v1/client/signature/check`。
- `POST /api/v1/client/signing-certificates/register`。
- `CLIENT_SIGNATURE_REQUIRED` 策略。
- release workflow 登记 token。

### 2.3 仓库内不实现
- 不在 Android 仓库实现后端服务代码。
- 不在 Android 仓库实现兑换码后台管理界面。
- 不在 Android 仓库实现 SMTP 实际发送集群。
- 不把普通 HTTP 签名头宣称为平台级不可伪造证明；Play Integrity 等平台证明属于后续增强。

## 3. 角色与设备分工

### Mobile
- 主控端，持有账号令牌。
- 负责账号接口、会员实时刷新、课表 V2 合并、官方云课表同步、每日简报本地通知调度。
- 把可公开的账号/会员/云状态摘要和合并后的课表通过 Data Layer 下发 Wear。

### Wear
- 课程与大部分状态以消费 Mobile 快照为主。
- 自动账号状态不可用时发起二维码设备授权。
- 独立会话只直连账号刷新和官方云 `wear.settings` 等必要在线功能。
- token、pollSecret、签名信任缓存不进入 Data Layer 或云文档。

### Backend
- 负责鉴权、会员、兑换码、密码重置、设备授权、官方云文档、SSE、每日简报邮件订阅、Ask AI、签名证书登记与签名门禁。

### GitHub Actions
- 使用 release keystore 构建 APK。
- 验证 APK 签名并上传 artifact。
- 用 `CLASSING_API_BASE_URL` 与 `CLASSING_SIGNATURE_REGISTER_TOKEN` 登记证书。

## 4. 核心规则

### 4.1 账户
- 登录标识为 `identifier`，支持邮箱或用户名。
- 注册必须包含 `username`、`email`、`password`，并完成邮箱验证码确认。
- 登录、注册申请与注册确认必须有三项协议同意状态。
- 密码重置始终走邮箱。
- 邮箱变更必须校验当前密码并通过新邮箱验证码确认；成功后撤销全部会话。
- 账号注销要求当前密码、确认文本和二次确认。

### 4.2 官方云
- `OFFICIAL` 固定基址，不支持用户修改。
- 任意时刻仅允许启用一种云同步 Provider。
- 同步项目仅限：
  - `TIMETABLE`
  - `MOBILE_SETTINGS`
  - `WEAR_SETTINGS`
- 账号令牌、会员缓存、WebDAV 密码、Drive Token、APK 签名材料不进入云同步文档。
- 登录账户默认同步设置域；有效会员可同步 `TIMETABLE`。
- 会员过期不删除本地课表；只停止课表域在线同步。

### 4.3 会员在线门禁
- 本地会员摘要可用于界面占位和离线展示。
- 任何可能读取、上传、应用课表域的官方云同步执行前，都必须使用当前 access token 调用 `GET /api/v1/membership/status`。
- 在线结果 `isMember=false` 时只同步设置域。
- 在线查询、token 刷新、签名预检或解析失败时，课表域 fail-closed。
- 兑换成功后立即刷新在线会员状态，无需重启解锁。

### 4.4 客户端签名完整性
- release 客户端从安装包签名证书计算 SHA-256，不使用每次构建都会变化的整包 APK SHA-256 作为长期信任锚。
- 在线功能前调用 `POST /api/v1/client/signature/check`。
- 后续在线请求携带平台、包名、versionCode 和证书 SHA-256。
- 同一安装快照的成功预检可缓存 5 分钟。
- 预检失败后禁止在线功能，不降级为无签名请求。
- Debug 构建跳过客户端签名门禁。
- HTTP 头可被完全修改的客户端伪造；后续需要平台级证明时接入 Play Integrity 或等价方案。

### 4.5 每日简报
- `APP_NOTIFICATION` 由客户端本地调度。
- `EMAIL` 或 `BOTH` 时提交后端订阅。
- 后端负责任务调度、SMTP 投递、邮箱池切换与幂等。

## 5. 主要流程

### 5.1 登录/注册
1. 客户端读取 `/auth/registration/config` 与协议 URL。
2. release 客户端对提交类在线请求执行签名预检。
3. 用户完成登录或邮箱验证注册。
4. 客户端保存 access/refresh token。
5. 请求 `/account/me` 与 `/membership/status`。
6. 更新本地账号摘要、会员摘要与 Wear 只读快照。

### 5.2 兑换码升级会员
1. 用户输入兑换码。
2. release 客户端签名预检通过。
3. 调用 `POST /api/v1/membership/redeem`。
4. 后端原子校验、扣减、升级和审计。
5. 客户端保存响应摘要，并再次请求 `/membership/status`。
6. 在线结果有效后解锁 `TIMETABLE` 官方云同步。

### 5.3 官方云同步
1. 确认当前 Provider 与登录会话。
2. release 构建完成签名预检。
3. 刷新 access token（如需）。
4. 实时请求 `/membership/status`。
5. 根据在线权益构造允许参与的 Scope。
6. 拉取文档、执行 V2 合并、按 ETag 和幂等键写回。
7. 非会员或会员校验失败时仅处理设置域。

### 5.4 Wear 二维码登录
1. Wear release 预检签名并调用 `/auth/device/qr/start`。
2. Wear 展示只含 `authorizationId` 的二维码。
3. Mobile 登录后扫码，签名预检并调用 `/approve`。
4. Wear 使用本地 `pollSecret` 调用 `/poll`。
5. 首次成功兑换独立 session。
6. Wear 立即签名预检并同步 `wear.settings`。

### 5.5 CI 证书登记
1. 构建 Mobile/Wear release APK。
2. 用 `aapt` 提取包名和 versionCode。
3. 用 `apksigner --print-certs` 提取证书 SHA-256。
4. 上传 APK artifact。
5. 用 `X-Classing-Release-Key` 调用 `/client/signing-certificates/register`。

### 5.6 每日简报
1. 用户设置 `enabled`、`channel`、`time`。
2. `APP_NOTIFICATION` 更新本地调度。
3. `EMAIL` / `BOTH` 先完成签名预检，再提交后端。
4. 后端生成幂等任务并投递。

## 6. 配置

前端 GitHub Secrets：

```text
CLASSING_API_BASE_URL=https://api-classing.underflo.ink
CLASSING_SIGNATURE_REGISTER_TOKEN=<随机登记令牌>
```

后端：

```text
RELEASE_PACKAGE_NAME=com.xtawa.classingtime
RELEASE_SIGNING_CERT_SHA256=<当前稳定证书指纹>
RELEASE_SIGNING_REGISTER_TOKEN=<至少16字符，与前端secret一致>
CLIENT_SIGNATURE_REQUIRED=false
```

## 7. 验收标准

### 账户与会员
- 非会员登录后可同步设置但不可同步课表。
- 本地篡改 `isMember=true` 不能解锁官方云课表同步。
- 兑换成功后无需重启即可通过在线会员校验解锁。
- 邮箱确认成功后所有本地 token 被清除并要求重新登录。

### 签名完整性
- 官方 release APK 可通过签名预检。
- 重新签名 APK 被拒绝，在线功能显示统一签名异常文案。
- Android 在线请求包含四个签名头。
- CI 从实际 artifact 提取证书并成功登记。
- 后端未配置签名策略时返回明确错误，客户端不降级绕过。
- 同源 Web 管理台在 `CLIENT_SIGNATURE_REQUIRED=false` 时保持可用。

### 官方云
- 会员状态在线查询失败时不上传或应用课表域。
- 非会员仍同步设置域。
- 本地课表不会因会员失效或在线校验失败被删除。
- SSE 只通知版本，正文通过文档 GET 拉取。

### Wear
- 手机快照可正常展示登录/会员/云状态。
- 无手机账号摘要时可完成二维码登录。
- 二维码不包含 `pollSecret` 或 token。
- Wear 独立 session 与签名校验通过后可同步 `wear.settings`。

### 构建
- Mobile/Wear release 启用 R8 与资源压缩。
- release APK 可被签名验证，CI artifact 可下载。

## 8. 接口文档

- [API-客户端签名校验与证书注册.md](./API-客户端签名校验与证书注册.md)
- [API-Ask-AI.md](./API-Ask-AI.md)
- [API-账户会员密码重置.md](./API-账户会员密码重置.md)
- [API-兑换码与会员状态.md](./API-兑换码与会员状态.md)
- [API-邮箱变更安全流程.md](./API-邮箱变更安全流程.md)
- [API-Wear二维码登录.md](./API-Wear二维码登录.md)
- [API-官方云同步与同步项目.md](./API-官方云同步与同步项目.md)
- [API-每日简报与邮件集群投递.md](./API-每日简报与邮件集群投递.md)
