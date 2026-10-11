# 本地验证记录

日期2026-10-11。这里只记录本机结果，未推送CI、未开模拟器、未部署、未执行真实购买或账户/设备验收。基准客户端 `d8a1586`、后端 `631a706`。

## 自动验证

最终Android检查 `BUILD SUCCESSFUL in 14m 39s`，533次测试执行：531通过、2跳过、0失败、0错误。这里统计两种market的执行次数，不代表533个不同场景。详细数量、lint结果与产物SHA-256见 [机器可读验证清单](verification-summary.json)。

| 测试模块 | 执行 | 通过 | 跳过 |
|---|---:|---:|---:|
| shared | 57 | 57 | 0 |
| mobile cn | 173 | 172 | 1 |
| mobile global | 173 | 172 | 1 |
| Wear cn | 65 | 65 | 0 |
| Wear global | 65 | 65 | 0 |

四个Release lint均为0 Error/Fatal；mobile各273 Warning、9 Information，Wear各92 Warning、1 Information。已修复本轮发现的失败：ICS导入断言核对已展开的具体日期；新JSON规则测试改在Robolectric运行；既有录音入口增加显式权限检查。最后补入的DST gap/count及DTEND精确时长回归、首启页面滚动布局均包含在最终检查中。

四份APK和两份Global AAB已构建。APK通过 `apksigner verify`，用AAPT2读取实际包名，与构建元数据一致；AAB通过 `jarsigner -verify`。签名校验不代表已完成Play App Signing或购买验收。产物保留在build目录，不纳入源代码提交：

| 客户端 | 国内APK | 国际APK | 国际AAB |
|---|---|---|---|
| 手机 | [mobile-cn-release.apk](../../mobile/build/outputs/apk/cn/release/mobile-cn-release.apk) | [mobile-global-release.apk](../../mobile/build/outputs/apk/global/release/mobile-global-release.apk) | [mobile-global-release.aab](../../mobile/build/outputs/bundle/globalRelease/mobile-global-release.aab) |
| Wear | [app-cn-release.apk](../../app/build/outputs/apk/cn/release/app-cn-release.apk) | [app-global-release.apk](../../app/build/outputs/apk/global/release/app-global-release.apk) | [app-global-release.aab](../../app/build/outputs/bundle/globalRelease/app-global-release.aab) |

国内实际包名 `com.xtawa.classingtime`，国际实际包名 `com.xtawa.classingtime.global`，手机/Wear同market保持一致。完整本机日志位于任务目录 `C:\Users\Administrator\Documents\Codex\2026-10-11\wearos\android-release-final.log`；JUnit与lint XML保留在各模块build目录。

Backend `go test ./...` 通过，包括混合OAuth scope同意/篡改拒绝与复杂规则金标准。`go vet ./...`、Windows `go build ./cmd/...`、Linux amd64 `go build ./cmd/...`通过。Linux媒体资源限制测试在Windows明确skip，交叉编译不能证明它运行成功。

5份后端DOM回归脚本通过：`tests/ai-web.test.cjs`、`ai-admin.test.cjs`、`announcements.test.cjs`、`ai-reset-cards.test.cjs`、`web-upgrade.test.cjs`。本轮未改这些网页行为。

`scripts/validate-schedule-migration.py`通过：读取实际Room3/4 schema及MIGRATION_3_4 SQL，在SQLite建旧表和数据后执行迁移，核对字段类型/nullability、保留课程与节次及外键。它不是Android实机或Room运行时迁移证明。

`scripts/validate-metrics.cjs`通过：空样本、creation session去重、孤立完成不进完成率、只归属已完成会话的纠错、导出按匿名安装ID覆盖、D7成熟分母、非法format拒绝。真实人群数据为0。

## 浏览器原型

用内置浏览器验证首屏、入队与applied分别显示、非法结束时间拒绝确认、取消后课程不变、深色主题和长课名两行。保存 [预览截图](preview.jpg)。这是交互HTML，未把浏览器按钮的模拟applied当成真实Wear ACK。

## 可复现环境

本机 JDK `G:\AndroidStudio\jbr`，Gradle缓存 `G:\AndroidDevData\Gradle`，TEMP/TMP `G:\AndroidDevData\Temp`，Android SDK已配置；没有更改机器级变量。另一JDK在此Windows路径下遇到UnixDomainSocket异常，使用已有JBR与短临时路径完成检查。

```powershell
$env:JAVA_HOME='G:\AndroidStudio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:TEMP='G:\AndroidDevData\Temp'
$env:TMP=$env:TEMP
.\gradlew.bat --gradle-user-home G:\AndroidDevData\Gradle --max-workers=2 --console=plain '-Pkotlin.compiler.execution.strategy=in-process' :shared:test :mobile:testCnDebugUnitTest :mobile:testGlobalDebugUnitTest :app:testCnDebugUnitTest :app:testGlobalDebugUnitTest :mobile:lintCnRelease :mobile:lintGlobalRelease :app:lintCnRelease :app:lintGlobalRelease :mobile:assembleCnRelease :mobile:assembleGlobalRelease :app:assembleCnRelease :app:assembleGlobalRelease :mobile:bundleGlobalRelease :app:bundleGlobalRelease
python scripts/validate-schedule-migration.py
# Install jsdom into a local test directory, or set JSDOM_MODULE to its existing path:
node scripts/validate-metrics.cjs
```

生成合成图/PDF使用已存在的Python PIL+reportlab运行时执行 `scripts/create-import-fixtures.py`；不是AI识别测试。后端执行 `go test ./...`、`go vet ./...`、`go build ./cmd/...`。Linux交叉编译在独立进程设置 `GOOS=linux` 和 `GOARCH=amd64`。

## 明确限制

- Windows上AndroidX FileProvider依赖POSIX路径的旧相机文件用例被显式skip；保留Linux CI运行该用例。没有将其标记为相机实测通过。
- 现有Android依赖/图标API有弃用和lint警告；本轮不把警告都消除等同功能验收。
- 30组排课金标准在一个参数表测试中；15组ICS、跨窗口例外和客户端回归另计，不把“测试方法数”说成独立学校样本数。
- 所有导入图/PDF都是合成；真实模型、学校OAuth、真实数据、Play购买/恢复、人测、实机离线重连和AOD均待验。
- 源学校时区/旅行双时区、正式Billing、Global AI启用、生产LMS OAuth/后台同步、Apple Watch工程仍未实现；[traceability](traceability.md)逐项列出。
