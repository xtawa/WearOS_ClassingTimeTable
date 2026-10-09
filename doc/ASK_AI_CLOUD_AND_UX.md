# Ask AI 云端辅助服务与客户端配置

## 管理员配置

后台「AI 管理 → Ask AI 辅助服务」配置：

- 推荐提示词模型：建议 MiMo 2.6 Flash。仅允许启用的公开供应商，普通输入价格不超过 25,000 点/百万 tokens、输出不超过 50,000 点/百万 tokens。Free / Legacy 的 Flash 限制继续生效。
- 转写方式 `MULTIMODAL_CHAT`：将本地 MP3 交给多模态模型转写，按实际 tokens 扣除 AI 额度；默认免费模型。
- 转写方式 `OPENAI_AUDIO`：选择已经配置地址和密钥的公开供应商，填写语音模型 ID，例如 `whisper-1`；使用 `POST /audio/transcriptions`。按实际秒数乘以每分钟算力点向上取整，最低 1 点。失败不扣费。
- 供应商管理复用原有加密密钥，不在客户端配置任何供应商密钥。

客户端只走云端转写。AudioRecord 获取 mono 16 kHz PCM，Jump3r 本地编码为 MP3；按住麦克风录音、松开发送、滑动或取消按钮撤销。限时一分钟，预留编码帧的时长余量。转写中禁止输入，显示 thinking 动画。服务器返回转写文本和算力点；云端与客户端临时音频在完成、错误或取消后删除。语音输入文件不使用附件的 14 天保留期。用量记录仅保留时长、模型、额度和状态，不保留录音或转写正文。

## 设置同步与推荐提示词

`GET/PATCH /api/v1/ai/preferences` 按账号保存默认模型、星标、自动刷新提示词、语音转写后发送、显示思考过程、图片预览和时间标记。每次进入 Ask AI、设置页或应用恢复前台时读取；修改单个字段合并，星标使用 add/remove 操作，避免覆盖其他设备的不同设置。失败时显示未同步，不冒充已保存。旧设备的本地星标首次登录后合并到云端。

这些是 Ask AI 本身的设置，同步对 Ask AI 用户开放；官方课表云同步仍仅限 Pro。国内版/国际版账号隔离保持原有规则，国际版 Google 登录仍为预览占位。

`POST /api/v1/ai/prompts` 接收当前课表。根据稳定课程内容哈希按账号缓存，日期变化和课程排序不触发付费生成；课程修改、添加、删除后重新生成。开启「每次打开自动刷新提示词」会绕过缓存并消耗额度，设置页明确提示。默认关闭；本地没有硬编码推荐条目。管理员可修改生成模型。

「用量与限额」位于 Ask AI 菜单的独立页面，显示周额度、永久余额和本周转写/提示词点数。默认模型不可用时回退到当前账户允许的默认模型。

## 课表编辑与拍照导入

客户端发送 `supportsTimetableActions` 与 `currentTimetableSnapshot`，其中 `editableLessons` 包含原始课程 ID。服务端只返回经过验证的 create/update 建议；不自动修改数据、不支持删除。客户端预览后由用户确认，再验证课表未变化后一次性应用；保留恢复快照。重复修改 ID、未知 ID、非法时间或周范围会被拒绝。

拍照导入使用 `/api/v1/ai/photo-import?stream=1` SSE，原有 JSON 客户端仍兼容。回复逐段显示，供应商实际返回的 `reasoning_content` 单独显示；识别完成后自动折叠，可展开。没有返回思考过程时明确显示未提供，不生成伪造思考内容。

## 动画与音频依赖

- Konfetti Compose 2.0.5，使用上游 burst 预设，最多 100 个粒子，尊重系统关闭动画设置。来源：https://github.com/DanielMartinus/Konfetti 。ISC 许可证见 `licenses/Konfetti-ISC.txt`。
- Jump3r 1.0.5，Java LAME 编码核心，无 native ABI 或桌面 AudioFormat 依赖。来源：https://git.iem.at/sciss/jump3r 。LGPL 2.1+ 许可证见 `licenses/Jump3r-LGPL-2.1.txt`。完整对应源代码：https://repo.maven.apache.org/maven2/de/sciss/jump3r/1.0.5/jump3r-1.0.5-sources.jar 。项目可从公开源码与 Gradle 依赖重新构建。

所有录音和编码都在后台线程执行。图片预览降采样到不超过 192 像素；会话预览缓存最多 32 张，避免完整图片常驻内存。
