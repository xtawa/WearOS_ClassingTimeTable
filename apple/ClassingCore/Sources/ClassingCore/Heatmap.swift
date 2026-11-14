import Foundation
public struct HeatmapLesson: Sendable { public var dayOfWeek: Int, startMinute: Int, endMinute: Int; public init(dayOfWeek: Int, startMinute: Int, endMinute: Int) { self.dayOfWeek = dayOfWeek; self.startMinute = startMinute; self.endMinute = endMinute } }
public struct HeatmapCell: Sendable, Identifiable { public var dayOfWeek: Int, startMinute: Int, endMinute: Int, count: Int; public var id: String { "\(dayOfWeek):\(startMinute):\(endMinute)" } }
public enum HeatmapBuilder {
    /// Same weekday × exact time-slot count contract as shared/ui/heatmap/HeatmapModels.kt.
    public static func cells(_ lessons: [HeatmapLesson]) -> [HeatmapCell] {
        let slots = Set(lessons.map { "\($0.startMinute):\($0.endMinute)" }).sorted { a, b in let x = a.split(separator: ":").compactMap { Int($0) }, y = b.split(separator: ":").compactMap { Int($0) }; return x[0] == y[0] ? x[1] < y[1] : x[0] < y[0] }
        var result: [HeatmapCell] = []
        for day in 1...7 { for slot in slots { let times = slot.split(separator: ":").compactMap { Int($0) }; result.append(HeatmapCell(dayOfWeek: day, startMinute: times[0], endMinute: times[1], count: lessons.filter { $0.dayOfWeek == day && $0.startMinute == times[0] && $0.endMinute == times[1] }.count)) } }
        return result
    }
}
