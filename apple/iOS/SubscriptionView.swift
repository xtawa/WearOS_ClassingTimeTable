import SwiftUI
import StoreKit

struct SubscriptionView: View {
    @EnvironmentObject var store: AppStore
    @ObservedObject private var purchase = SubscriptionStore.shared
    @Environment(\.openURL) private var openURL
    @State private var selectedID = ""
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                HStack { Text("CLASSING").font(.caption.weight(.bold)).tracking(3); Text("PRO").font(.caption.weight(.bold)).padding(.horizontal, 9).padding(.vertical, 5).background(ClassingPalette.accent.opacity(0.12), in: Capsule()) }.foregroundStyle(ClassingPalette.accent)
                Text("让每一段安排，\n都彼此连接。").font(.system(.largeTitle, design: .rounded, weight: .bold))
                Text("本地课表一直可用。需要更多跨设备与智能服务时，再选择适合你的 Pro。")
                    .font(.subheadline).foregroundStyle(.secondary)
                if store.officialCloudAllowed { Label("当前账号的 Pro 权益已生效", systemImage: "checkmark.seal.fill").font(.subheadline).foregroundStyle(.green) }
                VStack(alignment: .leading, spacing: 20) {
                    benefit("icloud", "官方云同步", "让手机与手表的课程、考试和设置保持一致。")
                    benefit("sparkles", "更多 AI 额度", "使用当前账号可用的 Pro 模型与额度。")
                    benefit("person.crop.circle.badge.checkmark", "同一个 Classing 账号", "权益以账号服务实际确认的会员状态为准。")
                }.padding(22).background(.background, in: RoundedRectangle(cornerRadius: 28))
                if purchase.localTesting { Label("StoreKit 本地测试", systemImage: "hammer").font(.caption).foregroundStyle(.orange) }
                if purchase.products.isEmpty {
                    HStack(spacing: 12) { unavailablePlan("月付"); unavailablePlan("年付") }
                } else {
                    ForEach(purchase.products, id: \.id) { product in plan(product) }
                }
                if !purchase.message.isEmpty { Text(purchase.message).font(.caption).foregroundStyle(.secondary).accessibilityIdentifier("purchase-status") }
                Button {
                    if let product = purchase.products.first(where: { $0.id == selectedID }) { Task { await purchase.purchase(product) } }
                } label: {
                    HStack { Spacer(); if purchase.busy { ProgressView() }; Text(purchase.checkoutAvailable ? "继续订阅" : "订阅即将开放").fontWeight(.semibold); Spacer() }.padding(10)
                }.buttonStyle(.borderedProminent).controlSize(.large).tint(ClassingPalette.accent)
                    .disabled(purchase.busy || !purchase.checkoutAvailable || selectedID.isEmpty).accessibilityIdentifier("subscribe")
                HStack {
                    Button("恢复购买") { Task { await purchase.restore() } }.disabled(purchase.busy)
                    Spacer()
                    Button("管理订阅") { openURL(URL(string: "https://apps.apple.com/account/subscriptions")!) }
                }.font(.subheadline)
                Text("订阅会自动续期，除非在当前周期结束前取消。实际价格、周期与确认信息以 App Store 购买页面为准。可在 Apple 账户的订阅设置中管理或取消。")
                    .font(.caption2).foregroundStyle(.secondary)
                HStack { Link("隐私政策", destination: URL(string: "https://lyxyy.notion.site/classing-user-policy")!); Spacer(); Link("使用条款", destination: URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!) }.font(.caption)
            }.padding(24)
        }.background(ClassingBackdrop()).navigationTitle("Classing Pro").navigationBarTitleDisplayMode(.inline)
            .task { await purchase.load(); selectedID = purchase.products.first?.id ?? "" }
    }
    private func benefit(_ icon: String, _ title: String, _ detail: String) -> some View {
        HStack(alignment: .top, spacing: 14) { Image(systemName: icon).font(.title3).foregroundStyle(ClassingPalette.accent).frame(width: 26); VStack(alignment: .leading, spacing: 4) { Text(title).font(.headline); Text(detail).font(.caption).foregroundStyle(.secondary) } }
    }
    private func unavailablePlan(_ title: String) -> some View {
        VStack(alignment: .leading, spacing: 8) { Text(title).font(.headline); Text("即将开放").font(.subheadline).foregroundStyle(.secondary) }.frame(maxWidth: .infinity, alignment: .leading).padding(20).background(.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 22))
    }
    private func plan(_ product: Product) -> some View {
        Button { selectedID = product.id } label: {
            HStack {
                VStack(alignment: .leading, spacing: 5) { Text(product.displayName).font(.headline); Text(product.subscription?.subscriptionPeriod.unit == .year ? "每年自动续订" : "每月自动续订").font(.caption).foregroundStyle(.secondary) }
                Spacer(); Text(product.displayPrice).font(.title3.weight(.semibold)); Image(systemName: selectedID == product.id ? "checkmark.circle.fill" : "circle").foregroundStyle(ClassingPalette.accent)
            }.padding(20).background(selectedID == product.id ? ClassingPalette.accent.opacity(0.09) : Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 22))
        }.buttonStyle(.plain).accessibilityIdentifier("plan-" + product.id)
    }
}
