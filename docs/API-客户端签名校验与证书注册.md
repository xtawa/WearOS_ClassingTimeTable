# API：客户端签名校验与证书注册

> 前端基准：WearOS_ClassingTimeTable PR #15（已于 2026-08-04 合并）  
> 后端基准：classing_backend PR #6（合并并部署后生效）

## 1. 适用范围

Android Mobile 与 Android Wear 的 release 构建在使用 Classing 在线功能前，必须读取当前安装包的签名证书 SHA-256 指纹，向服务端进行可信客户端预检，并在后续在线请求中携带客户端签名元数据。

Debug 构建跳过签名预检与签名请求头注入。

客户端平台值：

- Mobile：`ANDROID_MOBILE`
- Wear：`ANDROID_WEAR`

当前 release 包名：

```text
com.xtawa.classingtime
```

## 2. Android 在线请求签名头

release 客户端的受保护在线请求必须携带：

```http
X-Classing-Client-Platform: ANDROID_MOBILE
X-Classing-Package-Name: com.xtawa.classingtime
X-Classing-Version-Code: 123
X-Classing-Signing-Cert-Sha256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

字段规则：

| 请求头 | 规则 |
|---|---|
| `X-Classing-Client-Platform` | `ANDROID_MOBILE` 或 `ANDROID_WEAR` |
| `X-Classing-Package-Name` | 必须等于后端 `RELEASE_PACKAGE_NAME` |
| `X-Classing-Version-Code` | 大于 0 的十进制整数 |
| `X-Classing-Signing-Cert-Sha256` | APK 签名证书的 SHA-256 指纹；标准化后必须为 64 位十六进制小写字符串 |

证书指纹允许输入冒号、空格和大小写差异，服务端会移除分隔符并转为小写后校验。

## 3. 客户端签名预检

### `POST /api/v1/client/signature/check`

用途：在客户端执行登录、会员、Ask AI、官方云等在线操作之前，验证当前 APK 的包名、平台和签名证书是否受信任。

请求头：

```http
Accept: application/json
X-Classing-Client-Platform: ANDROID_MOBILE
X-Classing-Package-Name: com.xtawa.classingtime
X-Classing-Version-Code: 123
X-Classing-Signing-Cert-Sha256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

无请求体。

成功响应：

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
{
  "trusted": true,
  "packageName": "com.xtawa.classingtime",
  "platform": "ANDROID_MOBILE",
  "versionCode": 123
}
```

客户端规则：

1. release 构建读取安装包签名证书并计算 SHA-256。
2. 首次在线操作前调用预检接口。
3. 同一 `baseUrl + packageName + platform + versionCode + signingCertSha256` 的成功结果可在进程内缓存 5 分钟。
4. 缓存过期后重新预检。
5. 预检失败时禁止继续调用在线功能，并展示：`签名异常，客户端可能被非法修改，已禁止使用在线功能`。
6. 预检失败不得降级为仅依赖本地会员缓存或跳过服务端校验。

错误响应：

| HTTP | code | 含义 |
|---|---|---|
| 403 | `CLIENT_SIGNATURE_INVALID` | 签名头缺失、格式无效、包名不匹配或证书不在可信列表 |
| 503 | `CLIENT_SIGNATURE_POLICY_MISSING` | 后端未配置任何可信 release 证书 |
| 500 | `INTERNAL_ERROR` | 查询签名策略或可信证书记录失败 |
| 429 | `AUTH_RATE_LIMITED` | 签名预检请求超过认证接口限流 |

统一错误体：

```json
{
  "code": "CLIENT_SIGNATURE_INVALID",
  "message": "client signing certificate is not trusted",
  "requestId": "req_..."
}
```

## 4. CI 证书注册

### `POST /api/v1/client/signing-certificates/register`

用途：GitHub Actions 在构建 release APK 后，使用构建产物中提取的签名证书指纹登记可信客户端。

该接口仅供受信任的 release workflow 调用，不应由 Android 客户端调用。

请求头：

```http
Content-Type: application/json
X-Classing-Release-Key: <CLASSING_SIGNATURE_REGISTER_TOKEN>
```

请求体：

```json
{
  "certificates": [
    {
      "packageName": "com.xtawa.classingtime",
      "platform": "ANDROID_MOBILE",
      "versionCode": 123,
      "certSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "artifactName": "mobile-release.apk"
    },
    {
      "packageName": "com.xtawa.classingtime",
      "platform": "ANDROID_WEAR",
      "versionCode": 123,
      "certSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "artifactName": "app-release.apk"
    }
  ]
}
```

约束：

- `certificates` 必须包含 1 至 20 项。
- `packageName` 为空时后端可使用 `RELEASE_PACKAGE_NAME`；非空时必须与配置一致。
- `platform` 必须能标准化为 `ANDROID_MOBILE` 或 `ANDROID_WEAR`。
- `versionCode` 必须大于 0。
- `certSha256` 标准化后必须为 64 位十六进制字符串。
- `artifactName` 保存构建产物文件名，用于追踪登记来源。
- 同一可信记录使用 upsert 写入，重复登记同一证书不会创建不可控重复项。

成功响应：

```json
{
  "registered": [
    {
      "packageName": "com.xtawa.classingtime",
      "platform": "ANDROID_MOBILE",
      "versionCode": 123,
      "certSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "artifactName": "mobile-release.apk"
    }
  ],
  "count": 1
}
```

错误响应：

| HTTP | code | 含义 |
|---|---|---|
| 401 | `CLIENT_SIGNATURE_REGISTER_KEY_INVALID` | `X-Classing-Release-Key` 缺失或与后端令牌不一致 |
| 400 | `CLIENT_SIGNATURE_REQUEST_INVALID` | `certificates` 为空或超过 20 项，或 JSON 无效 |
| 400 | `CLIENT_SIGNATURE_CERT_INVALID` | `certSha256` 不是有效 SHA-256 证书指纹 |
| 400 | `CLIENT_SIGNATURE_PLATFORM_INVALID` | 平台值无效 |
| 400 | `CLIENT_SIGNATURE_PACKAGE_INVALID` | 包名与后端 release 包名不一致 |
| 400 | `CLIENT_SIGNATURE_VERSION_INVALID` | `versionCode` 不是正整数 |
| 503 | `CLIENT_SIGNATURE_REGISTRATION_DISABLED` | 后端未配置登记令牌 |
| 429 | `AUTH_RATE_LIMITED` | 登记请求超过认证接口限流 |

## 5. GitHub Actions 登记流程

PR #15 的 Android CI 在 release 构建后执行：

1. 上传 `mobile/build/outputs/apk/release/*.apk` 与 `app/build/outputs/apk/release/*.apk` 到 `release-apks` artifact。
2. 使用 Android Build Tools 中的 `aapt dump badging` 提取 `packageName` 与 `versionCode`。
3. 使用 `apksigner verify --print-certs` 提取 Signer #1 证书 SHA-256。
4. 移除冒号和空白，转为小写。
5. Mobile 产物登记为 `ANDROID_MOBILE`，Wear 产物登记为 `ANDROID_WEAR`。
6. 将所有产物合并为一次注册请求。

前端仓库 Secrets：

```text
CLASSING_API_BASE_URL=https://api-classing.underflo.ink
CLASSING_SIGNATURE_REGISTER_TOKEN=<与后端相同的随机令牌>
```

后端环境变量：

```text
RELEASE_PACKAGE_NAME=com.xtawa.classingtime
RELEASE_SIGNING_CERT_SHA256=<当前稳定 release 证书 SHA-256，可配置多个>
RELEASE_SIGNING_REGISTER_TOKEN=<至少 16 个字符的随机令牌>
CLIENT_SIGNATURE_REQUIRED=false
```

`CLASSING_SIGNATURE_REGISTER_TOKEN` 也可作为后端登记令牌的兼容环境变量，但部署时应优先使用 `RELEASE_SIGNING_REGISTER_TOKEN`。

## 6. `CLIENT_SIGNATURE_REQUIRED` 行为

### `false`

- Android release 客户端仍会主动预检并发送四个签名头。
- 未携带任何签名头的同源 Web 管理台请求可继续访问受保护接口。
- 一旦请求携带任意签名头，服务端会要求四个头完整且可信；部分签名头不能绕过校验。

### `true`

- 所有套用 `requireTrustedClient` 的路由都必须携带完整、可信的签名头。
- 该模式不适合与无签名头的同源 Web 管理台共用同一组受保护路由，除非 Web 与 Android API 已拆分。

## 7. 当前受保护路由

后端 PR #6 将签名中间件用于：

### 认证与设备授权

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/register/email/request`
- `POST /api/v1/auth/register/email/confirm`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/device/qr/start`
- `POST /api/v1/auth/device/qr/poll`
- `POST /api/v1/auth/device/qr/approve`
- `POST /api/v1/auth/device/qr/status`

### 账户与会员

- `GET /api/v1/account/me`
- `PATCH /api/v1/account/me`
- `POST /api/v1/account/email/confirm`
- `PUT /api/v1/account/password`
- `POST /api/v1/account/delete`
- `GET /api/v1/membership/status`
- `POST /api/v1/membership/redeem`

### Ask AI

- `POST /api/v1/ai/chat`
- `GET /api/v1/ai/usage/me`
- `GET /api/v1/ai/models`
- `GET /api/v1/ai/conversations`
- `GET /api/v1/ai/conversations/{id}/messages`
- `DELETE /api/v1/ai/conversations/{id}`

### 课表与官方云

- `GET /api/v1/timetables`
- `POST /api/v1/timetables`
- `GET /api/v1/timetables/{id}`
- `PUT /api/v1/timetables/{id}`
- `DELETE /api/v1/timetables/{id}`
- `GET /api/v1/cloud/official/ping`
- `POST /api/v1/cloud/official/test`
- `GET /api/v1/cloud/official/config`
- `GET /api/v1/cloud/official/document`
- `PUT /api/v1/cloud/official/document`
- `GET /api/v1/cloud/official/events`

### 每日简报

- `GET /api/v1/briefings/daily`
- `PUT /api/v1/briefings/daily`
- `DELETE /api/v1/briefings/daily`
- `POST /api/v1/briefings/daily/test`

以下接口当前未套用 `requireTrustedClient`：

- `GET /api/v1/auth/registration/config`
- `POST /api/v1/auth/password/reset/request`
- `POST /api/v1/auth/password/reset/confirm`
- `POST /api/v1/auth/logout`
- `GET /api/v1/client/announcements`
- `GET /api/v1/client/releases/latest`
- `GET /api/v1/client/releases/{id}/download`

Android PR #15 的 `AccountApiClient` 除注册安全配置接口外，会主动先执行签名预检并注入签名头；因此 Android 调用密码重置与登出时仍会带签名信息，即使后端中间件当前未强制要求。

## 8. 可信证书来源

服务端按以下来源判断证书可信：

1. `RELEASE_SIGNING_CERT_SHA256` 中静态配置的证书指纹。
2. release workflow 写入 `trusted_client_signatures` 的可信记录。

只要 release keystore 不变，签名证书 SHA-256 通常不会随应用版本变化。APK 文件整体 SHA-256 会随每次构建变化，不应用作长期 release 信任锚。

## 9. 安全边界

该机制可识别普通的重新打包与重新签名 APK。客户端签名元数据通过普通 HTTP 请求头传输，完全修改后的恶意客户端仍可能伪造请求头；需要更强的设备与应用真实性证明时，应增加 Play Integrity 或其他平台证明信号，并由服务端校验证明结果。
