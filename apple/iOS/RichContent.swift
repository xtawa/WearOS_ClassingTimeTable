import SwiftUI
import WebKit
import ClassingCore
struct RichAnnouncementView: UIViewRepresentable {
    var html: String, base: URL
    func makeCoordinator() -> Coordinator { Coordinator(base: base) }
    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration(); configuration.defaultWebpagePreferences.allowsContentJavaScript = false
        configuration.websiteDataStore = .nonPersistent()
        let view = WKWebView(frame: .zero, configuration: configuration); view.navigationDelegate = context.coordinator
        // Public announcement content receives no account cookies or Authorization header.
        view.loadHTMLString("<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src 'self' data: https://\(base.host ?? "invalid"); style-src 'unsafe-inline';\"><style>body{font:17px -apple-system;color:#202330;line-height:1.7;padding:16px;overflow-wrap:anywhere}img{max-width:100%;height:auto}pre{white-space:pre-wrap}@media(prefers-color-scheme:dark){body{background:#1c2024;color:#f1f4f3}}</style></head><body>\(html)</body></html>", baseURL: base); return view
    }
    func updateUIView(_ view: WKWebView, context: Context) {}
    final class Coordinator: NSObject, WKNavigationDelegate {
        var base: URL
        init(base: URL) { self.base = base }
        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = action.request.url else { decisionHandler(.cancel); return }
            if action.navigationType == .linkActivated { decisionHandler(.cancel); if url.scheme == "https" { UIApplication.shared.open(url) }; return }
            decisionHandler(url.scheme == "about" || (url.scheme == "https" && url.host == base.host) ? .allow : .cancel)
        }
    }
}
struct AttachmentThumbnail: View {
    @EnvironmentObject var store: AppStore
    var attachment: JSONValue
    @State private var image: UIImage?
    @State private var unavailable = false
    var body: some View {
        VStack(alignment: .leading) { if let image = image { Image(uiImage: image).resizable().scaledToFit().frame(maxHeight: 180).clipShape(RoundedRectangle(cornerRadius: 12)) }; Text(attachment["name"].string).font(.caption); if unavailable { Text("附件不可用或已过期").font(.caption2).foregroundStyle(.secondary) } }
        .task { guard attachment["mimeType"].string.hasPrefix("image/") else { return }; do { let response = try await store.api.response("GET", "/api/v1/ai/attachments/\(attachment["attachmentId"].string)"); image = UIImage(data: response.data); unavailable = image == nil } catch { unavailable = true } }
    }
}
