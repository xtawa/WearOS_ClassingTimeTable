import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use(::load)
    }
}
val driveOauthClientId = (
    localProps.getProperty("DRIVE_OAUTH_CLIENT_ID")
        ?: (project.findProperty("DRIVE_OAUTH_CLIENT_ID") as String?)
        ?: ""
    ).trim()
val driveOauthRedirectScheme = (
    localProps.getProperty("DRIVE_OAUTH_REDIRECT_SCHEME")
        ?: (project.findProperty("DRIVE_OAUTH_REDIRECT_SCHEME") as String?)
        ?: ""
    ).trim()
val releaseStoreFilePath = (
    localProps.getProperty("RELEASE_STORE_FILE")
        ?: (project.findProperty("RELEASE_STORE_FILE") as String?)
        ?: ""
    ).trim()
val releaseStorePassword = (
    localProps.getProperty("RELEASE_STORE_PASSWORD")
        ?: (project.findProperty("RELEASE_STORE_PASSWORD") as String?)
        ?: ""
    ).trim()
val releaseKeyAlias = (
    localProps.getProperty("RELEASE_KEY_ALIAS")
        ?: (project.findProperty("RELEASE_KEY_ALIAS") as String?)
        ?: ""
    ).trim()
val releaseKeyPassword = (
    localProps.getProperty("RELEASE_KEY_PASSWORD")
        ?: (project.findProperty("RELEASE_KEY_PASSWORD") as String?)
        ?: ""
    ).trim()
val hasReleaseSigning =
    releaseStoreFilePath.isNotBlank() &&
        releaseStorePassword.isNotBlank() &&
        releaseKeyAlias.isNotBlank() &&
        releaseKeyPassword.isNotBlank()

android {
    namespace = "com.xtawa.classingtime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xtawa.classingtime"
        minSdk = 26
        targetSdk = 35
        versionCode = 104
        versionName = "1.0.4"
        buildConfigField("String", "DRIVE_OAUTH_CLIENT_ID", "\"$driveOauthClientId\"")
        buildConfigField("String", "DRIVE_OAUTH_REDIRECT_SCHEME", "\"$driveOauthRedirectScheme\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")

    implementation(project(":shared"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.13")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
