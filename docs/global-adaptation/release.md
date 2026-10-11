# Global 独立构建与上线方案

`market` 维度保留 `cn` / `global`。手机与 Wear 同一 market 使用相同 applicationId，国内为 `com.xtawa.classingtime`，国际为 `com.xtawa.classingtime.global`；避免交叉配对和账号市场混用。Global 标签与版本后缀保持 preview。新增 `global-validation.yml` 跑独立测试、lint、Release APK/AAB 构建并保存产物，没有推送或发布。

本地命令（JDK 17+，已有 SDK）：

```powershell
.\gradlew.bat :shared:test :mobile:testGlobalDebugUnitTest :app:testGlobalDebugUnitTest
.\gradlew.bat :mobile:lintGlobalRelease :app:lintGlobalRelease
.\gradlew.bat :mobile:assembleGlobalRelease :app:assembleGlobalRelease
.\gradlew.bat :mobile:bundleGlobalRelease :app:bundleGlobalRelease
```

签名：构建脚本沿用 `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`，放本机私密配置或 CI secret，不写入仓库/聊天。无签名配置时产物仅供构建审查。Global 与 CN 当前复用签名配置槽位；上线若采用独立 Play App Signing，须单独配置 Global upload key、服务器的 package/certificate 允许项，再验证手机/Wear配对。现有本地签名不是 Play 测试购买凭证。

Play 发布前具体工作：建立独立应用及 Wear form factor、上传 AAB、配置 App Signing、隐私政策与 Data Safety、截图/商店文案、内部测试名单、产品/基础方案/优惠、服务端 RTDN 和购买验证。当前默认 API/Global AI 门禁需由发行配置明确启用；运行下列购买验收前禁止宣称正式上线。

Billing 实施合同（尚未添加 Billing 库或支付 UI）：

1. 按 [Google Play Billing 官方集成文档](https://developer.android.com/google/play/billing/integrate) 接入当前受支持版本；连接 BillingClient、查询 ProductDetails，使用平台返回价格及 offer token。
2. 用户明确选择付费功能后启动购买；`PENDING` 不授予权益。成功时将 purchase token、package/product 信息送到认证后端，由 Google Play Developer API 验证所属应用、状态、有效期与重复 token。
3. 后端幂等保存权益与购买记录；验证后按合同及时 acknowledge，RTDN 拉取真实状态后处理取消、宽限期、暂停、过期、退款/撤销。客户端缓存必须服从服务器到期时间。
4. 恢复购买与换机从 `queryPurchasesAsync` 加服务器权益恢复，不要求重复购买；account market 不交叉迁移，匿名购买的账户绑定冲突需明确提示。
5. 移除付款入口内的外链支付文案，核对 Play 政策；文案明确自动续费、周期、取消与恢复，不用伪造价格。

内部测试记录：普通成功、取消、pending→成功、重复回调、ack重试、断网恢复、重复安装、换机恢复、退款撤权、过期、市场不匹配、同token不同用户。每项记录产品ID、匿名案例编号、服务器验证/ack状态与结果，不记录原始token。本轮没有可用 Play 测试环境，这些项目未执行。
