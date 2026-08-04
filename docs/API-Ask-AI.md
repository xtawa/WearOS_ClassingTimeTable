# API：Ask AI

> 文档基准：WearOS_ClassingTimeTable `main`，包含已合并 PR #15。  
> Android release 客户端签名契约见 [API-客户端签名校验与证书注册.md](./API-客户端签名校验与证书注册.md)。

## 1. 基本规则

- Base URL：`https://api-classing.underflo.ink`
- API 前缀：`/api/v1/ai`
- 所有接口需要有效的 Bearer access token。
- Android Mobile release 客户端调用 Ask AI 前必须通过 `POST /api/v1/client/signature/check`。
- 所有 Ask AI 请求携带 Android 客户端签名头。
- `POST /api/v1/ai/chat` 使用 Server-Sent Events 返回增量内容。
- 新会话的首条消息需要附带课表快照；继续已有会话时可只提交 `conversationId` 与消息。
- 每次聊天请求使用新的 `clientRequestId`，当前 Android 客户端使用 UUID。
- 模型返回的 Markdown 仅作为本地富文本安全渲染，不执行原始 HTML。

## 2. 通用请求头

```http
Authorization: Bearer <accessToken>
Accept: application/json, text/event-stream
X-Classing-Client-Platform: ANDROID_MOBILE
X-Classing-Package-Name: com.xtawa.classingtime
X-Classing-Version-Code: <positive-integer>
X-Classing-Signing-Cert-Sha256: <64-hex-certificate-fingerprint>
```

有 JSON 请求体时同时携带：

```http
Content-Type: application/json; charset=utf-8
```

## 3. 用量查询

### `GET /api/v1/ai/usage/me`

请求体：无。

成功响应可直接返回用量对象，也可包装在 `usage` 字段中：

```json
{
  "usage": {
    "limit": 100,
    "used": 12,
    "reserved": 2,
    "creditBalance": 500,
    "creditAvailable": 480,
    "creditFrozen": false,
    "isMember": true,
    "resetAt": 1785628800000
  }
}
```

字段：

| 字段 | 类型 | 含义 |
|---|---|---|
| `limit` | integer | 当前周期基础调用额度 |
| `used` | integer | 已完成并计入的用量 |
| `reserved` | integer | 正在执行或已预留的用量 |
| `creditBalance` | integer | 账户算力余额 |
| `creditAvailable` | integer | 扣除冻结或预留后的可用余额 |
| `creditFrozen` | boolean | 余额是否被冻结 |
| `isMember` | boolean | 服务端本次返回的会员状态 |
| `resetAt` | integer | 基础额度重置时间，Unix epoch milliseconds |

客户端不得仅使用本地会员缓存推导 Ask AI 用量或权限，应以本接口响应和聊天接口实际结果为准。

## 4. 模型列表

### `GET /api/v1/ai/models`

成功响应：

```json
{
  "defaultModel": "deepseek-v4-flash",
  "models": [
    {
      "id": "deepseek-v4-flash",
      "name": "DeepSeek V4 Flash",
      "description": "快速回答"
    }
  ]
}
```

字段：

- `defaultModel`：服务端默认模型 ID。字段缺失时当前 Android 客户端回退为 `deepseek-v4-flash`。
- `models[].id`：提交聊天请求时使用的模型 ID。
- `models[].name`：界面显示名称。
- `models[].description`：模型说明。

客户端应保留服务端返回的实际模型列表，不应把本地旧列表覆盖到服务端新配置上。

## 5. 会话列表

### `GET /api/v1/ai/conversations?limit=30`

当前 Android 客户端固定请求最近 30 个会话。

成功响应：

```json
{
  "conversations": [
    {
      "conversationId": "aic_...",
      "title": "今天的课程安排",
      "updatedAt": 1785628800000
    }
  ]
}
```

字段：

- `conversationId`：后续读取消息、继续对话或删除会话时使用。
- `title`：服务端生成或保存的会话标题。
- `updatedAt`：最后更新时间，Unix epoch milliseconds。

## 6. 会话消息

### `GET /api/v1/ai/conversations/{conversationId}/messages`

成功响应：

```json
{
  "messages": [
    {
      "messageId": "aim_...",
      "role": "user",
      "content": "我今天有什么课？",
      "createdAt": 1785628800000
    },
    {
      "messageId": "aim_...",
      "role": "assistant",
      "content": "你今天有……",
      "createdAt": 1785628801000
    }
  ]
}
```

字段：

- `messageId`：消息 ID。
- `role`：通常为 `user` 或 `assistant`。
- `content`：完整消息正文。
- `createdAt`：创建时间，Unix epoch milliseconds。

## 7. 流式聊天

### `POST /api/v1/ai/chat`

读取超时：当前 Android 客户端为 200 秒。

### 7.1 新会话请求

```json
{
  "clientRequestId": "7ee65bdf-33f3-4aeb-9af5-226703684fb7",
  "message": "我今天有什么课？",
  "model": "deepseek-v4-flash",
  "timetableSnapshot": {
    "date": "2026-08-04",
    "week": 1,
    "lessons": []
  }
}
```

规则：

- `clientRequestId` 必填且每次请求唯一。
- `message` 必填。
- `model` 使用 `GET /api/v1/ai/models` 返回的 ID。
- 未提供 `conversationId` 时，当前 Android 客户端要求 `timetableSnapshot` 非空。
- 课表快照只提交完成当前回答所需的数据，不应包含 access token、refresh token、云凭据或 APK 签名材料。

### 7.2 继续会话请求

```json
{
  "clientRequestId": "ca76c536-02a6-4ab6-8ff9-e9e4b9cef8fe",
  "conversationId": "aic_...",
  "message": "第二节课在哪里？",
  "model": "deepseek-v4-flash"
}
```

已有 `conversationId` 时不需要重复提交初始课表快照，除非服务端契约后续明确要求。

### 7.3 SSE 响应

响应头：

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
```

客户端按 `event:` 与 `data:` 行解析。

#### `conversation`

```text
event: conversation
data: {"conversationId":"aic_..."}
```

- 新会话创建后返回实际 `conversationId`。
- 客户端用该值替换本地空会话 ID。

#### `delta`

```text
event: delta
data: {"text":"你今天"}
```

- `text` 是本次增量内容。
- 客户端按到达顺序追加，不应覆盖此前内容。

#### `done`

```text
event: done
data: {"truncated":false}
```

- `truncated=true` 表示响应因服务端限制被截断。
- 客户端可提供续写入口，但续写应作为新的聊天请求并使用新的 `clientRequestId`。

#### `error`

```text
event: error
data: {"code":"AI_PROVIDER_ERROR","message":"upstream request failed"}
```

- 当前 Android 客户端将 SSE `error` 转换为请求失败。
- 收到 `error` 后停止继续拼接正常结果。

## 8. 删除会话

### `DELETE /api/v1/ai/conversations/{conversationId}`

后端签名保护路由包含该接口。

请求头：

```http
Authorization: Bearer <accessToken>
X-Classing-Client-Platform: ANDROID_MOBILE
X-Classing-Package-Name: com.xtawa.classingtime
X-Classing-Version-Code: <versionCode>
X-Classing-Signing-Cert-Sha256: <certSha256>
```

请求体：无。

删除成功后客户端应从本地会话列表移除对应项，并清除当前页面对该会话的选中状态。当前 `AiApiClient` 尚未暴露删除方法；调用端接入时应使用本接口，不得只做本地隐藏。

## 9. 客户端签名失败行为

以下 Ask AI 路由属于后端签名保护范围：

- `POST /api/v1/ai/chat`
- `GET /api/v1/ai/usage/me`
- `GET /api/v1/ai/models`
- `GET /api/v1/ai/conversations`
- `GET /api/v1/ai/conversations/{id}/messages`
- `DELETE /api/v1/ai/conversations/{id}`

签名预检失败或接口返回签名错误时，客户端展示：

```text
签名异常，客户端可能被非法修改，已禁止使用在线功能
```

禁止：

- 改为不发送签名头重试。
- 只依赖本地会员或余额缓存继续聊天。
- 把签名失败显示为模型额度不足。

## 10. 通用错误处理

非 2xx JSON 错误体：

```json
{
  "code": "AI_REQUEST_INVALID",
  "message": "invalid AI request",
  "requestId": "req_..."
}
```

客户端需要区分：

- `401`：会话失效；按账户单飞刷新规则处理。
- `403`：权限或签名错误；不得当作 token 过期循环刷新。
- `429`：读取 `Retry-After` 并建立本地冷却。
- `5xx`：服务端或上游暂时失败；保留用户输入和已有流式正文，允许用户明确重试。
- SSE `error`：使用事件中的 `code` 与 `message`。

客户端不得在日志中记录完整 access token、refresh token、课表敏感正文或签名登记密钥。

## 11. 相关文档

- [API-客户端签名校验与证书注册.md](./API-客户端签名校验与证书注册.md)
- [API-账户会员密码重置.md](./API-账户会员密码重置.md)
- [API-兑换码与会员状态.md](./API-兑换码与会员状态.md)
