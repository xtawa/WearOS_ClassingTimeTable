# 小米手表 5 兼容性排查指南

本文用于定位「Wear 端 APK 无法在小米手表 5 上运行」这一类问题。请**按顺序**执行，第 1 步的结论决定后面还有没有必要继续。

## 1. 先判定固件平台（决定性步骤）

小米手表 5 存在两套完全不同的固件：

| 版本 | 发布 | 系统 | 能否安装 APK |
| --- | --- | --- | --- |
| 国行版 | 2025-12 | 澎湃 HyperOS for wearables（Xiaomi Vela / NuttX 实时内核） | **不能**，不是 Android，没有 PackageManager，只能跑快应用式的 Vela JS 应用 |
| 国际版 | 2026-02（MWC） | Wear OS 6（Android 16 / API 36），叠加 HyperOS 皮肤 | 可以，支持 adb 侧载与手表端 Play 商店 |

判定命令（手表开启「开发者选项 → ADB 调试 / 无线调试」后）：

```bash
adb devices                                   # 手表能否作为 Android 设备被识别
adb shell getprop ro.build.version.release    # Wear OS 6 应返回 16
adb shell getprop ro.product.model
adb shell pm list features | grep watch       # 期望 android.hardware.type.watch
adb shell pm list packages | grep gms         # 是否预置 Google Play 服务
```

- `adb devices` 认不到设备、或 `getprop` 无 Android 版本输出 → **HyperOS(Vela) 机型**。此时任何 Android 侧的改动都无效，工程层面不可修复；只能改用 Wear OS 机型，或另行开发 Vela 快应用。
- 返回 Android 16 且有 `android.hardware.type.watch` → 属于 Wear OS 6 机型，继续第 2 步。

## 2. 关于 `android.hardware.type.watch`

`app/src/main/AndroidManifest.xml` 中的：

```xml
<uses-feature android:name="android.hardware.type.watch" />
```

默认 `required="true"`，其作用**只是让应用商店与安装器过滤掉「非手表设备」**。真机层面：

- 任何合规的 Wear OS 设备都会声明该 feature，因此它**不会**阻止在小米手表 5（Wear OS 版）上安装；
- `adb install` 不做 `uses-feature` 过滤，所以侧载失败一定不是它造成的；
- HyperOS(Vela) 机型根本不解析 Android manifest，删掉也没有意义。

结论：**移除该限制对本问题没有帮助**，且会带来副作用（详见 PR 说明与下表）：

| 副作用 | 说明 |
| --- | --- |
| Play 表单因子分类失效 | Play 依据该 feature 判定 Wear 应用，移除后手表端商店不再分发，Tiles / Complications 相关审核项也会失败 |
| 与手机端应用撞包名 | Wear 与 mobile 模块共用 `applicationId = com.xtawa.classingtime`（Data Layer 要求），移除后 Wear APK 可被装到手机上，与手机端互相覆盖 |
| 部分 OEM 启动器行为异常 | 少数手表启动器结合该 feature 与 `com.google.android.wearable.standalone` 决定图标展示，移除后反而可能不显示 |

## 3. 侧载失败的常见返回码

| 报错 | 处理 |
| --- | --- |
| `INSTALL_FAILED_TEST_ONLY` | 用 `adb install -t app-debug.apk` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / 签名不一致 | 先 `adb uninstall com.xtawa.classingtime` 再装（release 与 debug 签名不同） |
| `INSTALL_PARSE_FAILED_NO_CERTIFICATES` | 装到了未签名的 release 产物，请配置 `RELEASE_STORE_*` 后重新构建 |
| 拿到的是 `.aab` | AAB 不能直接安装，需用 bundletool 生成 universal APK |

## 4. 装上但闪退 / 白屏 / 无图标

```bash
adb logcat -c
adb shell monkey -p com.xtawa.classingtime -c android.intent.category.LAUNCHER 1
adb logcat -s AndroidRuntime:E ClassingTimetable:V ActivityManager:E
```

重点关注：

- `ClassName not found` / Room 迁移异常 → 数据库或混淆（release 开启了 `isMinifyEnabled`）问题；
- GMS 相关 `SecurityException` / `ApiException` → 见第 5 步；
- 首次启动引导协程异常已在本 PR 中被包裹，不再导致进程崩溃。

## 5. 无 Google Play 服务的手表

Wearable Data Layer 完全依赖 GMS。若第 1 步的 `pm list packages | grep gms` 为空：

- 手机 ↔ 手表直连同步不可用，应使用「独立模式 + 官方云账号」；
- 本 PR 新增 `DevicePlatformCapabilities`，在无 GMS 时直接跳过 Data Layer 请求，避免长时间挂起。

## 6. HyperOS 皮肤的后台限制

即便应用能正常启动，小米的省电策略会影响提醒：

- 手表设置 → 电池 / 省电模式：关闭「省电模式」，为应用授予后台运行与自启动权限；
- 应用内确保已授予通知权限与精确闹钟（`SCHEDULE_EXACT_ALARM`）；
- 省电模式下 Wear OS 会限制 WorkManager 周期任务，提醒依赖 `AlarmManager` 兜底。

## 7. 建议的后续工程改动（尚未包含在本 PR）

- `compileSdk` / `targetSdk` 由 35 升至 36 以对齐 Wear OS 6（需同步升级 AGP 8.5.0 → 8.7+，当前 AGP 与 Gradle 9.0.0 的组合本身也偏激进）；
- Wear Compose / Tiles 依赖升级到支持 Wear OS 6 的版本；
- 在设置页加入「设备诊断」入口，直接展示 form factor、GMS 状态与已连接节点数，便于远程排障。
