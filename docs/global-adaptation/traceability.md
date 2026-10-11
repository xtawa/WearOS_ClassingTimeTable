# Todoist 全部 34 项追踪

来源：Classing 课程表 `6hhVqH2h5Hj5j3fG`，2026-10-11读取。有2个section和父子任务，没有另一个子project列表。每行对应原任务ID；“本地”不表示完整外部验收通过。

2026-10-11按用户请求核对验收条件后，Todoist已确认勾选第2、4、11、12、13、14、15、33项，共8项。其余任务仍开放；未把完成父任务作为批量关闭未验收子任务的手段。

| # / ID | 原需求 | 本轮交付/证据 | 未完成的外部验收或产品缺口 |
|---|---|---|---|
| 1 `6hhVqJ7j6JjQPHPG` | 调研10个海外App流程与差评 | [research](research.md)，官方/商店桌面矩阵与原评论分离 | 未安装10款；Subjects全文超时；部分差评未逐条新复核 |
| 2 `6hjCMVFvp4vJJJPG` | OAuth invalid_scope/admin rollback | 后端混合scope GET过滤无权管理员项、同意页提示；POST仍严验；`TestOAuthEligibleUserMixedScopesOfferExplicitPersonalConsent`等OAuth回归 | 有效Pro/Legacy个人scope可继续；free/admin-only仍按原资格拒绝；未在线连接验证 |
| 3 `6hhVqJ8C9wXXxFvp` | 国际版MVP/不做清单 | [feature matrix](feature-matrix.md)，复用排课/导入/Next/Watch/云 | 正式商店/Billing未交付 |
| 4 `6hjCP8fHCxchmCgp` | Free/Pro及订阅墙 | 明确免费本地建表与Widget、成本服务资格、延期项目 | 定价/付费接受度待研究 |
| 5 `6hjCP8gqQfvFwwvp` | Global构建/签名/Billing上线检查 | 独立Global测试/lint构建workflow；[release](release.md) | CI未远程运行；Billing未实现、购买/恢复未测试；Global仍preview |
| 6 `6hhVqJC759FQc2GG` | 10秒导入完整流程 | 首启图片/PDF/文字入口接现有AI合同；草稿校验编辑→保存 | 10秒不是实测；无真实AI/Global AI仍被后端门禁禁用 |
| 7 `6hjCP8ffVF63rRqG` | 图片/PDF整表草稿 | `OnboardingAiImport`复用图片API；逐页PDF原子提交；5PNG+5PDF及人工标注 | 合成素材未调用真实模型；真实准确率/每页费用体验待验 |
| 8 `6hjCP8gf325mv6Pp` | ICS重复/例外兼容 | `IcsOccurrenceExpanderTest`15组、parser非法REC-ID拒绝、按具体日期建表 | 系统日历对照未执行；月/年/ordinal/RDATE/EXRULE等有警告且不近似 |
| 9 `6hjCP8h9W5c5qQHp` | 导入预览/冲突/纠错 | `OnboardingImportEditor`全条目编辑删除、推断/未解析/冲突警告；取消不保存 | 实际用户发现/修正错误的任务未做 |
| 10 `6hjCP8hghX3Qf7pp` | 12–20人首次建表 | [acceptance](acceptance.md)+usability.csv+计时/会话事件 | 样本0；≥80%/120秒待测 |
| 11 `6hhVqJFJcWv9m2qG` | 海外复杂排课 | shared规则、两客户端字段/持久化/同步、Go排课匹配、Room3→4 | 老版本不理解advanced字段，验收需更新手机/Wear两端 |
| 12 `6hjCP8gHxVgcfwHp` | AB日/5/6/8日轮换 | 显式周期/锚点/学校日/假日指针/结束日；金标准 | 学校实际轮换政策待核对 |
| 13 `6hjCP8g4mPFhPqHp` | Lecture/Lab/学段 | 每meeting独立规则、courseGroup/meetingType/termName，任意时段/具体日期 | term是meeting边界/标签，不是新增学籍管理模块 |
| 14 `6hjCP8cfWfH9M6jG` | 跨查询窗口调课 | `ScheduleProjectorTest`调入/调出回归并修复；同周/跨周窗口 | 实机同步后的投影待端到端 |
| 15 `6hjCP8jcc53xrXvG` | 30组金标准 | `ScheduleGoldenTest`30组人工预期；ICS DST/异常另有15组；Go10组 | 源学校时区/旅行双时区不支持；独立系统日历oracle待补 |
| 16 `6hhVqJCpCqCghPPp` | Next作为第一层级 | Home主卡复用并加入新鲜度；查下一课至366天/学段边界 | 3秒找到信息待人测 |
| 17 `6hjCP8cxFmgGhJfG` | 同屏时间/教室/倒计时/阶段 | 现有HomeStateResolver阶段测试+交互原型 | 目标人群可读性待测 |
| 18 `6hjCP8cmPRmvGhJG` | 离线/新鲜度/未建表区分 | 本地投影；无课/未建表依据实际prefs区分；云失败不覆盖成功时间；Widget/Home显示过期 | 未增加实时网络探测徽标，依据本地缓存/时间说明；实机断网恢复待测 |
| 19 `6hhVqJGmHQRJPpFG` | Wear/Apple Watch规划 | Wear Tile与现有Complication；圆/方原型+验收规范 | Apple Watch无iOS工程；Wear实机/AOD未做 |
| 20 `6hjCP8hFvp89jxQG` | Android手机Widget | 新AppWidget小/中尺寸、light/dark、Chronometer、编辑/时间/启动刷新 | WorkManager非精确换课；重启/Doze/厂商后台须实机 |
| 21 `6hjCP8g2Pqqr8H6G` | Wear Tile/Complication可读性刷新 | Tile两行/高对比/紧凑布局；原型验证长名称；现有Complication复用 | 表盘裁切/主题/空数据/AOD/刷新仍未实机证明 |
| 22 `6hhVqJJHQQW8fVPG` | LMS优先级 | [lms](lms.md)：Canvas先、Moodle次，其他依授权需求 | 未做所有LMS接口 |
| 23 `6hjCP8cm78GPh8QG` | API/OAuth限制+10访谈 | 官方字段/权限映射与降级说明；lms-interviews.csv | 10人访谈未发生、学校管理员权限未获得 |
| 24 `6hjCP8cgfWwjc9rp` | LMS原生只读PoC/回退 | Canvas GET课程/课时/作业分离，分页守卫；3组合成+2权限边界测试 | 临时token PoC；3套真实授权数据及正式OAuth未验证 |
| 25 `6hhVqJM7Mg7Fm2Pp` | 同步可靠性清单 | [acceptance](acceptance.md) 8类场景、每类20轮表格 | 无实机轮次和P95结果 |
| 26 `6hjCP8fqjCgpqJ6p` | 云→Wear 0节 | mapper对象/数组兼容、异常域不默认为空、tombstone；已有applier/full/delta回归 | 真实账户权限/云数据未取；三端端到端节数待核对 |
| 27 `6hjCP8fwwh27CHPp` | 增量/删除/ACK顺序 | 单节点pending在发送前持久化，多节点回退全量；基线revision单调/TTL；queued非applied；新ACK回归+既有planner/applier测试 | 多设备真实传输与时钟差异待验 |
| 28 `6hjCP8fcJgm34fVG` | 20轮断网/换机/冲突恢复 | 既有reset/full补偿与CAS回归复用；可靠性记录模板 | 实测0轮，100%一致和<30秒不作结果 |
| 29 `6hjCP8m36VXQxRJp` | DST/跨区/重启提醒 | ICS TZID/DST测试、提醒重建广播/Widget刷新接入 | wall-clock存储无源学校时区；旅行策略、提醒防重复/设备一致未完成验收 |
| 30 `6hhVqJJVVWfjj4wp` | Today/Next/Homework低保真 | [index.html](index.html)，沿用品牌tokens、三层信息 | Homework仅只读原型/导入预览 |
| 31 `6hjCP8fMHgqvmxPG` | 原型+5人对照 | 浏览器检查queued/applied、非法时间阻止保存、取消保留数据、长名称/主题 | 用户样本0；没有5人误操作结果 |
| 32 `6hhVqJQ8Rh873P4G` | 零维护方向验证 | [experiment](experiment.md)，指标与采样/决策口径 | 方向仍是假设 |
| 33 `6hjCP8hP5PgGj82p` | 最少数据埋点/看板 | 默认关闭本地事件；28日/1000条；会话纠错、同步、Next/active；导出与HTML面板、撤销/聚合测试 | 自愿样本偏差；没有真实指标数据；人工维护/Next正确率需研究员记录 |
| 34 `6hjCP8cwqH34xjXG` | 一周零维护/付费墙A/B | 实验分组方案、weekly-observation.csv、待填报告/局限 | 未接生产A/B开关/支付，未开展实验 |

本轮交付用于代码审查与后续验收。人测、购买、真实AI、真实LMS数据、系统日历对照、实机刷新与旅行时区模型仍不具备完整成功证据，因此对应任务保持开放。
