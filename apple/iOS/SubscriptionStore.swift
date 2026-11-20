import SwiftUI
import StoreKit

@MainActor final class SubscriptionStore: ObservableObject {
    static let shared = SubscriptionStore()
    static let testMonthly = "com.xtawa.classingtime.test.pro.monthly"
    static let testYearly = "com.xtawa.classingtime.test.pro.yearly"
    @Published var products: [Product] = []
    @Published var busy = false
    @Published var message = ""
    @Published var verifiedProductIDs: Set<String> = []
    private var updates: Task<Void, Never>?
    var localTesting: Bool {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains("--storekit-testing")
        #else
        return false
        #endif
    }
    var productIDs: [String] {
        if localTesting { return [Self.testMonthly, Self.testYearly] }
        return ["ClassingProMonthlyID", "ClassingProYearlyID"].compactMap { Bundle.main.object(forInfoDictionaryKey: $0) as? String }.filter { !$0.isEmpty && !$0.hasPrefix("$(") }
    }
    // Cloud Pro is owned by the membership service. Until its Apple verification
    // endpoint and real products exist, a production build must not take payment.
    var checkoutAvailable: Bool { localTesting && !products.isEmpty }
    init() {
        updates = Task { [weak self] in
            for await result in StoreKit.Transaction.updates {
                guard let self else { return }
                if case .verified(let transaction) = result, self.productIDs.contains(transaction.productID) {
                    await self.refreshEntitlements()
                    if self.localTesting && transaction.environment == .xcode { await transaction.finish() }
                }
            }
        }
    }
    func load() async {
        busy = true; defer { busy = false }
        guard !productIDs.isEmpty else { products = []; message = "App Store 订阅即将开放。你可以继续免费使用本地课表。"; return }
        do {
            products = try await Product.products(for: productIDs).filter { $0.type == .autoRenewable }.sorted { $0.price < $1.price }
            message = products.isEmpty ? "暂时无法获取订阅方案，请稍后重试。" : localTesting ? "StoreKit 本地测试，价格与交易仅用于测试。" : "订阅尚未开放，开通时间请留意应用公告。"
            await refreshEntitlements()
        } catch { message = error.localizedDescription }
    }
    func purchase(_ product: Product) async {
        guard checkoutAvailable else { message = "订阅尚未开放。"; return }
        busy = true; defer { busy = false }
        do {
            switch try await product.purchase() {
            case .success(let result):
                guard case .verified(let transaction) = result, transaction.environment == .xcode else { message = "交易未通过本地测试验证。"; return }
                PurchaseReporter.report(result)
                await refreshEntitlements(); await transaction.finish()
                message = "本地测试订阅已验证"
            case .pending: message = "购买等待批准，批准后会自动更新。"
            case .userCancelled: message = "已取消购买。"
            @unknown default: message = "购买尚未完成。"
            }
        } catch { message = error.localizedDescription }
    }
    func restore() async {
        busy = true; defer { busy = false }
        do {
            try await StoreKit.AppStore.sync()
            await refreshEntitlements()
            message = verifiedProductIDs.isEmpty ? "没有找到有效的 Apple 订阅。" : localTesting ? "本地测试订阅已恢复" : "已找到 Apple 订阅，云端权益需由服务端验证。"
        } catch { message = error.localizedDescription }
    }
    func refreshEntitlements() async {
        var ids = Set<String>()
        for await result in StoreKit.Transaction.currentEntitlements {
            guard case .verified(let transaction) = result, productIDs.contains(transaction.productID), transaction.revocationDate == nil,
                  transaction.expirationDate.map({ $0 > Date() }) ?? true else { continue }
            ids.insert(transaction.productID)
        }
        verifiedProductIDs = ids
    }
}
