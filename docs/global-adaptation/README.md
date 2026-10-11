# Classing 逐项适配与本地验收

基准日期：2026-10-11。客户端在 `d8a1586`、后端在 `631a706` 上开始；两仓库先执行 `git pull --ff-only`，再进行修改。需求来自 Todoist「Classing 课程表」的 34 个父任务/子任务，以及 2026-10-10 的三条项目评论。后续按用户要求核对并勾选了8项代码/功能定义任务，详细编号见追踪表；其余外部验收任务仍开放。

用户指定当前没有 Play 测试环境、Canvas/Moodle 账户或连接的手机/手表，先交付代码、本地测试与验收材料。因而下列材料区分自动验证、浏览器原型和待外部验收；这里没有人测、购买、真实 AI 识别或实机同步的成功率。

- [全部 34 项需求与证据](traceability.md)
- [Free / Pro 功能矩阵](feature-matrix.md)
- [独立构建、签名、Play Billing 方案](release.md)
- [10 款竞品桌面调研](research.md)
- [Canvas/Moodle 字段与授权边界](lms.md)
- [验收步骤与空白记录表](acceptance.md)
- [一周实验方案与待填写报告](experiment.md)
- [验证记录、命令与明确缺口](verification.md)
- [测试数量、lint及六份产物校验清单](verification-summary.json)
- [Today / Next / Homework 交互原型](index.html)
- [本地指标面板](metrics.html)
- [5 张截图、5 份 PDF 与独立标注](fixtures/manifest.json)

原型可以直接打开 HTML。也可从此目录运行 `python -m http.server 8026 --bind 127.0.0.1`，访问 `http://127.0.0.1:8026/`。文件和指标不会由这些页面上传。

当前 Global 仍是独立预览版本。后端对 Global AI 保留 `GLOBAL_PREVIEW` 门禁，Billing 尚未实现；不要把可构建的 APK 当成可上架销售的版本。Canvas 是临时 token 的原生只读 PoC，Homework 只展示导入预览和浏览器原型，没有实现持久化的作业管理器或 Apple Watch 应用。
