# Classing · iOS / watchOS

原生 SwiftUI 移植位于本目录。业务逻辑是可在 Windows 运行测试的 Swift Package；iPhone、Apple Watch 和 WidgetKit 使用独立 target。**浏览器预览是布局与交互参照，不是原生 SwiftUI 模拟器。**

## 已实现的功能

| 功能 | iPhone | Apple Watch / WidgetKit |
|---|---|---|
| 今日、下一节课、时间与地点 | 原生界面、倒计时、日/周浏览 | 离线列表、周课表、下一节课 |
| 课程管理 | 新增、编辑、删除、搜索 | 新增、编辑、删除 |
| 重复规则 | 学期周次、单双周、每 N 周、轮换教学日、节假日、指定日期、课程分组/课型 | 保留所有规则并投影；高级规则可用 JSON 或在手机编辑 |
| 调课与补课 | 单日修改、取消、补课、快照 | 单日安排、取消、补课、恢复快照 |
| 考试 | CRUD、完成状态、关联课程、来源时区、提醒 | CRUD、倒计时、完成状态；组件随学习模式切换 |
| 导入与导出 | JSON、TSV、ICS、照片 AI、相机、Canvas 只读、EventKit、JSON/ICS 导出 | JSON/ICS 文本导入及手机文件同步 |
| 热力图 | 星期 × 精确时段的课程密度，数字与列表无障碍阅读 | 课表轻量展示；不移植 Android 密集图表页面 |
| 通知 | UNUserNotificationCenter 课程/考试提醒、每日简报、本地/邮件测试 | 默认镜像手机通知，可选独立通知，避免重复 |
| 账号 | 邮箱验证注册、Turnstile、登录、令牌轮换、资料/邮箱/密码修改、重置、注销、兑换 | 手机批准的独立可撤销会话，也可直接登录 |
| 云同步 | 官方 V2、WebDAV 条件写入、Google Drive OAuth/同步、官方设置 SSE | 官方云独立访问、与手机可靠文件同步；Drive 授权在手机完成 |
| Ask AI | SSE、模型、历史、推荐、用量、重置卡、附件、语音、需要确认的课程/考试建议 | 模型、历史、推荐、流式回答、系统听写、建议确认 |
| BYOK | 最多 20 组问答/识图/转写/朗读模型、OpenAI-compatible HTTPS、私有钥匙串、直接请求供应商、确认式建议 | 显式加密密钥传输与再次确认，独立对话 |
| Apple 平台能力 | iOS 26 Liquid Glass，旧系统 Material；动态字体、深浅色、减少透明度 | WidgetKit 智能叠放/表盘复杂功能，App Group 离线课表 |

不移植 Android Monet、Wear Tile、Data Layer、WorkManager 保活、APK 下载/安装及仅面向 Android 的设备采集/签名机制。对应替换为系统材质、WidgetKit、WatchConnectivity、Apple 后台刷新与 App Store 分发。国际版登录仍与现有后端一样是未开放状态，不伪造 Google 登录或会员购买。

## Windows：无需本地虚拟机

### 现在就预览

从仓库根目录运行：

```powershell
node apple/Preview/server.mjs
```

打开 **http://127.0.0.1:4173**。支持手机/手表切换、课程编辑、导入确认、课表查询演示、深浅色、大字体、普通/空课表/冲突状态与热力图。仅使用 `Fixtures/demo.json`，不处理真实账号、密钥、后端、系统通知或真正的 WatchConnectivity。

### Swift 开发与真实业务测试

打开 `apple/Classing.code-workspace`。使用官方 VS Code 扩展 `swiftlang.swift-vscode`，调试扩展为 `llvm-vs-code-extensions.lldb-dap`。Windows 没有 Apple SDK，SwiftUI/WatchKit/WidgetKit 类型检查、Canvas 和 iOS/watchOS 运行仍属于 macOS/Xcode。

本机已安装 Swift 6.4.2、最小 C++ Build Tools 和 Windows SDK。Swift 与主编译工具放在被忽略的 `apple/.tools/`，不进入 git。Swift 调试器额外组件未做运行验收。

```powershell
# 已验证：真实 Windows Swift 编译与 XCTest，不是 JavaScript 替代测试
pwsh -NoProfile -File apple/scripts/test-windows.ps1

# 用真实 Swift 编译器解析 Apple UI 语法（不等于 Apple SDK 类型检查）
pwsh -NoProfile -File apple/scripts/test-windows.ps1 -ParseOnly

# 独立的 JSON/YAML 与 Swift tree-sitter 语法检查
python -I -m pip install -r apple/scripts/requirements.txt
python -I apple/scripts/check_swift_syntax.py

# 浏览器数据契约测试
node --test apple/Preview/projection.test.mjs
```

另一台 Windows 可使用 [Swift 官方安装步骤](https://www.swift.org/install/windows/)，安装 Microsoft C++ Build Tools 与 Windows SDK，再安装 Swift。工具链版本或路径不同时，调整 `test-windows.ps1`；不要复制本机数 GB 的 `.tools` 到代码仓库。

浏览器 E2E：在 `apple/Preview` 中执行 `npm ci`、`npx playwright install chromium`、`npm run test:e2e`。已有 Chrome 时可设置 `CLASSING_BROWSER_PATH` 指向 Chrome 可执行文件。

## GitHub Actions 原生编译与截图

`.github/workflows/apple-ci.yml` 配置两个 job：

1. `preview`：语法/配置校验、浏览器数据契约与 Playwright 测试。
2. `native`：`macos-26` runner、Xcode 26+、Swift 核心测试、XcodeGen 工程生成、iOS/watchOS/两个组件 target 编译、真机 Release 归档及未签名 IPA、iPhone UI 测试与 iPhone/Watch 截图。

触发条件为 main 上的 Apple 文件变更、相关 PR 或手动 `workflow_dispatch`。可以用 repository variable `APPLE_XCODE_PATH` 指定完整 Developer 路径，例如 `/Applications/Xcode_26.2.app/Contents/Developer`；默认使用 runner 的 Xcode。

下载 Actions artifacts：

- `apple-browser-preview-and-tests`：浏览器截图、报告、失败 trace。
- `apple-native-simulator-builds`：模拟器 `.app`、PNG 截图、`.xcresult` 与 UI 测试附件。
- `apple-unsigned-iphone-watch-ipa`：真机 Release 构建的 `Classing-unsigned.ipa`。

GitHub 托管的 macOS runner 承担 Apple SDK 编译；最新运行状态和产物以 Actions 页面为准。

IPA 通过现有 XcodeGen embed 依赖包含 iPhone、Watch 及两端 Widget 扩展：

```text
Payload/Classing.app/
  PlugIns/ClassingWidgets.appex/
  Watch/ClassingWatch.app/
    PlugIns/ClassingWatchWidgets.appex/
```

工作流关闭代码签名并直接将归档中的设备 App 打包，无需 Apple 账号或签名密钥。未签名 IPA 不能直接安装；安装前须为主 App、Watch App 和扩展分别签名并匹配 provisioning profiles、App Group 及 companion 关系。模拟器产物单独保留，不用于 IPA。

## Mac / Xcode

```sh
brew install xcodegen
cp apple/Config/Local.example.xcconfig apple/Config/Local.xcconfig
xcodegen generate --spec apple/project.yml --project apple
open apple/Classing.xcodeproj
```

选择 `Classing` 或 `ClassingWatch` scheme。`Previews.swift` 提供真正的 Xcode Canvas 预览；预览/演示模式不会发送课表给真实手表，也不会排程真实通知。

模拟器 CI 不需要分发证书。**Apple 后端“不验证签名”不等于真机/App Store 安装“不需要 Apple 代码签名”。** 真机与分发须填写自己的 `DEVELOPMENT_TEAM` 并注册 bundle IDs/App Group；本次没有创建证书、上传 IPA 或发布 App Store。

Google Drive 必须填写自己的 iOS OAuth Client ID、回调 URI 和 callback scheme，使用 PKCE 与系统浏览器授权。不会内置第三方 client secret，也不会复用 Android OAuth 配置。

## 数据与后端兼容

以当前源码为准，旧文档仅供参考。关键对应：

- Android `PersistedLesson` → `ClassingCore/Lesson`；`scheduleRuleJson` 的 version=1 保留。
- Android 调课 wire 使用 **`MAKE_UP`**；跨日期调课为原日期 CANCEL + 新日期 RESCHEDULE。只改地点/教师不当作时间移动。
- `classing_cloud_sync_v2` 的 domain records、devices 在 wire 上是 **数组**，payload 为 JSON 字符串。
- Lamport 版本、确定性冲突合并、30 天删除标记、最多 64 台设备、最近 100 条变更。
- 关闭的 scope 不合并本地数据；PUT 整份文档时保留读到的远端禁用域，避免无意抹掉服务器记录。
- 当前源码的官方云所有接口（包括设置与 SSE）由 Pro 权限门控，旧文档的 Free 设置同步描述已不适用。选择 WebDAV/Drive 的 Pro 用户仍单独同步官方设置；Free/Legacy 的第三方同步与每日简报独立工作。Apple watch-prefixed 设置进入 `wear.settings`，不删除 Android 独有手表设置。
- 切换账号关闭自动云同步并重置云基线，保留本地课表，要求重新确认同步。
- access/refresh token、BYOK 配置/密钥/历史、WebDAV 密码、Drive 令牌只存 Keychain，不进入云文档或课表 JSON；BYOK 按账号与未登录 local 模式隔离。

后端改动见另一仓库 `internal/httpapi/client_integrity.go`、`client_integrity_test.go`、`apple_client_test.go`、`middleware.go`。**需要部署这些后端改动后，生产 API 才能接受新的 Apple 平台头。此会话未部署后端。**

## 验证状态与必须验收的边界

2026-11-14 在 Windows 完成：

- **53 项 Swift XCTest 全部通过**：包括规则、ICS、考试、夏令时、V2 合并、scope、跨星期调课、远期课程、热力图、并发刷新/注销、无 Android 签名头、凭据不入设置、多角色 BYOK、供应商错误脱敏和跨分片密钥脱敏。
- **7 项浏览器数据契约测试通过，8 项 Playwright UI 测试通过**。
- 全部 Apple Swift 源码通过真实 Swift 语法解析，JSON/YAML 校验通过。
- 后端 `go test ./...` 通过；Apple/Web 不校验 Android 证书，Android/部分签名头仍 fail-closed。
- Android 两项 `CrossPlatformCloudSettingsTest` 通过（Gradle `testCnDebugUnitTest` 定向运行），保留未知跨平台设置而不改变课表删除传播。旧 Android 版本须升级此兼容修复，避免误删 Apple 特有设置。
- 两套热力图序列色阶通过单色、单调明度、相邻明度差与表面对比校验。

仍需 macOS CI/真机验收：Apple SDK 类型检查和实际安装、Liquid Glass、相机/麦克风/日历权限、WatchConnectivity 配对/断网传输、加密密钥接收确认、WidgetKit 刷新预算、语音服务、配置后的 Google OAuth 与真实账户联调。浏览器截图不能替代这些验收。

Apple 对后台执行和通知数有限制：预先排程最近 60 条通知，在编辑、同步、前台和系统允许的后台刷新时重建，不承诺 Android 式固定周期。ICS 重复事件在明确的 366 天窗口内展开，未支持的参数会报出警告而不是猜测；更远日程需重新导入。Canvas 只读、不保存令牌。对话中断保留草稿，AI 修改必须确认且验证提问时的快照。
