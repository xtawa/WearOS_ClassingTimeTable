# 10 款竞品：流程、付费边界与待验证问题

2026-10-11 桌面调研：复核官方/商店页面，未安装10款App。此前Todoist评论完整保存在 [research input](todoist-research-input.md)，是2026-10-10的输入材料；以下分开写此次可核对的产品说明与仍待复核的差评。单条评论不代表故障率，不凭未提及功能断言“不支持”。

| App / 直接来源 | 首次建表与复杂排课 | Widget / Watch / 同步 | 付费与LMS | 差评证据边界与Classing验证 |
|---|---|---|---|---|
| [MyStudyLife](https://mystudylife.com/msl-plus/) | 常规、AB/轮换、block；AI快速建表已有竞品 | 多平台账号；具体Watch能力未核实 | 免费基础排课；Plus扩展AI/任务/日历 | 先前评论中的错时投诉需原评论逐条复核；做DST基准，不称AI导入独家 |
| [Power Planner](https://play.google.com/store/apps/details?id=com.barebonesdev.powerplanner) | 手建学期/课程/作业 | 跨平台账号、Widget | 免费核心、一次性Premium；高阶成绩/更多学期 | 当前商店可见“保存并继续添加”诉求与旧时区投诉/开发者修复回应；验证少步骤和时区，不外推总体差错 |
| [iStudiez Pro](https://apps.apple.com/us/app/istudiez-pro-legendary-planner/id310636441) | 常规、交替、rotating、block | Apple Watch；跨平台同步 | 同步订阅；原生LMS未核实 | 更新/重复作业投诉来自先前输入，未逐条再次复核；课程与作业不可混为Calendar |
| [Class Timetable](https://apps.apple.com/us/app/class-timetable-schedule-app/id425121147) | 多周课表、导入/导出 | Widget、Apple Watch、iCloud | Pro提醒等功能 | Widget低对比问题为先前评论输入；Classing验证深浅色/表盘色彩/AOD |
| [Smart Timetable](https://apps.apple.com/us/app/smart-timetable-study-planner/id1278473923) | 手建、复制/分享、周期课表 | Widget、Apple Watch、日历/同步 | 部分功能内购 | 消失/重置投诉属于先前具体评论输入，需脱敏复核；用备份恢复/删除/重连测试验证 |
| [School Planner](https://play.google.com/store/apps/details?id=daldev.android.gradehelper) | 手建课程、作业、考试 | Android Widget、云 | 广告/内购；学校原生LMS未核实 | 崩溃报告之前引用第三方聚合页，证据较弱；本轮只采官方商店能力，不给崩溃率 |
| [myHomework](https://m.myhomeworkapp.com/pricing) | time/block/period；学生/教师协作 | 多平台、Widget | 免费/付费分层；Teachers.io不等于Canvas | 冻结/Widget投诉待重核；验证离线建立与读取下一课。官网价格不作为Classing定价依据 |
| [Coursicle](https://www.coursicle.com/blog/is-coursicle-free/) | 选学校→选课程→排课 | 日历、付费Widget | 官方说明有LMS能力；支持学校是边界 | 学校名单/重复节次限制来自先前评论；验证没有授权也可手工/ICS完成 |
| [Untis](https://www.untis.at/en/products/webuntis/untis-mobile-app-1) | 学校WebUntis排课、替课/停课/教室变更 | Widget、学校系统；功能依学校模块 | 免费下载不等于所有学校模块免费 | [官方iOS日志](https://help.untis.at/hc/en-150/articles/360008379640-Untis-Mobile-iOS-Release-Notes) 2026-09-02列Widget入口等更新；可确认持续维护，不能据此断言所有用户故障 |
| [Subjects](https://eyen.fr/subjects/) | 官网索引可确认学生课程/作业与日历订阅 | 具体Widget数/Watch此次未完整刷新 | 先前订阅/试用信息待完整页面重核 | 此次官网直接抓取超时，仅有官网搜索摘要和先前输入；独立差评样本不足，不推断难用/贵 |

以上从每个来源衍生的说明保持简短，完整体验需要安装测试。重要的独立变量：学校是否授权、时区、排课例外、是否付费、OS/版本；比较实验不要把不同免费层/学校数据条件混在一起。

可验证的Classing方向（推断，不是市场胜出结论）：先核对再保存的整表导入；学校锚点与假日指针的轮换；离线Next与成功同步时间；终态ACK而非仅入队；无学校接口时免费回退。上述单点已有竞品，差异需通过 [一周实验](experiment.md) 和正确性测试组合证明。

下一轮差评采样：每款按版本/日期记录最近负评和开发者回应各自证据，保存来源与匿名摘要，追踪是否已修复；不足样本标“未证实”。不把先前聚合评论复制为本轮新实测结果。
