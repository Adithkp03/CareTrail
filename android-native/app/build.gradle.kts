plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi") version "1.41.0"
}

android {
    namespace = "com.caretrail.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.caretrail.native"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        // Same production backend the web app and Capacitor APK use.
        val api = (project.findProperty("caretrailApi") as String?) ?: "https://caretrail-adith-k-ps-projects.vercel.app"
        buildConfigField("String", "API_URL", "\"$api\"")
        // Google sign-in (optional). The anon key is Supabase's public client key,
        // same one the web frontend ships; still injected via gradle property so a
        // build without it simply hides the Google button.
        val supabaseUrl = (project.findProperty("caretrailSupabaseUrl") as String?) ?: "https://oeluqmffpqipqfpdjedi.supabase.co"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        val supabaseAnonKey = (project.findProperty("caretrailSupabaseAnonKey") as String?) ?: ""
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        val googleServerClientId = (project.findProperty("caretrailGoogleServerClientId") as String?) ?: "975953077508-bvjgv0ffekdmp0hjb3tein62874m5gtk.apps.googleusercontent.com"
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.41.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.41.0")
}

// Screenshot harness isolation: Roborazzi UI tests run only via the
// recordRoborazziDebug task (screenshot CI job). Core unit-test/APK jobs pass
// -Pcaretrail.skipScreenshotTest so a UI-harness failure cannot block them.
tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest" && providers.gradleProperty("caretrail.skipScreenshotTest").isPresent) {
        exclude("com/caretrail/app/VaccinationGuidanceScreenshotTest.class")
        exclude("com/caretrail/app/VaccinationGuidanceScreenshotTest\$*")
        exclude("com/caretrail/app/OfficialSupportScreenshotTest.class")
        exclude("com/caretrail/app/OfficialSupportScreenshotTest\$*")
        exclude("com/caretrail/app/BackupScreenshotTest.class")
        exclude("com/caretrail/app/BackupScreenshotTest\$*")
        exclude("com/caretrail/app/RecordSafetyScreenshotTest.class")
        exclude("com/caretrail/app/RecordSafetyScreenshotTest\$*")
        exclude("com/caretrail/app/LoginScreenshotTest.class")
        exclude("com/caretrail/app/LoginScreenshotTest\$*")
        exclude("com/caretrail/app/HomeLoadingScreenshotTest.class")
        exclude("com/caretrail/app/HomeLoadingScreenshotTest\$*")
        exclude("com/caretrail/app/AudioScreenScreenshotTest.class")
        exclude("com/caretrail/app/AudioScreenScreenshotTest\$*")
        exclude("com/caretrail/app/DoseCardScreenshotTest.class")
        exclude("com/caretrail/app/DoseCardScreenshotTest\$*")
        exclude("com/caretrail/app/StartupStaleScreenshotTest.class")
        exclude("com/caretrail/app/StartupStaleScreenshotTest\$*")
    }
}
