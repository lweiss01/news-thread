import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
}

val pagesBaseUrl = "https://lweiss01.github.io/news-thread"
val legalUrl = "$pagesBaseUrl/"
val privacyPolicyUrl = "$pagesBaseUrl/privacy/"
val termsUrl = "$pagesBaseUrl/terms/"

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val releaseTasksRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("release", ignoreCase = true)
}
val hasKeystoreProperties = keystorePropertiesFile.exists().also { exists ->
    if (exists) {
        FileInputStream(keystorePropertiesFile).use(keystoreProperties::load)
    }
}

data class ReleaseKeystoreConfig(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
)

fun loadReleaseKeystoreConfig(): ReleaseKeystoreConfig? {
    if (!hasKeystoreProperties) return null

    val requiredKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    val missingKeys = requiredKeys.filter { key ->
        keystoreProperties.getProperty(key)?.trim().isNullOrEmpty()
    }

    if (missingKeys.isNotEmpty()) {
        throw GradleException(
            "Release signing is misconfigured. Missing ${missingKeys.joinToString()} in ${keystorePropertiesFile.name}. " +
                "See keystore.properties.example."
        )
    }

    val storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
    if (!storeFile.exists()) {
        throw GradleException(
            "Release signing is misconfigured. Keystore file not found at ${storeFile.path}. " +
                "Update ${keystorePropertiesFile.name} or regenerate the local keystore."
        )
    }

    return ReleaseKeystoreConfig(
        storeFile = storeFile,
        storePassword = keystoreProperties.getProperty("storePassword"),
        keyAlias = keystoreProperties.getProperty("keyAlias"),
        keyPassword = keystoreProperties.getProperty("keyPassword")
    )
}

val releaseKeystoreConfig = loadReleaseKeystoreConfig()

if (releaseTasksRequested && releaseKeystoreConfig == null) {
    throw GradleException(
        "Release signing requires ${keystorePropertiesFile.name}. " +
            "Copy keystore.properties.example, point storeFile at a local .jks, and rerun bundleRelease."
    )
}

android {
    namespace = "com.newsthread.app"
    compileSdk = 34

    defaultConfig {
        // Read WORKER_URL from local.properties (gitignored, keeps secrets out of source)
        val workerUrl: String = providers.gradleProperty("WORKER_URL").getOrElse(
            com.android.build.gradle.internal.cxx.configure.gradleLocalProperties(rootDir, providers).getProperty("WORKER_URL", "")
        )
        val workerApiKey: String = providers.gradleProperty("WORKER_API_KEY").getOrElse(
            com.android.build.gradle.internal.cxx.configure.gradleLocalProperties(rootDir, providers).getProperty("WORKER_API_KEY", "dev_key_fallback")
        )
        buildConfigField("String", "WORKER_URL", "\"$workerUrl\"")
        buildConfigField("String", "WORKER_API_KEY", "\"$workerApiKey\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
        buildConfigField("String", "TERMS_URL", "\"$termsUrl\"")
        buildConfigField("String", "LEGAL_URL", "\"$legalUrl\"")

        applicationId = "com.newsthread.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Room schema export for migrations
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        // WORKER_URL and WORKER_API_KEY are set from local.properties above.
        // Do NOT hardcode them here — the key would be committed to source control.
    }

    signingConfigs {
        releaseKeystoreConfig?.let { config ->
            create("release") {
                storeFile = config.storeFile
                storePassword = config.storePassword
                keyAlias = config.keyAlias
                keyPassword = config.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            releaseKeystoreConfig?.let {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols.add("**/libtensorflowlite_jni.so")
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }

    androidResources {
        noCompress += "tflite"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "newsthread-${variant.versionName}-${variant.baseName}.apk"
        }
    }

}

dependencies {
    // Kotlin
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.9.22"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    // OkHttp (Networking — RSS feeds + article HTML fetching)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Hilt (Dependency Injection)
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Coil (Image Loading)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Accompanist (Phase 9: SwipeRefresh)
    implementation("com.google.accompanist:accompanist-swiperefresh:0.32.0")

    // Future enhancement note:
    // Optional account-based backup/sign-in dependencies are intentionally excluded from the
    // shipped release surface for v1.2 Play submission. Reintroduce them only when the feature
    // is actively implemented and the manifest/legal surface is updated to match.

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // DataStore (for preferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Article text extraction
    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("org.jsoup:jsoup:1.22.1")

    // TensorFlow Lite (Phase 3: Embedding Engine)
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4") {
        exclude(group = "org.tensorflow", module = "tensorflow-lite")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    // kxml2 provides XmlPullParser implementation for JVM unit tests
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
