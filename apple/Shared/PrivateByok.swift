import Foundation
import ClassingCore
@MainActor extension AppStore {
    var byokOwner: String { signedIn && !account["userId"].string.isEmpty ? account["userId"].string : "local" }
    func byokKeyName(_ id: String) -> String { "byok.key.\(byokOwner).\(id)" }
    func loadPrivateByok() async throws {
        let raw = try await vault.secret("byok.config.\(byokOwner)")
        if raw.isEmpty { byok = ByokConfiguration(ownerId: byokOwner) }
        else { let value = try JSONValue.decode(Data(raw.utf8)); let config = try ByokConfiguration.decode(value); guard config.ownerId == byokOwner else { throw ClassingError.invalid("自备模型配置不属于当前账号。") }; byok = config }
    }
    func savePrivateByok(_ config: ByokConfiguration) async throws {
        try config.validate(); guard config.ownerId == byokOwner else { throw ClassingError.invalid("不能将其他账号的自备模型配置用于当前账号。") }
        try await vault.setSecret(String(decoding: Wire.encoder.encode(config), as: UTF8.self), key: "byok.config.\(byokOwner)"); byok = config
    }
    func byokClient(_ profile: ByokProfile) async throws -> BYOKClient {
        guard byokAllowed else { throw ClassingError.invalid("自备 AI 模型仅向有效的 Classing Pro 账号开放。") }
        guard byok.ownerId == byokOwner else { throw ClassingError.invalid("请先选择当前账号的自备模型配置。") }
        try profile.validate(); guard let url = URL(string: profile.baseUrl) else { throw ClassingError.invalid("供应商地址无效。") }
        let key = try await vault.secret(byokKeyName(profile.id)); guard byokAllowed else { throw ClassingError.invalid("Pro 已到期，请续订后使用自备模型。") }; return try BYOKClient(endpoint: url, model: profile.model, key: key)
    }
    func removeByokProfile(_ id: String) async throws { var config = byok; config.remove(id); try await vault.setSecret("", key: byokKeyName(id)); try await savePrivateByok(config) }
}
