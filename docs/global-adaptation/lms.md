# LMS 原生只读接入

优先 Canvas：REST 合同清晰，已有 OkHttp 与课表导入草稿可复用。Moodle 第二：学校需启用外部服务及函数，不假设所有学校开放相同 API/OAuth。Google Classroom、Blackboard、PowerSchool 继续按学校授权与真实需求排序，本轮没有编写猜测的接口。

| 领域 | Canvas 官方字段/端点 | Classing 映射 | Moodle 待验证函数 |
|---|---|---|---|
| 课程 | `GET /api/v1/courses`，`id/name`，active enrollment | `LmsCourse`，独立课程身份 | `core_enrol_get_users_courses` |
| 课时 | `GET /api/v1/calendar_events?type=event`，`context_code/id/title/start_at/end_at/location_name` | 有效时间才进 `LmsMeeting`；单次日期规则，用户选择后进草稿 | `core_calendar_get_calendar_events`；事件可能不是课时，须学校核对 |
| 作业 | 同端点 `type=assignment` 的calendar视图，`start_at` 为截止时间 | `LmsAssignment`，单独只读列表；缺截止时间不造时间 | `mod_assign_get_assignments`，学校授权后再做真实映射 |
| 全部作业 | Canvas assignments API 是后续正式实现的数据源 | 本 PoC 只读取选定日期窗口的calendar视图，不宣称完整作业同步 | 模块访问权限与课程可见性需复核 |
| 分页 | HTTP `Link` 的 `rel=next` | 同HTTPS origin、仅 `/api/v1/`；最多50页/5000记录，阻止循环和跨域带token | 不假定 REST 响应采用 Canvas Link |

Canvas 每批最多10个 `context_codes[]`；PoC 限定日期范围，忽略已删/隐藏事件，对全天、无结束时间、无效时间显式警告。相同事件 ID 去重；课时与作业分离。暂不生成没有排课事件的课程节次。用户需选择希望导入的课时；已有课程与导入的课时在草稿中核对，不能按相似课名就合并 Lecture/Lab。

授权边界：正式 Canvas OAuth 需要学校/机构开发者 key 和登记回调，学校可限制启用与scope。PoC只接受用户在本机输入的临时token、只发GET、不跟重定向、不保存token，完成/失败后清空UI token。它不是生产OAuth授权界面。Moodle 需要管理员允许外部服务、关联函数与token/用户权限；OAuth登录本身不等于Web Service授权。

失败回退：401/403提示授权不足；429稍后重试；非法Link/过大响应/解析失败不提交草稿。始终可以退出到手工或ICS导入，不写回 LMS、不爬取登录页面、不获取成绩/提交/学生名单。只有用户确认后才写本地课表。

本轮验证：5个自动用例，含3组合成数据、分页/重复、作业分离、撤销权限与危险Link。未取得3组真实/获授权学校数据，未完成10人访谈。访谈/授权记录见 [验收材料](acceptance.md)。Homework正式持久化与后台刷新须另根据访谈决定。

依据：[Canvas calendar events](https://developerdocs.instructure.com/services/canvas/resources/calendar_events)、[Canvas courses](https://developerdocs.instructure.com/services/canvas/resources/courses)、[Canvas assignments](https://developerdocs.instructure.com/services/canvas/resources/assignments)、[Canvas pagination](https://developerdocs.instructure.com/services/canvas/basics/file.pagination)、[Canvas OAuth2](https://developerdocs.instructure.com/services/canvas/oauth2/file.oauth)、[Moodle External services](https://moodledev.io/docs/5.0/apis/subsystems/external)、[Moodle API functions](https://docs.moodle.org/dev/index.php?title=Web_service_API_functions)。读取于2026-10-11，学校具体权限须以授权测试为准。
