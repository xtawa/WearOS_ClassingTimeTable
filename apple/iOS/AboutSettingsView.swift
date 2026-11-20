import SwiftUI
import PostHog
import ClassingCore

struct AboutSettingsView: View {
    @EnvironmentObject var store: AppStore
    @AppStorage("classing.about.developerMode") private var developerMode = false
    @State private var support = false
    @State private var updates = false
    @State private var feedback = false
    @State private var analyticsEnabled = !PostHogSDK.shared.isOptOut()
    @State private var testingConnection = false
    @State private var connectionResult: String?
    private var version: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.1.4" }
    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 12) {
                    Image(systemName: "calendar").font(.system(size: 42)).foregroundStyle(ClassingPalette.accent)
                    Text("ClassingTime").font(.title2.bold())
                    Text("V\(version)").font(.subheadline.monospacedDigit()).foregroundStyle(.secondary)
                }.padding(.vertical, 12)
                LabeledContent("更新通道", value: "测试版")
            }
            Section("资源") {
                NavigationLink("公告") { AnnouncementsView() }
                Button("检测更新") { updates = true }
                Button("意见反馈") { feedback = true }
                Button("客服微信") { support = true }
            }
            Section {
                Toggle("Dev Mode", isOn: $developerMode)
                if developerMode {
                    LabeledContent("构建", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1")
                    Text(Bundle.main.bundleIdentifier ?? "").font(.caption).textSelection(.enabled)
                }
            }
            if developerMode {
                Section {
                    Picker("当前服务", selection: Binding(
                        get: { store.state.settings["serverSelection"].string.isEmpty ? "OFFICIAL" : store.state.settings["serverSelection"].string },
                        set: { tag in
                            Task {
                                await store.switchServer(to: tag)
                                connectionResult = nil
                            }
                        }
                    )) {
                        Text("官方生产服务").tag("OFFICIAL")
                        Text("AirHost 独立服务").tag("AIRHOST")
                        Text("自定义服务").tag("CUSTOM")
                    }
                    if store.state.settings["serverSelection"].string == "AIRHOST" {
                        LabeledContent("服务地址", value: "https://classing-airhost.airhost.site")
                            .font(.caption)
                    } else if store.state.settings["serverSelection"].string == "CUSTOM" {
                        TextField("服务 URL", text: Binding(
                            get: { store.state.settings["serverCustomUrl"].string },
                            set: { store.setting("serverCustomUrl", .string($0)) }
                        )).textInputAutocapitalization(.never).keyboardType(.URL)
                        Button("应用自定义地址") {
                            Task {
                                await store.switchServer(to: "CUSTOM", customURL: store.state.settings["serverCustomUrl"].string)
                                connectionResult = nil
                            }
                        }
                    } else {
                        LabeledContent("服务地址", value: "https://api-classing.underflo.ink")
                            .font(.caption)
                    }
                    Button(testingConnection ? "正在检测…" : "测试服务连接") {
                        testingConnection = true
                        Task {
                            defer { testingConnection = false }
                            do {
                                let res = try await store.api.request("GET", "/api/v1/health", authorized: false)
                                connectionResult = "连接正常：\(res["service"].string.isEmpty ? "OK" : res["service"].string) (v\(res["version"].string.isEmpty ? "1.0" : res["version"].string))"
                            } catch {
                                connectionResult = "连接失败：\(error.localizedDescription)"
                            }
                        }
                    }.disabled(testingConnection)
                    if let result = connectionResult {
                        Text(result).font(.footnote).foregroundStyle(result.contains("失败") ? .red : .green)
                    }
                } header: {
                    Text("服务器环境 (Dev Mode)")
                } footer: {
                    Text("切换服务后使用独立的账号会话与云端记录，本地课表不会被覆盖。")
                }
            }
            Section {
                Toggle("允许使用情况统计", isOn: Binding(
                    get: { analyticsEnabled },
                    set: { enabled in
                        if enabled {
                            PostHogSDK.shared.optIn()
                        } else {
                            PostHogSDK.shared.optOut()
                        }
                        analyticsEnabled = !PostHogSDK.shared.isOptOut()
                    }
                ))
                Link("隐私政策", destination: URL(string: "https://lyxyy.notion.site/classing-user-policy")!)
                Link("使用条款", destination: URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!)
            } footer: {
                Text("收集启动及页面访问等使用信息，帮助改进 Classing。统计不关联账号，也不包含反馈文字或图片。关闭后停止使用情况统计；主动发送反馈和崩溃报告不受影响。")
            }
            Section { Text("© \(String(Calendar.current.component(.year, from: Date()))) Xihao Zhang。").font(.footnote).foregroundStyle(.secondary) }
        }.navigationTitle("关于 Classing")
        .postHogScreenView("About")
        .onAppear { analyticsEnabled = !PostHogSDK.shared.isOptOut() }
        .alert("当前为安装包测试版本", isPresented: $updates) {
            Link("查看 Apple 构建", destination: URL(string: "https://github.com/xtawa/WearOS_ClassingTimeTable/actions/workflows/apple-ci.yml")!)
            Button("知道了", role: .cancel) {}
        } message: { Text("后续测试版本通过 Apple 构建页面提供安装包。") }
        .sheet(isPresented: $feedback) { FeedbackView() }
        .sheet(isPresented: $support) {
            NavigationStack {
                VStack(spacing: 20) { Image("wechat_support_qr").resizable().scaledToFit().padding(24); Text("扫码添加微信，获取支持与反馈。").font(.subheadline).foregroundStyle(.secondary) }
                    .navigationTitle("客服微信").navigationBarTitleDisplayMode(.inline)
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { support = false } } }
            }.presentationDetents([.medium, .large])
        }
    }
}
