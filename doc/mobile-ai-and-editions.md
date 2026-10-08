# 移动端 Ask AI、拍照导入与版本配置

## 登录和账号域

登录页可自由切换「国内版」和「国际版」，提示账号不互通，并链接到 [国内版与国际版的区别](https://xtawa.craft.me/diff-classing)。国内版沿用账号密码和当前后端。国际版目前只有 Google 登录按钮与待接入提示，点击不会发起 OAuth、注册或切换已有账号。

`API_BASE_URL` 是国内现有 API，默认 `https://api-classing.underflo.ink`。`cn` / `global` flavor 的安装包、签名和 `CLIENT_MARKET` 继续分别校验；新的 `X-Classing-Account-Market: CN|GLOBAL` 指定用户选择的账号域。服务器必须同时验证安装包签名、账号域和登录用户，不允许仅更改请求头访问另一个账号的数据。未发送新请求头的旧客户端沿用安装包对应版本。移动端仅在退出登录后显示版本切换。

账号详情提供 `account.accountClass`（FREE / LEGACY）、`account.market`（CN / GLOBAL）；会员状态提供 `membership.accountType`（FREE / LEGACY / PRO）和 `membership.access`。Pro 以服务器认定的有效会员到期时间为准，不使用本地缓存来授权云服务。

Google ID 尚未接入。后续需配置独立的国际版 API 地址（若分开部署）、Google OAuth Web/Android client ID、已登记包名及签名、服务端 ID Token 的 issuer/audience 验证，以及 GLOBAL 用户注册流程。不要把 Google Drive OAuth client ID 当作国际版登录 ID。用户名/邮箱仍保持现有唯一性规则；未来支持同邮箱分别注册两个版本时，需要另行迁移身份索引，不能直接复用国内用户。

## Ask AI

左上角汉堡菜单打开侧栏，提供返回、新建对话和近期对话。模型改成下拉菜单，可星标；星标按用户 ID 在本机保存，并稳定排到列表前面。服务端过滤后的模型列表决定可用模型，星标不会扩大权限。

附件通过系统文件选择器上传，每条消息最多 4 个，每个不超过 20 MiB。支持 PDF、JPEG/PNG/GIF/WebP、MP3/WAV/M4A/OGG/FLAC、TXT/MD/CSV/JSON。移动端图片统一压缩为 JPEG。文字 PDF 提取全文（128 KiB 上限）；扫描 PDF 最多 4 页；音频附件最多 10 分钟。原文件按账号隔离保存，默认 14 天，过期后不再供模型访问并由定时清理任务删除。删除附件按钮同时删除后端原文件。对话文字和模型回答不因附件到期而删除。

附件内容按模型能力发送；处理图片和音频建议选 MiMo 2.6 Flash。部分 cc 模型只支持文本，上游拒绝不支持的多模态内容时会返回错误。供应商 API 密钥只保存在后端，不进入 APK。

语音按钮按住录音，松手转写并发送，上滑 56 dp 或点击「取消」撤销。也支持无障碍点击开始、再次点击结束。录音最长 60 秒，离开页面或应用进入后台取消录音和待处理转写。

Android 12+ 且 `isOnDeviceRecognitionAvailable` 为真时使用明确的本地识别器，不调用 Android 默认的未知云识别服务。其他设备录制 16 kHz 单声道 PCM/WAV，由 Classing 私有临时接口转换为 MP3 后交给 MiMo 2.6 Flash 转写。Android 系统不提供通用 MP3 编码器，因此保留轻量 WAV 采集，避免为客户端引入大型编码库。云端转写计入现有 Ask AI 点数；音频转写完成、失败或取消后立即删除，不进入 14 天附件库。设备端临时文件也随结束删除。转写文字作为普通问题进入对话。

必需权限：`RECORD_AUDIO`（运行时申请）；`queries` 包含 `android.speech.RecognitionService`。首次授权后需重新按住麦克风。

## 后端接口与存储配置

- `POST /api/v1/ai/attachments`：multipart，唯一字段 `file`；返回 `attachment` 元数据。
- `GET /api/v1/ai/attachments/{id}`：授权下载。
- `DELETE /api/v1/ai/attachments/{id}`：删除原文件。
- `POST /api/v1/ai/chat`：JSON 增加 `attachmentIds` 数组，SSE 协议不变。
- `POST /api/v1/ai/transcriptions`：multipart `file`，返回 `text`、`costPoints`。
- `GET /api/v1/briefings/daily/notifications`：仅 Legacy/Pro，读取简报测试通知；不提供云文档或课表，避免将 Legacy 简报与 Pro 云同步绑定。

环境变量：`AI_ATTACHMENT_STORAGE_DIR`（Compose 为 `/data/ai-attachments`）；`AI_ATTACHMENT_RETENTION_DAYS=14`；`AI_MAX_ATTACHMENT_BYTES=20971520`；`AI_MAX_USER_STORAGE_BYTES=209715200`；`AI_MAX_TOTAL_STORAGE_BYTES=1073741824`。上传配额和 Ask AI 点数额度分别控制。Docker 运行镜像需要 `ffmpeg`、`poppler-utils`。附件目录必须持久化且不能直接公开；备份包含 `ai-attachments.tar.gz`。

权限矩阵：

| 账号 | Ask AI | 官方云同步 | API/MCP | 每日简报 |
|---|---|---|---|---|
| 新 Free | 免费额度、Flash | 不允许 | 不允许 | 不允许 |
| Legacy | 与 Free 相同 | 不允许 | 允许 | 允许 |
| Pro | 会员额度、可用会员模型 | 允许 | 允许 | 允许 |

迁移只在第一次上线时标记已有账号所属历史群体，现有有效 Pro 保持 Pro，历史账号在会员到期后恢复 Legacy；以后注册默认 Free。原账号 ID、课表、会员到期时间和会话不变。

## 验证与低负载构建

有效照片读取尺寸时 `BitmapFactory.decodeStream(... inJustDecodeBounds=true)` 正常返回 null；修复后只检查输入流是否打开及图片宽高。回归测试覆盖有效图片、缩放和坏图片。

小内存服务器一次只运行一个构建/测试。Gradle 使用单 worker、in-process Kotlin、有限 JVM 堆及 cgroup 的内存/交换空间/CPU 上限；不要使用仓库默认的 4 GiB JVM 堆在 2 GiB VPS 上直接构建。构建不能替代真机验收：需在支持本地识别和不支持本地识别的 Android 设备分别检查按住、松手、上滑取消、后台中断、权限拒绝和中文转写，以及实际相机图片的 EXIF 方向。

参考：[Android 图片尺寸读取](https://developer.android.com/reference/android/graphics/BitmapFactory.Options#inJustDecodeBounds)、[SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)、[MiMo 音频理解](https://mimo.mi.com/docs/en-US/quick-start/usage-guide/multimodal-understanding/audio-understanding)。
