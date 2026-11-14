import SwiftUI
#Preview("iPhone · Today") { NavigationStack { HomeView() }.environmentObject(AppStore(preview: true)) }
#Preview("iPhone · Week") { NavigationStack { TimetableView() }.environmentObject(AppStore(preview: true)) }
#Preview("iPhone · Settings") { NavigationStack { SettingsView() }.environmentObject(AppStore(preview: true)) }
#Preview("iPhone · Heatmap") { NavigationStack { CourseHeatmapView() }.environmentObject(AppStore(preview: true)) }
