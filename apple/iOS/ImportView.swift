import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import ClassingCore
import UIKit
struct ImportView: View {
    @EnvironmentObject var store: AppStore
    @State private var raw = "", draft: ImportDraft?, picker = false, photo: PhotosPickerItem?, camera = false, busy = false, replace = false, confirm = false
    @State private var canvasOrigin = "", canvasToken = "", from = SchoolTime.dateString(Date()), through = SchoolTime.dateString(Date().addingTimeInterval(15_552_000))
    var body: some View {
        Form {
            Section("ICS / JSON / 文本") {
                TextEditor(text: $raw).frame(minHeight: 150).font(.caption.monospaced()).accessibilityIdentifier("import-text")
                Text("文本格式：课程名、星期（1–7）、开始 HH:mm、结束 HH:mm，以制表符分隔。").font(.caption).foregroundStyle(.secondary)
                HStack { Button("选择文件") { picker = true }; Spacer(); Button("解析") { parse() }.disabled(busy) }
            }
            Section("照片识别") {
                PhotosPicker(selection: $photo, matching: .images) { Label("从照片导入", systemImage: "photo") }
                Button { camera = true } label: { Label("拍照导入", systemImage: "camera") }.disabled(!UIImagePickerController.isSourceTypeAvailable(.camera))
                Text(store.byok.enabled ? "照片直接发送到你选择的 BYOK 识图模型，不经 Classing；结果需要确认。" : "识别时会把压缩后的照片发送到 Classing AI，并计入服务端点数。结果需要你确认后才会保存。").font(.caption).foregroundStyle(.secondary)
            }
            Section("Canvas（只读）") {
                TextField("学校 HTTPS 根地址", text: $canvasOrigin).textInputAutocapitalization(.never).autocorrectionDisabled(); SecureField("个人访问令牌（仅本次请求）", text: $canvasToken)
                TextField("开始 YYYY-MM-DD", text: $from); TextField("结束 YYYY-MM-DD", text: $through)
                Button("读取日历事件") { Task { busy = true; defer { busy = false; canvasToken = "" }; do { guard let origin = URL(string: canvasOrigin) else { throw ClassingError.invalid("Canvas 地址无效。") }; draft = try await CanvasClient().load(origin: origin, token: canvasToken, from: from, through: through, zone: store.state.settings.timezone) } catch { store.error = error.localizedDescription } } }.disabled(busy)
            }
            if busy { ProgressView("正在处理…") }
            if let draft = draft {
                Section("导入预览") {
                    Text("\(draft.lessons.count) 门重复课程 · \(draft.exceptions.count) 条日程 · \(draft.exams.count) 场考试")
                    ForEach(draft.lessons) { l in Text("\(l.title) · 周\(l.dayOfWeek) \(SchoolTime.clock(l.startMinute))") }
                    ForEach(Array(draft.exceptions.prefix(30))) { e in Text("\(e.date) \(e.title ?? "临时安排")") }
                    ForEach(draft.warnings, id: \.self) { Text($0).font(.caption).foregroundStyle(.orange) }
                    Toggle("替换现有课表（否则按 ID 合并）", isOn: $replace)
                    Button("确认导入") { confirm = true }.buttonStyle(.borderedProminent)
                }
            }
        }.navigationTitle("导入课表")
        .confirmationDialog(replace ? "替换现有课表？" : "合并导入课表？", isPresented: $confirm, titleVisibility: .visible) { Button("导入") { do { guard let draft = draft else { return }; try store.commit(draft.applying(to: store.state, replace: replace)); self.draft = nil; store.status = "导入完成" } catch { store.error = error.localizedDescription } } } message: { Text("会自动保存导入前的快照。") }
        .fileImporter(isPresented: $picker, allowedContentTypes: [.data]) { result in do { let url = try result.get(), scoped = url.startAccessingSecurityScopedResource(); defer { if scoped { url.stopAccessingSecurityScopedResource() } }; let data = try Data(contentsOf: url); guard data.count <= 10_485_760 else { throw ClassingError.invalid("文件超过 10 MiB。") }; guard let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .utf16) else { throw ClassingError.invalid("文件编码不受支持。") }; raw = text; parse() } catch { store.error = error.localizedDescription } }
        .onChange(of: photo) { _, item in Task { do { guard let data = try await item?.loadTransferable(type: Data.self), let image = UIImage(data: data) else { return }; await recognize(image) } catch { store.error = error.localizedDescription } } }
        .sheet(isPresented: $camera) { CameraCapture { image in camera = false; if let image = image { Task { await recognize(image) } } } }
        .onDisappear { canvasToken = "" }
    }
    func parse() { do { draft = try ScheduleImport.text(raw, zone: store.state.settings.timezone) } catch { store.error = error.localizedDescription } }
    func recognize(_ image: UIImage) async {
        busy = true; defer { busy = false }
        do {
            guard store.byok.enabled || store.signedIn else { throw ClassingError.missingSession }
            let scale = min(1, 1800 / max(image.size.width, image.size.height)), size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
            let normalized = UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
            guard let jpeg = normalized.jpegData(compressionQuality: 0.75), jpeg.count <= 3_145_728 else { throw ClassingError.invalid("照片压缩后仍超过 3 MiB。") }
            if store.byok.enabled {
                let client = try await store.byokClient(store.byok.selected("VISION")); draft = try ScheduleImport.json(try await client.recognize(jpeg).data()); return
            }
            let result = try await store.api.request("POST", "/api/v1/ai/photo-import", body: .object(["clientRequestId": .string(UUID().uuidString), "imageBase64": .string(jpeg.base64EncodedString())])); draft = try ScheduleImport.json(result["timetable"].data())
        } catch { store.error = error.localizedDescription }
    }
}
struct CameraCapture: UIViewControllerRepresentable {
    var completion: (UIImage?) -> Void
    func makeUIViewController(context: Context) -> UIImagePickerController { let c = UIImagePickerController(); c.sourceType = .camera; c.delegate = context.coordinator; return c }
    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(completion) }
    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let completion: (UIImage?) -> Void
        init(_ completion: @escaping (UIImage?) -> Void) { self.completion = completion }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) { completion(info[.originalImage] as? UIImage) }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { completion(nil) }
    }
}
