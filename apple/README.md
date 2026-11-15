# Classing · iOS / watchOS

原生 SwiftUI 移植位于本目录。业务逻辑是可在 Windows 运行测试的 Swift Package；iPhone、Apple Watch 和 WidgetKit 使用独立 target。**浏览器预览是布局与交互参照，不是原生 SwiftUI 模拟器。**

## 已实现的功能

| 功能 | iPhone | Apple Watch / WidgetKit |
|---|---|---|
| 今日、下一节课、时间与地点 | 原生界面、倒计时、日/周浏览 | 离线列表、周课表、下一节课 |
| 课程管理 | 新增、编辑、删除、搜索 | 仅查看课程与详情，修改在手机完成 |
| 重复规则 | 学期周次、单双周、每 N 周、轮换教学日、节假日、指定日期、课程分组/课型 | 保留规则并投影；高级规则在手机编辑 |
| 调课与补课 | 单日修改、取消、补课、快照 | 查看同步后的安排，修改在手机完成 |
| 考试 | CRUD、完成状态、关联课程、来源时区、提醒 | CRUD、倒计时、完成状态；组件随学习模式切换 |
| 导入与导出 | JSON、TSV、ICS、照片 AI、相机、Canvas 只读、EventKit、JSON/ICS 导出 | 只接收手机同步，不提供独立导入 |
| 热力图 | 星期 × 精确时段的课程密度，数字与列表无障碍阅读 | 课表轻量展示；不移植 Android 密集图表页面 |
| 通知 | UNUserNotificationCenter 课程/考试提醒、每日简报、本地/邮件测试 | 默认镜像手机通知，可选独立通知，避免重复 |
| 账号 | 邮箱验证注册、Turnstile、登录、令牌轮换、资料/邮箱/密码修改、重置、注销、兑换 | 默认跟随已配对手机账号自动登录、切换与退出；手表保留独立会话 |
| 云同步 | 官方 V2、WebDAV 条件写入、Google Drive OAuth/同步、官方设置 SSE | 官方云独立访问、与手机可靠文件同步；Drive 授权在手机完成 |
| Ask AI | SSE、模型、历史、推荐、用量、重置卡、附件、语音、需要确认的课程/考试建议 | 当前对话、推荐、流式回答、系统听写；不展示历史会话列表或应用课程修改 |
| BYOK | 最多 20 组问答/识图/转写/朗读模型、OpenAI-compatible HTTPS、私有钥匙串、直接请求供应商、确认式建议 | 显式加密密钥传输与再次确认，独立对话 |
| Apple 平台能力 | iOS 26 Liquid Glass，旧系统 Material；动态字体、深浅色、减少透明度 | WidgetKit 智能叠放/表盘复杂功能，App Group 离线课表 |

不移植 Android Monet、Wear Tile、Data Layer、WorkManager 保活、APK 下载/安装及仅面向 Android 的设备采集/签名机制。对应替换为系统材质、WidgetKit、WatchConnectivity、Apple 后台刷新与 App Store 分发。国际版登录仍与现有后端一样是未开放状态，不伪造 Google 登录或会员购买。

## 首页与小组件

首页沿用 Android 的课程信息岛、柔和课程配色与时间上下文，保留 Apple 的原生导航和系统材质。
开课前、上课中、课间、今日结束及空课表分别显示对应状态；首页、Watch 和 WidgetKit 复用
`ClassingCore/Agenda.swift` 的时间计算，考试模式没有考试时不会回退显示课程。

- “下一节课 / 考试”：小、中、大号桌面组件，以及单行、圆形、矩形锁屏/表盘组件。
- “今日课表”：大号组件显示当天剩余安排。
- 设置 → 外观与组件 → 小组件：预览实际组件的 SwiftUI 布局，并查看添加方法。

组件只读取设备本地 App Group 数据，不为显示课程请求网络。时间线包含开课、下课和本地午夜；
倒计时由系统计时文本显示，实际刷新仍受 WidgetKit 调度约束。应用内预览不替代真实主屏幕、
锁屏及表盘上的安装验收。macOS CI 会导出首页深浅色、空课表和组件布局截图。

## 实时活动、引导与 Apple 订阅

- 首次引导分为欢迎、学期日期、导入、按需授权四步。可以跳过，不强制登录、授权或订阅。
- 设置 → 外观与组件 → 实时活动：开启当天课程/考试的锁屏与灵动岛卡片。ActivityKit 在应用运行或系统
  允许的后台刷新时更新；系统计时文本可继续倒计时。开课/结束边界后标记内容过期，提示打开
  课表刷新，不把陈旧状态继续表示为实时状态。当前没有配置 APNs 服务端推送或后台自动开课保活。
- 设置 → 外观与组件 → 小组件 → 实时活动：查看锁屏布局及灵动岛示意；最终系统容器仍需真机验收。
- 设置 → Classing Pro：月付/年付页面、商品本地化价格查询、交易监听、验证、购买与恢复。
  现有云端 Pro 权限仍只由会员服务决定，StoreKit 本地测试不修改账号或云端权益。

商品尚未创建，现有后端也没有 Apple 交易验证/通知接口，因此 **Release 暂不开放扣款**。
`CLASSING_PRO_MONTHLY_ID` / `CLASSING_PRO_YEARLY_ID` 配置真实商品后可查询价格；正式启用购买
还需服务端验证 Apple JWS、绑定 Classing 账号、处理续订/退款/撤销并更新会员状态。
不能仅凭客户端交易 ID 或成功弹窗授予云端 Pro。

`Config/ClassingTest.storekit` 仅包含本地测试商品及测试价格。UI 测试通过 `SKTestSession`
注入测试商店，并以 Debug 参数 `--storekit-testing` 执行实际 StoreKit 测试购买和恢复。
测试商品 ID 使用 `com.xtawa.classingtime.test.pro.monthly` / `.yearly`，不得复制为生产配置。
本地测试不需要 Apple 账号，也不会真实扣款。Xcode 的 Classing scheme 指向本地测试商店；
测试购买入口仍只接受 Debug 参数 `--storekit-testing`。Release 归档不包含测试商店资源，
不接受该参数。测试会话保留到整个购买/恢复用例结束，并在结束时清除测试交易。

## 手机与手表的设置分工

手机设置参考 Android 的分类与摘要：首页保留账号、Pro，以及学期与课程、提醒与简报、
外观与组件、同步与设备、Ask Classing、数据与备份、关于七个入口，详细开关放到子页面。
手表设置仅保留显示模式、教师显示、独立提醒、连接与更新课表。账号表单、模型密钥、云提供商、
独立导入和快照恢复均放到手机；手机公布当前账号标识；手表复用设备授权接口创建独立会话，配对手机自动批准。手机号、密码、访问令牌和刷新令牌不会经配对通道复制。

本地设备同步在应用启动时初始化，不等待账号或云服务请求。两端可直连时，小型课表通过
WatchConnectivity 消息传输并等待保存确认；离线或较大资料使用后台文件队列。界面区分连接、
排队和接收确认，不把文件入队当成同步完成。设备本地同步独立于云同步范围，沿用现有 V2
合并与删除标记；手机负责学校日期、时区等全局设置，避免新手表的默认值覆盖手机，
也防止较晚送达的旧队列文件回退新设置。
真实配对、后台投递和签名后的安装关系仍需真机验收。

助手星标使用后端的 `favoriteAdd` / `favoriteRemove` 单项操作。回答直接消费事件流里的额度信息，
完成后不追加一个会使成功回答报错的额度请求；完成帧后的尾部或连接关闭不撤销已完成的回答。
正常请求取消不弹“操作未完成”，未启用云同步的首页刷新只更新本地安排。服务端错误按错误码和状态映射为用户可理解的提示，不展示内部参数、HTTP 编号或原始英文异常。

主页面去掉说明文案与首页悬浮助手入口。周课表按真实时间轴绘制并处理重叠课程；月日历显示有课日期与选中日期的课程。课程列表使用独立搜索区域，隐藏无关日期控件。Watch 的周数按钮独立于列表行，显示真实教学周和日期范围。BYOK 的模型请求与密钥传输均检查有效 Pro 权益。

手机与手表的助手回答支持 Markdown 标题、强调、链接、引用、有序/无序与任务列表、
横向表格及代码块。代码保持原样，手机可复制代码；思考过程沿用相同排版。未闭合的
代码围栏也会随流式输出显示，不等整个回复完成。用户自己输入的消息保留原文。

SSE 直接按 UTF-8 字节识别 LF/CRLF/CR 和空行边界，避免 Foundation `AsyncBytes.lines`
合并空行后，把不同事件的 JSON 累积到 `done`。回归测试通过实际 URLSession 接收后端形状的
完整事件流，并分别检查 delta、usage、done，不只向聊天 API 注入预先拆好的事件。

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

2026-11-15 更新后的验证范围：

- **Windows 的 102 项 Swift XCTest 通过**：包括规则、ICS、考试、夏令时、V2 合并、scope、跨星期调课、远期课程、热力图、并发刷新/注销、凭据隔离、BYOK、首页/组件边界、助手请求与完成帧、UTF-8 事件流分帧、URLSession 接收、富格式排版、取消识别，以及独立于云范围的设备同步、手表初始化与乱序投递保护、简报投递时区、旧云/设备快照不覆盖服务端简报偏好和可读错误提示。macOS 的相同测试以对应提交的 Actions 为准。
- **7 项浏览器数据契约测试通过，8 项 Playwright UI 测试通过**。
- 全部 Apple Swift 源码通过真实 Swift 语法解析，JSON/YAML 校验通过。
- iPhone、Watch、两个 Widget 扩展已通过 Apple SDK 编译，设备 Release 归档及未签名 IPA 打包通过；后续变更以对应提交的 Actions 结果为准。
- 原生 UI 测试覆盖首页、深色大字体、空课表、组件/实时活动布局、四步引导及 StoreKit 本地购买/恢复。通过状态与截图以对应提交的 `apple-native-simulator-builds` 为准，不将源代码检查当作系统交互验收。
- 模拟器启动有七分钟限时和一次保留数据的重启重试；三项脚本测试覆盖已启动设备、迁移超时恢复及重试失败退出。Watch 在手机 UI 测试结束后启动，手机测试禁用并行模拟器。
- CI 的手机 UI 测试固定使用已安装的 iOS 26.2，避免 [iOS 26.5 StoreKit Test 配置回归](https://developer.apple.com/forums/thread/826971)。三项选择器测试保证指定运行时生效、缺失时报错以及默认选择行为；编译仍使用 runner 的当前 Xcode SDK。
- 后端 `go test ./...` 通过；Apple/Web 不校验 Android 证书，Android/部分签名头仍 fail-closed。
- Android 两项 `CrossPlatformCloudSettingsTest` 通过（Gradle `testCnDebugUnitTest` 定向运行），保留未知跨平台设置而不改变课表删除传播。旧 Android 版本须升级此兼容修复，避免误删 Apple 特有设置。
- 本轮 Android `AccountApiClientContractTest` 13 项通过；移除过期的免费账号简报限制，保留服务端返回的规范状态，并在前台刷新。Windows 验证使用 JDK 21、短临时路径和 Kotlin `in-process` 编译，未更改系统 Java 配置。
- 两套热力图序列色阶通过单色、单调明度、相邻明度差与表面对比校验。

仍需真机验收：实际安装、Liquid Glass、相机/麦克风/日历权限、WatchConnectivity 配对/断网传输、加密密钥接收确认、WidgetKit 刷新预算、实时活动/灵动岛系统容器、语音服务、配置后的 Google OAuth 与真实账户联调。正式 IAP 还需真实商品、服务端验证和 Apple 沙盒联调。浏览器截图不能替代这些验收。

### 账号、简报与导入更新

Apple 登录采用 AuthenticationServices 与服务端签发的五分钟单次 nonce；服务端验证 Apple JWKS 签名、issuer、audience、时效和 nonce。iOS entitlement 已声明 Sign in with Apple，正式签名仍需在开发者后台启用对应 App ID capability。后端需部署新的 `/auth/apple/nonce`、`/auth/apple`、`/account/providers/apple`，并配置 `APPLE_CLIENT_IDS=com.xtawa.classingtime.apple`。未配置时页面提示暂不可用，不创建模拟账号。首次 Apple 登录不自动合并同邮箱账号；用户可在账号管理中连接并验证邮箱，再设置密码。

资料、邮箱与密码使用独立页面；普通用户名编辑无需当前密码。iOS 不提供会员兑换入口，保留购买和恢复购买；助手额度重置码仍可使用。每日简报在登录、回到前台和打开设置时从独立简报接口读取，保存使用服务端返回值，投递时区独立于学校时区。导入文件、文本、照片及 Canvas 均显示处理和完成反馈，确认后才写入课表。新增页面与交互的原生验收以当前提交 CI 为准。

Apple 对后台执行和通知数有限制：预先排程最近 60 条通知，在编辑、同步、前台和系统允许的后台刷新时重建，不承诺 Android 式固定周期。ICS 重复事件在明确的 366 天窗口内展开，未支持的参数会报出警告而不是猜测；更远日程需重新导入。Canvas 只读、不保存令牌。对话中断保留草稿，AI 修改必须确认且验证提问时的快照。
