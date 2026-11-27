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

    // Isolated-test switch: -PcaretrailTestVariant=true (with -PcaretrailApi=<test
    // backend>) builds a TEST APK that installs side-by-side with the production
    // app: separate applicationId, "CareTrail TEST" launcher label, and no
    // production service keys baked in (Google sign-in section is hidden).
    val testVariant = (project.findProperty("caretrailTestVariant") as String?) == "true"

    defaultConfig {
        applicationId = "com.caretrail.native"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        // Same production backend the web app and Capacitor APK use. A test-variant
        // build must be given its isolated backend explicitly; it never defaults
        // to production.
        val api = (project.findProperty("caretrailApi") as String?)
            ?: if (testVariant) "" else "https://caretrail-adith-k-ps-projects.vercel.app"
        if (testVariant && (api.isEmpty() || api.contains("caretrail-adith-k-ps-projects"))) {
            throw GradleException("Test variant needs -PcaretrailApi=<isolated test backend URL>")
        }
        buildConfigField("String", "API_URL", "\"$api\"")
        // Google sign-in (optional). The anon key is Supabase's public client key,
        // same one the web frontend ships; still injected via gradle property so a
        // build without it simply hides the Google button. The test variant ships
        // no production service keys at all.
        val supabaseUrl = if (testVariant) "" else (project.findProperty("caretrailSupabaseUrl") as String?) ?: "https://oeluqmffpqipqfpdjedi.supabase.co"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        val supabaseAnonKey = if (testVariant) "" else (project.findProperty("caretrailSupabaseAnonKey") as String?) ?: ""
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        val googleServerClientId = if (testVariant) "" else (project.findProperty("caretrailGoogleServerClientId") as String?) ?: "975953077508-bvjgv0ffekdmp0hjb3tein62874m5gtk.apps.googleusercontent.com"
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
        manifestPlaceholders["appLabel"] = if (testVariant) "CareTrail TEST" else "CareTrail"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            if (testVariant) {
                applicationIdSuffix = ".test"
                versionNameSuffix = "-test"
            }
        }
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

    // On-device (emulator) keyboard proof: same harness as the unit UI tests.
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
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
        exclude("com/caretrail/app/CommunityScreenshotTest.class")
        exclude("com/caretrail/app/CommunityScreenshotTest\$*")
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
        exclude("com/caretrail/app/ContentSectionsScreenshotTest.class")
        exclude("com/caretrail/app/ContentSectionsScreenshotTest\$*")
    }
}


// Offline android-all for Robolectric: resolve the preinstrumented SDK jar
// through Gradle (cached and retried by Gradle/setup-gradle) instead of
// Robolectric's runtime Maven fetch, which intermittently fails in CI with
// MavenArtifactFetcher AssertionError caused by IOException (e.g.
// native-build run 45818280563, where every Robolectric test class failed at
// classMethod on the network fetch). Coordinates come from Robolectric
// 4.16.1's DefaultSdkProvider for SDK 34 (the app's targetSdk):
// "14" + "10818077" + "i" + PREINSTRUMENTED_VERSION(7).
// Robolectric picks LocalDependencyResolver when robolectric.offline=true and
// reads the jar flat from robolectric.dependency.dir.
val robolectricOffline = configurations.create("robolectricOffline")

dependencies {
    robolectricOffline("org.robolectric:android-all-instrumented:14-robolectric-10818077-i7")
}

tasks.withType<Test>().configureEach {
    val offlineDir = layout.buildDirectory.dir("robolectric-offline")
    doFirst {
        val jar = robolectricOffline.singleFile
        val dir = offlineDir.get().asFile.apply { mkdirs() }
        jar.copyTo(File(dir, jar.name), overwrite = true)
    }
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", offlineDir.get().asFile.absolutePath)
}
