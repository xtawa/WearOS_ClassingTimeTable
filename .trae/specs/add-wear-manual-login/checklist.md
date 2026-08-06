# Checklist

- [x] `WearQrAuthApiClient.login(identifier, password)` 调用 `POST /api/v1/auth/login`，body 含 `{identifier, password, consent}`。
- [x] `WearQrAuthApiClient` 在 release 构建下对登录/刷新请求完成 `ClientIntegrity.ensureTrusted` 预检并注入 `ANDROID_WEAR` 签名头；DEBUG 跳过。
- [x] 登录返回的 `session` 正确解析为 `WearDirectAccountSession`（accessToken/refreshToken/accessExpiresAt/refreshExpiresAt/userId/username/isMember/membershipTier）。
- [x] 登录成功后会话写入 `WearDirectAccountStore`，`WearDirectAccountSessionManager` 后续刷新/登出逻辑无需改动即可复用。
- [x] `CloudSyncScreen` 未登录态展示「账号登录」入口，提供 identifier/password 输入与提交。
- [x] 输入校验：identifier 非空、密码 ≥ 8，校验不通过时提交按钮禁用。
- [x] 登录成功后自动触发官方云同步，状态文案与 QR 登录一致。
- [x] 登录失败（401/签名异常/网络错误）展示对应错误且不写入会话，保留输入可重试。
- [x] en / zh-rCN / zh-rTW 三语字符串均已新增且无硬编码中文。
- [x] `WearOfficialCloudSyncCoordinator` 构造 `WearQrAuthApiClient` 时传入 context，刷新签名头生效。
- [x] 新增单元测试覆盖 login 成功解析与失败异常、输入校验边界，全部通过。
