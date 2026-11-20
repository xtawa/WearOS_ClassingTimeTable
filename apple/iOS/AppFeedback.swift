// AppFeedback.swift — an in-app feedback form. What the person writes,
// plus up to four photos, goes to this application's independent platform environment.
//
// Feedback transport uses no account or Info.plist entries: a public
// ingestion key authorizes submission only. This app adds localized UI and
// a fixed PostHog screen name. iOS 17 or later.
//
// With the App Attest capability and `teamID` set, each entry carries the
// device's proof (AirDeviceProof.swift). Without them — and always on the
// Simulator — feedback is still sent and stored, marked unverified.
//
// Usage — present the form from anywhere:
//   .sheet(isPresented: $showFeedback) { FeedbackView() }
// or send without the form:
//   try await FeedbackClient.send(message: "…", images: [uiImage])
//
// User-facing strings follow the app's Simplified Chinese UI.

import PhotosUI
import PostHog
import SwiftUI
import UIKit

enum FeedbackConfig {
    /// The `feedbackEndpoint` and public `key` returned by `workers app-key`.
    static let endpoint = URL(string: "https://apps.air.build/v1/ingest/feedback")!
    static let appKey = "ak_production_ZVF6r75cvfvDDFdZeYTz-N9Rf-r5Ofw4Wnu55bi_PGs"
    /// The Apple team id (DEVELOPMENT_TEAM) for App Attest; nil sends no proof.
    static let teamID: String? = nil
    static let maximumImages = 4
    static let maximumMessageLength = 4000
}

enum FeedbackClient {
    struct Failure: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }

    /// Sends one piece of feedback. Images are downscaled to 2048 px on the
    /// long side and sent as JPEG, so a report with four screenshots stays
    /// small enough for cellular. Main actor because the device strings come
    /// from `UIDevice`; the network wait does not block anything.
    @MainActor
    static func send(message: String, images: [UIImage]) async throws {
        guard message.count <= FeedbackConfig.maximumMessageLength else {
            throw Failure(message: "反馈内容不能超过 \(FeedbackConfig.maximumMessageLength) 字，请精简后重试。")
        }
        let info = Bundle.main.infoDictionary ?? [:]
        let body: [String: Any] = [
            "message": message,
            "appVersion": info["CFBundleShortVersionString"] as? String ?? "",
            "appBuild": info["CFBundleVersion"] as? String ?? "",
            "deviceModel": deviceModel,
            "osVersion": "\(UIDevice.current.systemName) \(UIDevice.current.systemVersion)",
            "locale": Locale.current.identifier,
            "images": images.prefix(FeedbackConfig.maximumImages).compactMap { image -> [String: String]? in
                guard let data = jpegData(image) else { return nil }
                return ["mediaType": "image/jpeg", "data": data.base64EncodedString()]
            },
        ]

        var request = URLRequest(url: FeedbackConfig.endpoint)
        request.httpMethod = "POST"
        request.setValue("Bearer \(FeedbackConfig.appKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let bytes = try JSONSerialization.data(withJSONObject: body)
        request.httpBody = bytes
        request.timeoutInterval = 60
        // The proof signs these exact bytes; none is fine (stored unverified).
        let proof = await AirDeviceProof.headers(
            for: bytes, endpoint: FeedbackConfig.endpoint, appKey: FeedbackConfig.appKey, teamID: FeedbackConfig.teamID
        )
        for (name, value) in proof {
            request.setValue(value, forHTTPHeaderField: name)
        }

        let response: URLResponse
        do {
            let result = try await URLSession.shared.data(for: request)
            response = result.1
        } catch let error as URLError {
            switch error.code {
            case .notConnectedToInternet, .networkConnectionLost:
                throw Failure(message: "网络连接已断开，请检查网络后重试。")
            case .timedOut:
                throw Failure(message: "发送超时，请稍后重试。")
            default:
                throw Failure(message: "暂时无法连接反馈服务，请稍后重试。")
            }
        }
        guard let http = response as? HTTPURLResponse else {
            throw Failure(message: "未收到服务器响应，请稍后重试。")
        }
        if !proof.isEmpty {
            await AirDeviceProof.handle(status: http.value(forHTTPHeaderField: "X-Attest-Status"))
        }
        guard (200 ..< 300).contains(http.statusCode) else {
            switch http.statusCode {
            case 413:
                throw Failure(message: "反馈内容或图片过大，请减少内容后重试。")
            case 429:
                throw Failure(message: "发送过于频繁，请稍后重试。")
            default:
                throw Failure(message: "反馈发送失败（错误码 \(http.statusCode)），请稍后重试。")
            }
        }
    }

    private static func jpegData(_ image: UIImage) -> Data? {
        let longest = max(image.size.width, image.size.height)
        let scale = min(1, 2048 / max(longest, 1))
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let scaled = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
        return scaled.jpegData(compressionQuality: 0.8)
    }

    /// The hardware identifier (`iPhone17,2`), which is what a bug report needs;
    /// `UIDevice.model` only says "iPhone".
    private static var deviceModel: String {
        var system = utsname()
        uname(&system)
        return withUnsafeBytes(of: &system.machine) { buffer in
            String(decoding: buffer.prefix { $0 != 0 }, as: UTF8.self)
        }
    }
}

struct FeedbackView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var message = ""
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var images: [UIImage] = []
    @State private var sending = false
    @State private var loadingImages = false
    @State private var imageLoadTask: Task<Void, Never>?
    @State private var failure: String?
    @State private var sent = false

    private var canSend: Bool {
        !sending && !loadingImages && message.count <= FeedbackConfig.maximumMessageLength && (!message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !images.isEmpty)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("请描述遇到的问题或你希望增加的功能", text: $message, axis: .vertical)
                        .lineLimit(5 ... 12)
                        .disabled(sending)
                } footer: {
                    Text("\(message.count)/\(FeedbackConfig.maximumMessageLength) 字")
                        .foregroundStyle(message.count > FeedbackConfig.maximumMessageLength ? Color.red : Color.secondary)
                }
                Section {
                    if !images.isEmpty {
                        ScrollView(.horizontal) {
                            HStack(spacing: 8) {
                                ForEach(images.indices, id: \.self) { index in
                                    Image(uiImage: images[index])
                                        .resizable()
                                        .scaledToFill()
                                        .frame(width: 72, height: 72)
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                        .overlay(alignment: .topTrailing) {
                                            Button {
                                                images.remove(at: index)
                                                pickerItems = []
                                            } label: {
                                                Image(systemName: "xmark.circle.fill")
                                                    .symbolRenderingMode(.palette)
                                                    .foregroundStyle(.white, .black.opacity(0.6))
                                            }
                                            .disabled(sending || loadingImages)
                                            .accessibilityLabel("移除第 \(index + 1) 张图片")
                                            .padding(4)
                                        }
                                }
                            }
                        }
                        .scrollIndicators(.hidden)
                    }
                    PhotosPicker(
                        selection: $pickerItems,
                        maxSelectionCount: FeedbackConfig.maximumImages - images.count,
                        matching: .images
                    ) {
                        Label("添加截图", systemImage: "photo.on.rectangle")
                    }
                    .disabled(sending || loadingImages || images.count >= FeedbackConfig.maximumImages)
                    if loadingImages {
                        ProgressView("正在载入图片…")
                    }
                } footer: {
                    Text("最多添加 \(FeedbackConfig.maximumImages) 张图片。发送时会附带应用版本与构建号、设备型号、系统版本及语言地区信息，帮助我们排查问题。")
                }
                if let failure {
                    Section {
                        Text(failure).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("意见反馈")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }.disabled(sending)
                }
                ToolbarItem(placement: .confirmationAction) {
                    if sending {
                        ProgressView().accessibilityLabel("正在发送")
                    } else {
                        Button("发送") { send() }.disabled(!canSend)
                    }
                }
            }
            .onChange(of: pickerItems) { _, items in
                guard !items.isEmpty else { return }
                imageLoadTask?.cancel()
                loadingImages = true
                imageLoadTask = Task { await load(items) }
            }
            .alert("感谢反馈", isPresented: $sent) {
                Button("完成") { dismiss() }
            } message: {
                Text("你的反馈已发送。")
            }
            .interactiveDismissDisabled(sending)
            .onDisappear {
                imageLoadTask?.cancel()
                imageLoadTask = nil
                loadingImages = false
                pickerItems = []
            }
        }
        .postHogScreenView("Feedback")
    }

    @MainActor
    private func load(_ items: [PhotosPickerItem]) async {
        defer {
            if !Task.isCancelled {
                loadingImages = false
                pickerItems = []
                imageLoadTask = nil
            }
        }
        for item in items {
            guard !Task.isCancelled, images.count < FeedbackConfig.maximumImages else { break }
            let data = try? await item.loadTransferable(type: Data.self)
            // Loading an iCloud image suspends; recheck cancellation and the limit afterward.
            guard !Task.isCancelled, images.count < FeedbackConfig.maximumImages else { break }
            guard let data, let image = UIImage(data: data) else {
                failure = "部分图片未能载入，请重新选择后重试。"
                continue
            }
            images.append(image)
        }
    }

    private func send() {
        guard canSend else { return }
        sending = true
        failure = nil
        Task {
            do {
                try await FeedbackClient.send(message: message, images: images)
                sent = true
            } catch {
                failure = (error as? FeedbackClient.Failure)?.message ?? "反馈发送失败，请稍后重试。"
            }
            sending = false
        }
    }
}
