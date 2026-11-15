import SwiftUI
import ClassingCore

struct WidgetGalleryView: View {
    @EnvironmentObject var store: AppStore
    @State private var selection = 0
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("把下一站，放在眼前。").font(.title2.bold())
                Text("长按主屏幕添加 Classing 小组件；锁屏编辑中选择圆形或矩形组件。Apple Watch 可在表盘编辑中添加复杂功能。")
                    .font(.subheadline).foregroundStyle(.secondary)
                Picker("组件类型", selection: $selection) {
                    Text("桌面").tag(0); Text("锁屏与手表").tag(1); Text("实时活动").tag(2)
                }.pickerStyle(.segmented)
                let snapshot = store.agenda(at: store.now)
                if selection == 0 {
                    Text("下一节课 · 小号").font(.headline)
                    card(snapshot, size: .small, width: 158, height: 158)
                    Text("下一节课 · 中号").font(.headline)
                    card(snapshot, size: .medium, width: 338, height: 158)
                    Text("今日课表 · 大号").font(.headline)
                    card(snapshot, size: .large, width: 338, height: 354, agenda: true)
                } else if selection == 1 {
                    Text("锁屏 / 表盘 · 圆形").font(.headline)
                    card(snapshot, size: .circular, width: 80, height: 80)
                    Text("锁屏 / 智能叠放 · 矩形").font(.headline)
                    card(snapshot, size: .rectangular, width: 300, height: 92)
                    Text("锁屏 · 单行").font(.headline)
                    card(snapshot, size: .inline, width: 300, height: 44)
                    Text("显示内容来自本机课表。系统决定组件刷新时机；打开应用或修改课程后会请求刷新。")
                        .font(.caption).foregroundStyle(.secondary)
                } else {
                    Text("锁屏实时活动").font(.headline)
                    if let item = snapshot.primary {
                        let content = CourseActivityAttributes.ContentState(title: item.title, location: item.location, start: item.start, end: item.end, reference: snapshot.date, timezone: item.timezone, isExam: item.isExam)
                        CourseLiveActivityCard(state: content, liveTimer: false).foregroundStyle(.white).background(.black, in: RoundedRectangle(cornerRadius: 28))
                        Text("灵动岛紧凑布局示意").font(.headline)
                        HStack { Image(systemName: "calendar").foregroundStyle(.indigo); Spacer(); Text("\(snapshot.minutesRemaining)分").monospacedDigit() }
                            .font(.caption).padding(.horizontal, 18).frame(width: 210, height: 42).foregroundStyle(.white).background(.black, in: Capsule())
                    } else { Text("添加当天课程后，可以开启实时活动查看时间与地点。").font(.subheadline).foregroundStyle(.secondary) }
                    Toggle("启用课程实时活动", isOn: Binding(get: { store.state.settings["liveActivitiesEnabled"].bool }, set: { store.setting("liveActivitiesEnabled", .bool($0)) }))
                    Text("这里预览实际锁屏卡片布局。灵动岛的最终形态由设备和系统决定；课程状态更新依赖应用运行或获准的后台刷新。")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }.padding(20)
        }.background(ClassingBackdrop()).navigationTitle("小组件").navigationBarTitleDisplayMode(.inline)
    }
    private func card(_ snapshot: AgendaSnapshot, size: AgendaWidgetSize, width: CGFloat, height: CGFloat, agenda: Bool = false) -> some View {
        AgendaWidgetCard(snapshot: snapshot, size: size, agenda: agenda, liveTimer: false)
            .padding(16).frame(maxWidth: width).frame(height: height)
            .background(.background, in: RoundedRectangle(cornerRadius: 26))
            .overlay(RoundedRectangle(cornerRadius: 26).stroke(.primary.opacity(0.07)))
            .accessibilityIdentifier("widget-preview")
    }
}
