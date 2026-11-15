import SwiftUI
import ClassingCore

struct OnboardingView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var step = 0
    @State private var semester = Date()
    @State private var liveActivities = false
    @State private var permissionMessage = ""
    private let titles = ["把课表，带在身边。", "从你的学期开始。", "你的课表，轻松带来。", "只在需要时，提醒你。"]
    private let subtitles = ["在 iPhone 上从容安排，在 Apple Watch 上抬腕查看。", "选好第一教学周，课程周次就会与学校保持一致。", "导入已有资料，或从第一门课慢慢开始。", "课前通知与实时活动，都由你决定是否开启。"]
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    HStack { Text("CLASSING").font(.caption.weight(.bold)).tracking(3); Spacer(); Text(String(format: "%02d / 04", step + 1)).font(.caption.monospacedDigit()).foregroundStyle(.secondary) }.foregroundStyle(ClassingPalette.accent)
                    illustration
                    VStack(alignment: .leading, spacing: 12) { Text(titles[step]).font(.system(.largeTitle, design: .rounded, weight: .bold)); Text(subtitles[step]).font(.subheadline).foregroundStyle(.secondary) }
                    details
                }.padding(26)
            }
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 16) {
                    HStack(spacing: 6) { ForEach(0..<4) { i in Capsule().fill(i == step ? ClassingPalette.accent : Color.primary.opacity(0.12)).frame(width: i == step ? 22 : 6, height: 6) } }
                    Button {
                        if step < 3 { withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.25)) { step += 1 } }
                        else { finish() }
                    } label: {
                        Text(step == 3 ? "进入 Classing" : "继续").font(.headline).frame(maxWidth: .infinity).padding(10)
                    }.buttonStyle(.borderedProminent).controlSize(.large).tint(ClassingPalette.accent).accessibilityIdentifier("onboarding-next")
                    if step > 0 { Button("上一步") { step -= 1 }.font(.subheadline).foregroundStyle(.secondary) }
                    Text("无需登录即可使用本地课表").font(.caption2).foregroundStyle(.secondary)
                }.padding(.horizontal, 26).padding(.vertical, 16).background(.regularMaterial)
            }
            .background(ClassingBackdrop())
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("稍后设置") { finish() }.font(.subheadline) } }
        }.interactiveDismissDisabled()
    }
    private var illustration: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 38).fill(ClassingPalette.accent.opacity(0.07)).frame(height: 170)
            VStack(spacing: 12) {
                Image(systemName: ["applewatch.watchface", "calendar", "square.and.arrow.down", "bell.badge"][step]).font(.system(size: 48, weight: .light)).foregroundStyle(ClassingPalette.accent)
                Text(["iPhone + Apple Watch", "每一周，都刚刚好", "ICS · JSON · 拍照 · 手动", "通知 · 锁屏 · 灵动岛"][step]).font(.caption.weight(.medium)).foregroundStyle(.secondary)
            }
        }.accessibilityHidden(true)
    }
    @ViewBuilder private var details: some View {
        switch step {
        case 0:
            VStack(alignment: .leading, spacing: 18) {
                feature("calendar", "课程与考试", "当前安排、时间和地点，一眼就看清。")
                feature("wifi.slash", "离线也在", "保存后的课表留在设备上，没网也能查。")
                feature("sparkles", "按需更聪明", "账号与智能服务可以之后再配置。")
            }
        case 1:
            DatePicker("第一教学周开始日期", selection: $semester, displayedComponents: .date).datePickerStyle(.graphical)
            Text("请选择学校第一教学周的开始日期。之后可在“设置 → 学期与外观”修改。")
                .font(.caption).foregroundStyle(.secondary)
        case 2:
            VStack(alignment: .leading, spacing: 16) {
                NavigationLink { ImportView() } label: { HStack { Label("导入我的课表", systemImage: "square.and.arrow.down"); Spacer(); Image(systemName: "chevron.right") }.padding(20).classingGlass() }.buttonStyle(.plain)
                Text(store.state.lessons.isEmpty ? "还没有准备好文件？直接继续，稍后也能导入或手动添加。" : "已保存 \(store.state.lessons.count) 门课程，可以继续设置。")
                    .font(.subheadline).foregroundStyle(.secondary)
                Text("图片识别需要联网；识别结果须由你核对并确认后才写入课表。")
                    .font(.caption).foregroundStyle(.secondary)
            }
        default:
            VStack(alignment: .leading, spacing: 18) {
                Toggle("在锁屏与灵动岛显示课程", isOn: $liveActivities)
                Text("实时活动显示当天当前或下一节课。系统计时会继续显示，课程更新依赖应用运行或系统允许的后台刷新。")
                    .font(.caption).foregroundStyle(.secondary)
                Button("开启课前通知") {
                    Task {
                        do { let allowed = try await LocalNotifications.permission(); store.setting("reminderEnabled", .bool(allowed)); permissionMessage = allowed ? "通知已开启，可在设置中调整提前时间。" : "暂未开启，之后可在系统设置中修改。" }
                        catch { permissionMessage = error.localizedDescription }
                    }
                }.buttonStyle(.bordered)
                if !permissionMessage.isEmpty { Text(permissionMessage).font(.caption).foregroundStyle(.secondary) }
                NavigationLink("了解 Classing Pro，按需升级") { SubscriptionView() }.font(.subheadline)
            }
        }
    }
    private func feature(_ icon: String, _ title: String, _ subtitle: String) -> some View {
        HStack(alignment: .top, spacing: 14) { Image(systemName: icon).font(.title3).foregroundStyle(ClassingPalette.accent).frame(width: 26); VStack(alignment: .leading, spacing: 4) { Text(title).font(.headline); Text(subtitle).font(.caption).foregroundStyle(.secondary) } }
    }
    private func finish() {
        store.mutate {
            $0.settings["semesterWeekStartDate"] = .string(SchoolTime.dateString(semester, zone: $0.settings.timezone))
            $0.settings["liveActivitiesEnabled"] = .bool(liveActivities)
            $0.settings["onboardingCompleted"] = .bool(true)
        }
    }
}
