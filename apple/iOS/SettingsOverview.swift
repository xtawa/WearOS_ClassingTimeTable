import SwiftUI
import ClassingCore

struct SettingsView: View {
    @EnvironmentObject var store: AppStore
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("课程、提醒与设备，按你的习惯安排。")
                    .font(.subheadline).foregroundStyle(.secondary)
                SettingsIsland {
                    SettingsLink(store.signedIn ? accountName : "登录 Classing 账号", icon: "person.crop.circle", detail: store.signedIn ? store.account["email"].string : "本地课表无需登录") { AccountView() }
                    Divider().padding(.leading, 62)
                    SettingsLink("Classing Pro", icon: "sparkles", detail: store.officialCloudAllowed ? "当前账号的 Pro 权益已生效" : "了解云同步与智能服务") { SubscriptionView() }
                }
                VStack(alignment: .leading, spacing: 8) {
                    sectionLabel("日常安排")
                    SettingsIsland {
                        SettingsLink("学期与课程", icon: "calendar", detail: "\(store.state.lessons.count) 门课程 · 周次与临时安排") { CourseSettingsView() }
                        Divider().padding(.leading, 62)
                        SettingsLink("提醒与简报", icon: "bell", detail: store.state.settings["reminderEnabled"].bool ? "课前 \(store.state.settings["reminderMinutes"].int) 分钟提醒" : "按需开启课前提醒") { NotificationSettingsView() }
                        Divider().padding(.leading, 62)
                        SettingsLink("外观与组件", icon: "paintpalette", detail: "主题 · 小组件 · 实时活动") { AppearanceSettingsView() }
                    }
                }
                VStack(alignment: .leading, spacing: 8) {
                    sectionLabel("连接与助手")
                    SettingsIsland {
                        SettingsLink("同步与设备", icon: "arrow.triangle.2.circlepath", detail: "云同步 · Apple Watch · 系统日历") { ConnectivitySettingsView() }
                        Divider().padding(.leading, 62)
                        SettingsLink("Ask Classing", icon: "sparkles", detail: "助手偏好、用量与自备模型") { AssistantPreferencesView() }
                    }
                }
                VStack(alignment: .leading, spacing: 8) {
                    sectionLabel("资料与帮助")
                    SettingsIsland {
                        SettingsLink("数据与备份", icon: "archivebox", detail: "导入课表、导出与恢复") { DataSettingsView() }
                        Divider().padding(.leading, 62)
                        SettingsLink("关于 Classing", icon: "info.circle", detail: "版本、公告与隐私政策") { AboutSettingsView() }
                    }
                }
            }.padding(20)
        }.background(ClassingBackdrop()).navigationTitle("设置").navigationBarTitleDisplayMode(.inline)
    }
    private var accountName: String { store.account["username"].string.isEmpty ? "我的账号" : store.account["username"].string }
    private func sectionLabel(_ title: String) -> some View { Text(title).font(.caption.weight(.semibold)).foregroundStyle(.secondary).padding(.leading, 8) }
}

private struct SettingsIsland<Content: View>: View {
    var content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        VStack(spacing: 0) { content }
            .background(.background, in: RoundedRectangle(cornerRadius: 26))
            .overlay(RoundedRectangle(cornerRadius: 26).stroke(.primary.opacity(0.045)))
    }
}
private struct SettingsLink<Destination: View>: View {
    var title: String, icon: String, detail: String
    var destination: Destination
    init(_ title: String, icon: String, detail: String, @ViewBuilder destination: () -> Destination) {
        self.title = title; self.icon = icon; self.detail = detail; self.destination = destination()
    }
    var body: some View {
        NavigationLink { destination } label: {
            HStack(spacing: 14) {
                Image(systemName: icon).font(.title3).foregroundStyle(ClassingPalette.accent).frame(width: 28)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline).foregroundStyle(.primary)
                    Text(detail).font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                }.frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
            }.padding(18).contentShape(Rectangle())
        }.buttonStyle(.plain)
    }
}

struct CourseSettingsView: View {
    @EnvironmentObject var store: AppStore
    var body: some View {
        List {
            Section { Text("已保存 \(store.state.lessons.count) 门课程与 \(store.state.exams.count) 场考试").font(.subheadline).foregroundStyle(.secondary) }
            Section("课程安排") {
                NavigationLink("学期设置") { SemesterSettingsView() }
                NavigationLink("课表与课程") { TimetableView() }
                NavigationLink("调课与临时安排") { ChangesView() }
            }
            Section("查看统计") { NavigationLink("课程密度热力图") { CourseHeatmapView() } }
        }.navigationTitle("学期与课程")
    }
}
struct AppearanceSettingsView: View {
    @EnvironmentObject var store: AppStore
    var body: some View {
        Form {
            Section("外观") {
                Picker("主题", selection: Binding(get: { store.state.settings["appearance"].string }, set: { store.setting("appearance", .string($0)) })) {
                    Text("跟随系统").tag("system"); Text("浅色").tag("light"); Text("深色").tag("dark")
                }
                Text("字体大小与减少动态效果跟随系统设置。").font(.caption).foregroundStyle(.secondary)
            }
            Section("随时查看") {
                NavigationLink("小组件") { WidgetGalleryView() }
                NavigationLink("实时活动与灵动岛") { LiveActivitySettingsView() }
            }
        }.navigationTitle("外观与组件")
    }
}
struct LiveActivitySettingsView: View {
    @EnvironmentObject var store: AppStore
    var body: some View {
        Form {
            Section {
                Toggle("显示课程实时活动", isOn: Binding(get: { store.state.settings["liveActivitiesEnabled"].bool }, set: { store.setting("liveActivitiesEnabled", .bool($0)) }))
            } footer: { Text("在锁屏与灵动岛查看当天当前或下一节课。关闭后会结束课程实时活动。") }
            Section { NavigationLink("查看布局") { WidgetGalleryView(selection: 2) } }
            Section { Text("课程状态会在应用运行或系统允许后台刷新时更新。到达课程边界后，可打开课表查看最新安排。").font(.caption).foregroundStyle(.secondary) }
        }.navigationTitle("实时活动")
    }
}
struct ConnectivitySettingsView: View {
    var body: some View {
        List {
            Section("保持一致") { NavigationLink("云同步") { CloudSettingsView() }; NavigationLink("Apple Watch") { WatchSettingsView() } }
            Section("其他日历") { NavigationLink("系统日历") { CalendarSettingsView() } }
        }.navigationTitle("同步与设备")
    }
}
struct AssistantPreferencesView: View {
    var body: some View {
        List {
            Section("Classing 助手") { NavigationLink("助手偏好与用量") { AssistantSettingsView() } }
            Section { NavigationLink("自备 AI 模型") { BYOKSettingsView() } } footer: { Text("使用自己的 AI 服务时，在手机配置模型与密钥，也可加密发送到手表。") }
        }.navigationTitle("Ask Classing")
    }
}
struct DataSettingsView: View {
    var body: some View {
        List {
            Section { NavigationLink("导入课表") { ImportView() } }
            Section { NavigationLink("备份、导出与恢复") { BackupView() } } footer: { Text("修改前的本地快照也保存在这里。恢复时会先备份当前课表。") }
        }.navigationTitle("数据与备份")
    }
}
struct AboutSettingsView: View {
    var body: some View {
        List {
            Section { Text("Classing").font(.title2.bold()); Text("iPhone 与 Apple Watch 课程表").foregroundStyle(.secondary); LabeledContent("版本", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0") }
            Section { NavigationLink("公告") { AnnouncementsView() }; Link("隐私政策", destination: URL(string: "https://lyxyy.notion.site/classing-user-policy")!); Link("使用条款", destination: URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!) }
        }.navigationTitle("关于 Classing")
    }
}
