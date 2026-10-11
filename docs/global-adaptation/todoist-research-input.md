# Todoist research input

Imported from the user's Classing project comments on 2026-10-11. These comments were posted on 2026-10-10; their claims are input evidence, not new runtime verification. See research.md for this run's refresh and limits.

【对比 GitHub 仓库 xtawa/WearOS_ClassingTimeTable｜代码核查与可执行计划｜2026-10-10】
仓库：https://github.com/xtawa/WearOS_ClassingTimeTable （默认分支main；此次静态读码，未在真实手机/手表运行）

已具备【源码存在，非线上质量担保】
• mobile/app/shared 结构；mobile Onboarding有6步及手工/ICS/JSON/云/备份入口：mobile/.../screen/MobileOnboarding.kt、OnboardingImportEditor.kt。
• ICS导入+RRULE及RECURRENCE-ID解析：shared/.../importer/IcsImportParser.kt、mobile/.../MobileParsingAndMappingUtils.kt。
• 课表投影包含单双周 ALL/ODD/EVEN、指定周范围、取消/调课/补课：shared/.../schedule/ScheduleProjector.kt。
• Wear OS 下一节课 Tile+表盘Complication、Room数据缓存：app/.../tile/NextClassTileService.kt、complication/NextClassComplicationService.kt、data/local/。
• 手机到手表有Google Wear Data Layer、全量/增量fingerprint、ACK与删除集；官方云手机/Wear两侧有源码：mobile/.../sync/WearDataLayerSyncPublisher.kt、WearSyncPayloadPlanner.kt、app/.../sync/WearOfficialCloudSyncCoordinator.kt。
• 云模型具逻辑版本与冲突合并：shared/.../sync/CloudSyncV2.kt；手机侧可见WebDAV、Google Drive和官方云客户端。

差距及风险（严格区分未见与确认）：
P0-1 轮换模型目前以周次+单双周+固定DayOfWeek为主；未见完整学校A/B日轮换、5/6/8-day cycle、假日后轮换指针、lecture+lab多种RRULE的完整产品化证明。
P0-2 课表投影源码可能漏显示“原日期在查询窗口外、被调课至窗口内”的课程：ScheduleProjector先遍历窗口内原日期，再生成新日期并过滤。须添加失败用例确认；不是运行实测确认Bug。
P0-3 Wear Sync具备真实代码，仍需实机端到端验收：0节、远程删除、离线→重连、换机、ACK迟到、增量转全量、夏令时、官方云到Wear。
P0-4 Onboarding是6步、可导ICS/JSON；尚未确认“截图/PDF→自动生成整表→逐项校验→一次确认”在首次建表中闭环。AskAI课程提案代码存在，但不能等同首启自动导入产品体验。
P1-5 本次仓库树未见Android手机桌面AppWidget实现，已有Wear Tile/Complication；未见直接Canvas/Moodle/Classroom/Blackboard接入实现。不要把系统日历或ICS称为LMS原生接入。
P1-6 docs/edition-architecture.md明确global仍为preview placeholder，自动化只生产cnRelease、Google Play Billing尚未加入（以本次main文档为准）。

推荐实验（指标为建议目标，非当前实测）：
A. 入门建表：招募12–20人，每人试“截图/PDF/ICS/手工”之一，计时到可正确显示下一节课；目标≥80%在120秒内完成，课时抽样准确≥99%，失败可人工纠正。
B. 复杂排课金标准：30组含AB周/AB日/6日轮转/停课/跨周调课/DST/跨时区案例，投影准确率目标100%；现有模型不能表达的标为功能缺口。
C. Wear可靠性：20轮每类断网重连、0节、换机、冲突与删除；最终一致性100%，在线P95同步延迟目标<30秒；绝不把已入队等同成功应用。
D. 下一节课可用性：测3秒内获得“课程/时间/教室/还有多久”，手机和Wear离线均可读；旧数据要提示最后同步时间。
E. 定价/门槛A/B：基础建表、下一节课、基础Widget免费 vs 首次流程中出现订阅提示；跟踪建表完成率、7日留存、付费意愿；价格策略待验证。
F. LMS需求：至少访谈10名用Canvas/Moodle等的国际学生，确认读作业、课程表和OAuth学校授权的真实可行性再开发。

建议顺序：P0排课与Wear一致性 → P0截图导入预览闭环 → P1手机Widget/手表离线体验 → P1选1个LMS做OAuth/API验证 → 国际版Billing与商店适配。
不宜重复造泛用成绩统计、番茄钟、社交模块。
注意：此次属竞品网页调研+仓库静态审查，未安装10个App、未实测Classing运行质量。

【海外差评证据与机会｜2026-10-10】
以下为用户报告/商店评论或厂商更新说明；单条报告不代表总体故障率；Subjects缺乏足够独立差评样本，不推断普遍缺陷。

1. MyStudyLife：2026年8月多名评论报告输入课时正确、显示时间错误；开发者回应已修复相关bug。优先测试时区/夏令时/跨端显示。证据：https://apps.apple.com/us/app/my-study-life-school-planner/id910639339?see-all=reviews
2. Power Planner：2026-01用户要求“保存并继续添加”以避免反复建作业；2023旧评报告课时整体偏移。证据：https://play.google.com/store/apps/details?id=com.barebonesdev.powerplanner
3. iStudiez Pro：用户报告无法创建重复作业、调整作业费步骤，订阅状态下更新缓慢；App Store页面显示很旧版本更新。证据：https://apps.apple.com/us/app/istudiez-pro-legendary-planner/id310636441
4. Class Timetable：用户报告iOS 26透明/着色Widget白字白底不可读；某些第三方综述列出同步/重复规则投诉（弱于一手）。证据：https://apps.apple.com/us/app/class-timetable-schedule-app/id425121147
5. Smart Timetable：2026-09用户报告课表消失，开发者承认近期版本存在少见重置bug；另有Widget更新后失效历史报告。证据：https://apps.apple.com/us/app/smart-timetable-study-planner/id1278473923?see-all=reviews
6. School Planner：2026-02用户报告加载广告或添加课程时崩溃，官方已重构云同步。证据：https://www.appbrain.com/app/daldev.android.gradehelper
7. myHomework：2025 Android用户报告进入秋季建表时冻结；有付费用户反馈作业Widget无法使用。证据：https://play.google.com/store/apps/details?id=com.myhomeowork ；https://apps.apple.com/us/app/303490844?see-all=reviews
8. Coursicle：不在学校支持名单则难以开始；用户抱怨Widget属付费、同门课同日重复课时受限。证据：https://apps.apple.com/us/app/coursicle/id1187418307?see-all=reviews
9. Untis Mobile：过去有替课提醒缺失、Widget状态/导航异常评论；官方2026日志涉及Widget与通知修复。证据：https://help.untis.at/hc/en-150/articles/360008379600-Untis-Mobile-Android-Release-Notes
10. Subjects：确认订阅及7天试用，但差评样本不足，暂不以“难用/贵”作为实证。证据：https://eyen.fr/subjects/

跨样本待验证假设：H1 手工建表时间与错误率过高；H2 调课/时区/同步错误破坏信任；H3 免费基础Widget及离线下节课可能影响留存；H4 学校数据源有限导致建表失败；H5 过早订阅墙妨碍用户完成首次任务。验证时需记录用户任务成功率，而不是仅统计差评关键词。

【Classing 国际版｜10 款海外课程表竞品矩阵｜2026-10-10】
口径：✅官网/商店明确支持；◐有限支持；?未核实（不等于没有）。以下是公开资料桌面调研，未逐个安装实测。

|App|首次建表|复杂轮换|Widget/Watch|同步|付费墙|LMS|
|---|---|---|---|---|---|---|
|MyStudyLife|手动；图/PDF/文字 AI，首次扫描免费|A/B、day rotation、block|Watch ?；Widget ?|iOS/Android/Web账号|免费5个未完成任务；MSL+无限AI/任务、日历同步|原生LMS未核实|
|Power Planner|手建学期/课程/作业|常规学期；高级轮换 ?|Widget ✅；Watch ?|跨平台账号|一次性Premium，免费无广告|?|
|iStudiez Pro|手建学期/课程/讲师|A/B、rotating、block ✅|Apple Watch ✅|多平台云同步需订阅|同步订阅|?|
|Class Timetable|手动+导入|多周 ✅|iOS Widget+Apple Watch ✅|iCloud|Pro：提醒/导出等|?|
|Smart Timetable|手动、复制、分享|1–4周、rotating ✅|Widget+Apple Watch ✅|iCloud、Apple Calendar|提醒及部分同步付费|?|
|School Planner（Andrea Dal Cin）|手动课/作业|A/B 可行（用户反馈）|Android Widget ✅，Watch ?|云同步（近期重构）|广告+内购|?|
|myHomework|手动/教师班级加入|time/block/period ✅|Widget ✅，Watch ?|多平台离线+云|免费广告；Premium $4.99/年（官网标价，地区可能变化）|Teachers.io；不是等同Canvas|
|Coursicle|选择学校→选课→组合课表|真实大学课程目录，任意轮换 ?|Widget Premium；Watch ?|账号、Google Calendar|核心排课及LMS同步免费；Widget/更多日程付费|Canvas/Blackboard/Brightspace/Moodle/Sakai/Google Classroom|
|Untis Mobile|选择学校→登录WebUntis|学校统一排课/替课/停课 ✅|Widget ✅，Watch ?|学校WebUntis、离线缓存|部分功能取决于学校模块|WebUntis原生|
|Subjects（Eyen）|手建/导入共享资料|A/B及多重复规则 ✅|25+ Widgets；Watch ?|Apple日历/订阅、分享|7天试用；月/年订阅或买断|原生LMS ?|

官方与商店来源：
MyStudyLife https://mystudylife.com/msl-plus/
Power Planner https://play.google.com/store/apps/details?id=com.barebonesdev.powerplanner
iStudiez https://apps.apple.com/us/app/istudiez-pro-legendary-planner/id310636441
Class Timetable https://apps.apple.com/us/app/class-timetable-schedule-app/id425121147
Smart Timetable https://apps.apple.com/us/app/smart-timetable-study-planner/id1278473923
School Planner https://play.google.com/store/apps/details?id=daldev.android.gradehelper
myHomework https://m.myhomeworkapp.com/pricing
Coursicle https://www.coursicle.com/blog/is-coursicle-free/
Untis https://help.untis.at/hc/en-150/articles/360015222380-Untis-Mobile-features-for-students-and-parents-legal-guardians
Subjects https://eyen.fr/subjects/

竞品基本结论：A/B周、Apple Watch、Widget、基础云同步、AI截图导入和LMS作业同步已有成熟竞品。Classing需用“准确导入+难例排课+设备间可靠一致性+下一节课可用”组合验证差异，不能以任一单点自称独家。
