# Google Drive OAuth 配置与排障指南

本文用于配置 Classing 的 Google Drive OAuth 云同步能力（与 WebDAV 二选一），并覆盖常见错误排查与发布前检查。

## 1. 方案说明（先读）

- 云提供方支持：`WebDAV` / `Google Drive OAuth`，互斥选择。
- Google Drive 固定使用 `appDataFolder` 下单文件：默认 `classing_sync.json`。
- 云文档格式沿用：`classing_cloud_sync_v1`。
- 当前为无后端模式：不保存 refresh token，仅使用短期 access token。
- 手表 token 由手机下发；手表端遇到 401/过期会请求手机触发云同步刷新。
- 检测到国行/CN/LE 手表时仅显示风险提示，不阻断开启 Google Drive。

## 2. Google Cloud Console 配置

### 2.1 创建项目并启用 API

1. 打开 [Google Cloud Console](https://console.cloud.google.com/)。
2. 新建或选择项目。
3. 进入 `APIs & Services` -> `Library`。
4. 搜索并启用 `Google Drive API`。

### 2.2 配置 OAuth 同意屏幕

1. 进入 `APIs & Services` -> `OAuth consent screen`。
2. 选择 `External`（个人/公众应用）或按组织策略选择 `Internal`。
3. 填写应用名、支持邮箱、开发者联系方式。
4. 在 Scopes 中加入：
   - `https://www.googleapis.com/auth/drive.appdata`
5. 测试阶段将需要登录的 Google 账号加入 `Test users`。

### 2.3 创建 Android OAuth Client

1. 进入 `APIs & Services` -> `Credentials`。
2. 点击 `Create Credentials` -> `OAuth client ID`。
3. Application type 选择 `Android`。
4. 填写：
   - Package name：`com.xtawa.classingtime`
   - SHA-1：当前签名证书指纹（见下一节）

## 3. 配置 SHA-1（Debug/Release）

### 3.1 Debug SHA-1

在项目根目录执行：

```powershell
./gradlew signingReport
```

在输出中找到 `Variant: debug` 的 `SHA1`，填入 Android OAuth Client。

### 3.2 Release SHA-1

使用发布 keystore 计算 SHA-1（示例）：

```powershell
keytool -list -v -keystore <your-release-keystore.jks>
```

将 release SHA-1 也配置到对应 OAuth Client（或单独创建一个 release client）。

## 4. 项目构建配置（必须）

在仓库根目录 `local.properties` 添加：

```properties
DRIVE_OAUTH_CLIENT_ID=你的_android_oauth_client_id.apps.googleusercontent.com
DRIVE_OAUTH_REDIRECT_SCHEME=
```

说明：

- `DRIVE_OAUTH_CLIENT_ID` 会注入 `BuildConfig.DRIVE_OAUTH_CLIENT_ID`。
- 未配置时，App 内点击 `Connect Drive` 会提示“Google Drive OAuth 未配置”。
- 本项目不支持运行时手输 OAuth 参数，必须通过构建配置注入。

## 5. 应用内授权与同步流程

1. 手机端进入：`设置 -> 云同步`。
2. Provider 选择 `Google Drive`。
3. 点击 `Connect Drive`，完成 Google 账号授权。
4. 看到已连接状态后点击 `保存配置`。
5. 点击 `立即同步` 验证连接与读写。
6. 手表端进入云同步页面，仅显示只读授权状态与同步按钮（Drive 模式下不提供 WebDAV 编辑）。

## 6. 国行/CN/LE 手表提示验证

验证预期：

- 当检测到手表变体为 `CN/LE` 且 provider=Google Drive 时，手机页面显示风险提示。
- 提示文案仅告知“手表端可能无法云同步”，不阻断用户保存和开启 Google Drive。

## 7. 常见问题排查

### 7.1 授权失败（Authorization failed）

优先检查：

- `DRIVE_OAUTH_CLIENT_ID` 是否正确。
- 包名是否为 `com.xtawa.classingtime`。
- SHA-1 是否和当前安装包签名一致（debug/release 不可混用）。
- OAuth consent screen 是否完成配置，当前账号是否在测试用户内（测试阶段）。

### 7.2 `scope` 未生效或无权限

检查：

- OAuth scope 是否包含 `drive.appdata`。
- `Google Drive API` 是否已启用。
- 授权账号是否完成同意流程。

### 7.3 连接测试失败（Google Drive connection failed）

检查：

- 手机网络是否可访问 Google 服务。
- 客户端是否已拿到 access token（页面显示已连接）。
- Drive 文件名是否为空（默认应为 `classing_sync.json`）。

### 7.4 手表端 token 过期/401

预期行为：

- 手表写入“token 过期，已请求手机刷新”状态。
- 手机收到请求后重新触发云同步并下发新短期 token。

排查建议：

- 确认手机与手表 Data Layer 连通。
- 在手机端重新执行 `Connect Drive` + `立即同步`。

### 7.5 SHA-1 不匹配

典型原因：

- debug 包却使用了 release SHA-1（或反之）。
- 更换 keystore 后未更新控制台配置。

## 8. 发布前检查清单

- [ ] Debug/Release SHA-1 均已配置并实际验证。
- [ ] Google Drive API 已启用。
- [ ] OAuth 同意屏幕配置完成，测试账号可授权。
- [ ] `DRIVE_OAUTH_CLIENT_ID` 已在构建环境配置。
- [ ] Provider 切换互斥正确（WebDAV/Drive 字段显隐正确）。
- [ ] 国行/CN/LE 命中时出现非阻断风险提示。
- [ ] 手表 Drive 模式下无本地 WebDAV 编辑入口。
- [ ] token 过期后可由手机刷新恢复。
- [ ] WebDAV 全流程回归通过（保存/测试/手动同步/自动同步）。

## 9. 安全与架构备注（无后端短期 token 模式）

- 当前不保存 refresh token，不引入服务器托管凭据。
- 手机端负责请求/刷新短期 access token。
- 手表端使用手机下发的短期 token 直连拉云。
- 该模式实现成本低，但稳定性与续期体验依赖手机在线与桥接可用性。
- 若后续要增强可靠性，可升级为服务端托管 refresh token 模式。
