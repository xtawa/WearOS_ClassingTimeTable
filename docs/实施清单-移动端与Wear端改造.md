# 实施清单：移动端与 Wear 端改造

> 当前基准：WearOS_ClassingTimeTable `main`，包含已合并 PR #15。

## 已完成方向

### `mobile`
- 账号/会员数据模型与本地持久化字段。
- `AuthCredentialStore` 与账号接口客户端。
- 邮箱验证注册、登录、单飞刷新、退出、密码重置、邮箱两步变更、账号注销。
- Wear 二维码扫描、批准与调试状态查询。
- 官方云同步 Provider、同步项目 `SyncScope`、官方频率 `OfficialSyncFrequency`。
- 官方云 SSE 前台事件与 WorkManager 后台兜底。
- 每日简报配置与本地通知调度。
- Dashboard 下一节课、今日统计、热力图与 Ask AI 入口。
- Ask AI 用量、模型、会话、消息和 SSE 聊天客户端。
- release 构建启用 R8 混淆和资源压缩。
- release 在线功能读取安装包签名证书 SHA-256，执行客户端签名预检并注入签名头。
- 官方云会员限定课表域在同步前实时调用 `/membership/status`，不再信任持久化 `isMember`。
- 关于页更新通道与 APK 下载恢复/校验流程。
- 备份规则排除账号凭据、云凭据与同步身份状态。

### `wear`
- About 页面与导航。
- 手机下发云状态、账号和会员摘要的接收与展示。
- 云同步页二维码登录、独立会话加密存储、token 单飞刷新。
- 独立会话直连官方云同步 `wear.settings`。
- release 构建启用 R8 混淆和资源压缩。
- release 二维码、账号和官方云在线请求执行客户端签名预检并注入签名头。
- 备份规则排除独立账号会话、官方云状态与同步身份。

### `shared`
- 同步 V2 文档、逻辑版本、设备标识、冲突合并与 tombstone。
- ScheduleProjector、倒计时 ticker、热力图模型等共享逻辑。

### GitHub Actions
- Android test、lint、release 构建。
- 上传 Mobile/Wear release APK artifact。
- 使用 `aapt` 与 `apksigner` 提取实际构建产物的包名、versionCode 与签名证书 SHA-256。
- 调用 `/api/v1/client/signing-certificates/register` 登记证书。
- release workflow 验证签名并生成 APK SHA256SUMS；整包 SHA-256 仅用于 artifact 完整性，不作为长期客户端签名信任锚。

## 后端配套状态

### 已有接口依赖
- 账户、会员、兑换码、密码重置、邮箱变更、账号注销。
- 官方云文档、ETag、幂等键、SSE。
- 每日简报订阅和测试发送。
- Wear 二维码设备授权。
- Ask AI 用量、模型、会话、消息与流式聊天。

### PR #15 配套
- `classing_backend` PR #6：
  - `POST /api/v1/client/signature/check`
  - `POST /api/v1/client/signing-certificates/register`
  - `trusted_client_signatures`
  - `RELEASE_SIGNING_REGISTER_TOKEN`
  - `CLIENT_SIGNATURE_REQUIRED`
  - Android 受保护路由签名中间件
- 在后端 PR #6 合并并部署前，PR #15 release 客户端的签名预检无法完成，在线功能会按 fail-closed 失败。

## 必需配置

### 前端 GitHub Secrets

```text
CLASSING_API_BASE_URL=https://api-classing.underflo.ink
CLASSING_SIGNATURE_REGISTER_TOKEN=<与后端一致的随机令牌>
```

release 签名仍需要现有 keystore 配置：

```text
RELEASE_KEYSTORE_BASE64
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

### 后端环境变量

```text
RELEASE_PACKAGE_NAME=com.xtawa.classingtime
RELEASE_SIGNING_CERT_SHA256=<当前稳定release证书SHA-256>
RELEASE_SIGNING_REGISTER_TOKEN=<至少16字符，与GitHub Secret一致>
CLIENT_SIGNATURE_REQUIRED=false
```

## 仍需联合验证

### 构建与 CI
- Mobile/Wear release APK 在 CI 中启用 R8 后可成功构建。
- 资源压缩不会移除运行时通过资源 ID 使用的内容。
- ProGuard/R8 不会破坏 JSON、Room、Compose、协程、WorkManager、SSE 与 Data Layer。
- `release-apks` artifact 同时包含 Mobile 与 Wear APK。
- workflow 能从实际 artifact 提取两端证书并一次性登记。
- PR 事件不登记证书；非 PR 的受信任构建才使用登记 token。

### 签名门禁
- 官方 release Mobile/Wear 通过预检。
- 重新签名 APK 返回 `CLIENT_SIGNATURE_INVALID`。
- 后端未配置可信证书时返回 `CLIENT_SIGNATURE_POLICY_MISSING`。
- 成功预检缓存 5 分钟，缓存键包含 base URL、包名、平台、versionCode 与证书指纹。
- 签名失败显示统一文案并禁止在线功能。
- `CLIENT_SIGNATURE_REQUIRED=false` 时同源 Web 管理台保持可用。
- 任意一个签名头存在但头集合不完整时，后端拒绝请求。

### 会员门禁
- 本地将 `isMember` 改为 `true` 不能解锁课表域同步。
- 会员在线状态为 false 时继续同步设置域，不同步课表域。
- 会员状态查询网络失败、401 刷新失败、签名失败或 JSON 无效时不执行课表域同步。
- fail-closed 不删除本地课表。
- 兑换成功后立即在线刷新并解锁，不需重启。

### 账户与 Wear
- 登录、注册、刷新、退出和账号注销与后端字段一致。
- 邮箱确认成功后全部会话撤销并清理本地凭据。
- Wear 二维码 start/poll 使用 `ANDROID_WEAR`，Mobile approve/status 使用 `ANDROID_MOBILE`。
- 二维码只包含 `authorizationId`，`pollSecret` 只留在 Wear。
- Wear 独立会话建立后立即同步 `wear.settings`。

### Ask AI
- 用量字段与后端响应一致。
- 模型列表使用服务端实际 ID。
- 新会话必须提交课表快照，已有会话不重复提交。
- SSE `conversation`、`delta`、`done`、`error` 事件顺序与断线行为正确。
- `truncated=true` 可发起新的续写请求。

### 官方云
- `If-Match: "0"` 首次写入、带引号 ETag 后续写入。
- `Idempotency-Key` ≤ 128 字节。
- CAS 最多 3 次退避重试。
- SSE 只传版本，正文通过 GET 拉取。
- `syncScopes` 与服务端在线会员门禁共同决定实际同步域。

### 每日简报
- `GET` / `PUT` / `DELETE` / `test` 与服务端字段一致。
- 429 读取 `Retry-After`，测试按钮建立冷却。
- 本地通知不依赖后端签名接口。

## 测试清单

### Mobile
- [ ] Debug 构建跳过客户端签名预检。
- [ ] Release 构建使用官方证书通过预检。
- [ ] 重新签名 release APK 被阻止使用在线功能。
- [ ] 登录后重启应用，凭据和账号状态按预期恢复。
- [ ] 本地会员缓存被篡改时课表域仍保持锁定。
- [ ] 退出登录后官方云在线同步立即锁定。
- [ ] 切换 `syncScopes` 后仅同步选中且有权访问的域。
- [ ] `OFFICIAL` 频率切换后周期任务更新。
- [ ] 每日简报时间到点触发本地通知。
- [ ] Ask AI SSE 中断后保留已接收正文并可明确重试。

### Wear
- [ ] 收到手机状态快照后 About/Settings/Cloud 页面展示正确。
- [ ] 未收到账号状态时显示二维码登录。
- [ ] 二维码 start/poll 请求包含 Wear 签名头。
- [ ] 批准后独立 session 加密保存。
- [ ] 成功登录后立即同步 `wear.settings`。
- [ ] release APK 重新签名后在线功能被阻止。

### CI / Backend
- [ ] 后端登记 token 与前端 Secret 完全一致。
- [ ] 登记 token 少于 16 字符时后端拒绝启动配置。
- [ ] 两个 APK 的证书记录均写入可信表。
- [ ] `CLIENT_SIGNATURE_REQUIRED=false` 的 Web 管理台回归通过。
- [ ] 对注册接口提交超过 20 个证书项返回 400。
- [ ] 证书指纹大小写、冒号和空白标准化后正确匹配。

## 交付边界
- Android 仓库交付客户端实现、CI 与文档。
- 后端实现通过 `classing_backend` 独立仓库交付。
- SMTP 基础设施和管理台不在 Android 仓库实现。
- 普通签名头门禁不等同于 Play Integrity；平台证明属于后续安全增强。
