# Classing UX and local BYOK — 2026-10-11

本轮开始前已分别执行 `git pull --ff-only`：客户端 `f49a53d → d963d93`，后端 `de2c74b → 0f3b8b9`。本目录记录其上的本地修改与验收；未部署，未推送。OPPO 版本延期，本轮没有新建 OPPO 模块。

## 已实现的行为

| 范围 | 实现 |
| --- | --- |
| 手机考试首页 | 保留课表/考试胶囊；使用课程页的页面标题、信息岛、时间字号、间距和主题色；下一场考试、待考列表、已结束折叠、新增/编辑/删除确认；编辑期间的数据变动会阻止覆盖 |
| 显示类型 | 设置 → 外观 → 聚焦型/密集型；同一密集组件用于首页与完整课表页；七天列、按时间排列课程、上/下周、本周定位、详情及编辑；重叠课程全部保留，大字体允许水平滚动 |
| 本地使用指标 | 从 OOBE 移除；关于页仅在 Debug 构建且开发者模式开启时显示；Release 中隐藏；默认关闭策略保留 |
| OOBE 登录导入 | 图片/PDF、AI 文本导入先校验登录；复用已有账号/注册/密码重置页面，登录成功后继续所选导入；取消登录保留选择且不挂载受保护导入器；注册的 Turnstile 弹窗也在 OOBE 内正常挂载；ICS/JSON/手动及独立 Canvas 授权保持各自流程 |
| 调课语义 | 同一日期与时间段的备注、教师、地点、标题等覆盖保留原有单次编辑范围，但不进入调课列表、不显示调课来源；空修改不新增例外；时间或日期改变仍计为调课 |
| 点数呈现 | 分开显示周额度、处理中预留、加购余额和总可用；预留先覆盖周额度再占加购点数；大余额用 Long 防溢出；官方 AI 停用时显示零可用并保留余额说明；未改变服务端价格、扣费或购买规则 |
| BYOK 设置 | AskAI 设置内的二级页面；Pro 才能配置和调用，调用前重新检查账号与权益；问答、图片多模态、语音转文字、语音合成分别配置；自定义 HTTPS API 基址、模型 ID 与密钥；删除/清空确认 |
| BYOK AskAI | 复用手机 AssistantContent；本地加密历史和收藏、图片选择/相机、语音转草稿、手动朗读；设置往返保留草稿和当前会话；删除模型后仍可阅读旧会话，继续发送前需选择有效模型；服务商回复保存失败时仍可阅读；课程/考试提案复用现有校验和明确确认，并检查快照是否过期 |
| BYOK 导入 | 已开启 BYOK 时，AI 图片/PDF 和文本导入使用选定的自带模型并进入现有预览；缺少所需模型、权益失效、存储错误时停止，不自动切到官方付费接口 |
| Wear BYOK | 手机选择一个支持问答的模型；手表前台主动开启接收界面；两端已配对、使用相同发行版本且登录同一 Classing 账号；用户核对六位码后传输；手表加密落盘成功后才确认；Wear AskAI 直接连接该模型且保持只读问答 |

密集型是 **每日列下按时间排列课程卡片**，不是按分钟比例绘制高度的时间轴。聚焦型默认保留，外观设置保存在本机。

## 密钥与同步边界

- 用户更正的目的端是 **Wear，不是 Web**；没有新增 Web BYOK，也没有服务器 BYOK 密钥字段。
- `classing_byok_private` 使用 Android Keystore 主密钥和 EncryptedSharedPreferences；按 Classing 账号隔离配置、历史和收藏。密钥输入不使用 rememberSaveable，使用密码键盘并关闭自动纠正；不进入日志或普通设置 JSON。非 ASCII/控制字符会在配置验证阶段拒绝，避免 HTTP 头异常回显凭证。
- 两个模块都将该文件排除于 Android 旧版云备份、新版云备份和系统设备迁移。Classing 官方同步、WebDAV、课表导出及普通 Wear Data Layer 不读取这个存储。
- 用户模型请求直接发往其配置的服务商；服务商通过 Authorization 头接收调用凭证。Classing 后端仅用于账号/Pro 权益检查，检查请求不含自带密钥或对话。
- 与手表共享密钥使用 **安全 RFCOMM 蓝牙连接**，另加临时 P-256 ECDH 会话密钥、AES-GCM、六位校验码、账号和 applicationId 绑定、长度限制、两分钟超时与加密保存回执。只发送选定 Wear 模型，其他模型密钥不发送。断开、取消、超时即停止，无 Internet/Data Layer 备用路径。
- 提供者错误不展示原始响应体；异常 JSON 使用固定错误文本；问答/转录输出会移除与当前密钥完全匹配的内容。合同测试包含畸形响应及凭证回显情况。
- 自带模型费用由服务商收取，不消耗 Classing AI 点数。支持 OpenAI 兼容的 `chat/completions`（含 image_url）、`audio/transcriptions` 和 `audio/speech`；提供者需要实现相应端点，模型能力不能仅由模型名字推断。没有对任意厂商进行未持有密钥的在线兼容承诺。

## 动效源码调查与复用

两个仓库的当前代码并未在 Android 或原生 JS 控制台中加载 React Bits 运行时。现有实现是 Compose/CSS 对其交互模式的适配；本轮继续复用这些组件。

| 阅读的原始实现 | 关键行为 | 本轮落点 |
| --- | --- | --- |
| [FadeContent.jsx](https://raw.githubusercontent.com/DavidHDev/react-bits/main/src/content/Animations/FadeContent/FadeContent.jsx) | 进入视口后显示、阈值、状态重置与清理 | 共用 fade；首页状态/课程身份、课程/考试切换、调课筛选、日历同步、AskAI 状态；控制台动态弹窗、消息、提示和点数重置卡 |
| [AnimatedContent.jsx](https://raw.githubusercontent.com/DavidHDev/react-bits/main/src/content/Animations/AnimatedContent/AnimatedContent.jsx) | 轴向进入、方向、透明度/缩放、生命周期清理 | 共用 horizontal 与 pop；密集周翻页、导航、发送按钮；信息岛/卡片/课程块统一内容尺寸动画并响应关闭动画设置 |
| [SpotlightCard.jsx](https://raw.githubusercontent.com/DavidHDev/react-bits/main/src/content/Components/SpotlightCard/SpotlightCard.jsx) | 指针位置、可见性、减少动画 | 沿用已有控制台 spotlight，扩大动态节点发现范围；重播使用帧调度而非强制布局；减少动画设置关闭重播并确保内容可见 |

手机新增页面继续使用 `bitsReveal`、Classing 信息岛、共用 progress/loading 组件；没有用持续动画替代真实操作状态。详细既有适配见 [native motion matrix](../react-bits-native-motion-matrix.md)。

## 设计预览与依据

[交互 HTML](preview.html) 使用示例数据，包含聚焦、密集、考试、点数、BYOK 二级说明；截图 [密集](dense-preview.png)、[考试](exam-preview.png)、[BYOK](byok-preview.png) 均为 HTML 设计预览，**不是手机/手表运行截图**。

密集课表参考 [WakeUp 主界面说明](https://www.wakeup.fun/doc/main_interface.html) 的一周浏览与周切换，视觉使用当前 Classing 课程页主题组件。

通信依据：[Android Wear Data Layer](https://developer.android.com/training/wearables/data/overview) 可能经云端路由，故不用于传密钥；[蓝牙连接文档](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices) 与 [权限文档](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) 对应本地 RFCOMM 及 Android 12+ Nearby devices 授权。

模型请求依据：[Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[Transcriptions](https://developers.openai.com/api/reference/resources/audio/subresources/transcriptions/methods/create)、[Speech](https://developers.openai.com/api/reference/resources/audio/subresources/speech/methods/create)。

## 验证与待实机验收

最终机器可读结果见 [verification-summary.json](verification-summary.json)，逐项操作和未执行项见 [acceptance.csv](acceptance.csv)。`python tools/verify_byok_boundary.py` 可复查备份、59 个同步/序列化文件及传输/请求路径的边界。

当前没有测试账号、模型密钥、Play 测试购买环境或连接的手机/手表，因此 **真实提供者调用、Pro 账号操作、登录/Turnstile 联调、蓝牙传输和设备布局尚需实机验收**。本地通过的模拟合同、加密和业务测试不能替代这些项；不启动 AVD。
