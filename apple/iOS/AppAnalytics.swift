import Foundation
import PostHog

/// Anonymous lifecycle and fixed screen names only. Never identify a signed-in account.
@MainActor enum AppAnalytics {
    static func start() {
        // Automated UI runs should not appear as real product usage.
        guard !ProcessInfo.processInfo.arguments.contains("--ui-testing") else { return }
        let config = PostHogConfig(
            projectToken: "ak_production_ZVF6r75cvfvDDFdZeYTz-N9Rf-r5Ofw4Wnu55bi_PGs",
            host: "https://apps.air.build/traffic"
        )
        config.captureApplicationLifecycleEvents = true
        config.captureScreenViews = false
        config.sessionReplay = false
        config.surveys = false
        config.preloadFeatureFlags = false
        config.sendFeatureFlagEvent = false
        config.personProfiles = .never
        config.setDefaultPersonProperties = false
        config.disableGeoIp = true
        config.captureElementInteractions = false
        config.captureSwiftUIElementInteractions = false
        config.captureAutocaptureElementText = false
        config.capturePushNotificationSubscriptions = false
        config.capturePushNotificationOpened = false
        config.errorTrackingConfig.autoCapture = false
        config.errorTrackingConfig.exceptionSteps.enabled = false
        config.rageClickConfig.enabled = false
        PostHogSDK.shared.setup(config)
    }
}
