import SwiftUI

struct WatchAuthorizationView: View {
    @EnvironmentObject var store: AppStore
    @ObservedObject private var bridge = PhoneWatchBridge.shared
    @State private var busy = false
    var body: some View {
        NavigationStack {
            Group {
                if store.signedIn {
                    VStack(alignment: .leading, spacing: 22) {
                        Image(systemName: "applewatch").font(.system(size: 52)).foregroundStyle(ClassingPalette.accent)
                        Text("连接你的 Apple Watch").font(.title.bold())
                        Text("手表将使用当前 Classing 账号建立独立会话。仅在你刚刚通过自己的手表发起连接时授权。").foregroundStyle(.secondary)
                        Button { Task { busy = true; await bridge.approve(); busy = false } } label: {
                            HStack { if busy { ProgressView() }; Text("授权并连接手表").frame(maxWidth: .infinity) }.padding(10)
                        }.buttonStyle(.borderedProminent).disabled(busy)
                        Spacer()
                    }.padding(26).navigationTitle("Apple Watch")
                } else {
                    AccountView().safeAreaInset(edge: .top) { Label("登录后，在此确认连接自己的 Apple Watch", systemImage: "applewatch").font(.caption).foregroundStyle(.secondary).padding() }
                }
            }.toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { bridge.pendingAuthorization = nil } } }
        }.tint(ClassingPalette.accent)
    }
}
