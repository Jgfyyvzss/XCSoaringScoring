import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
// Baked-in, app-wide key (tasks:read). Never committed - lives only in local.properties
// on your machine and in CI secrets. See README "API key management" for the tradeoffs.
val ssApiKey: String = localProperties.getProperty("ss.apiKey") ?: ""

// DustDevil.cloud sign-in: the PUBLIC id of the above key, shown on the SoaringScoring
// dashboard once the dev approves our redirect URI - not a secret itself, but there's
// no point baking in a value that doesn't exist yet, so it stays out of git like the
// key above. Empty until then; the sign-in button stays disabled with an explanatory
// message rather than firing a request that can only 400. See DEVELOPMENT.md's
// "DustDevil.cloud sign-in" section.
val ssDustDevilClientKeyId: String = localProperties.getProperty("ss.dustDevilClientKeyId") ?: ""

// DustDevil.cloud sign-in redirect scheme/host - the single source of truth, also used
// below to fill in the manifest's intent-filter data element via manifestPlaceholders.
// Deliberately NOT derived from applicationId (see DEVELOPMENT.md's "DustDevil.cloud
// sign-in" section) - which is exactly why the app's rename to XCSoaringScoring
// (applicationId below) required no change here and no re-registration with the dev.
val oauthRedirectScheme = "xcsoaringscoring"
val oauthRedirectHost = "oauth-callback"

android {
    namespace = "com.soaringscoring.xcsoaringscoring"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.soaringscoring.xcsoaringscoring"
        minSdk = 26
        targetSdk = 34
        // Bump BOTH on every release you publish anywhere (GitHub, F-Droid, etc).
        // versionCode: internal, integer only, Android/F-Droid use it to detect
        //   "is this newer than what's installed" - just +1 every release, never reuse a number.
        // versionName: what humans see. Semantic-ish is fine: MAJOR.MINOR.PATCH -
        //   bump PATCH for a bugfix-only release, MINOR for new features, MAJOR for
        //   a big breaking change. Also feeds the friendly APK filename below.
        versionCode = 1
        versionName = "0.3.0"

        buildConfigField("String", "SS_API_KEY", "\"$ssApiKey\"")
        buildConfigField("String", "SS_DUSTDEVIL_CLIENT_KEY_ID", "\"$ssDustDevilClientKeyId\"")
        buildConfigField("String", "OAUTH_REDIRECT_SCHEME", "\"$oauthRedirectScheme\"")
        buildConfigField("String", "OAUTH_REDIRECT_HOST", "\"$oauthRedirectHost\"")

        manifestPlaceholders["oauthRedirectScheme"] = oauthRedirectScheme
        manifestPlaceholders["oauthRedirectHost"] = oauthRedirectHost
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "XCSoaringScoring-${variant.versionName}-${variant.name}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Document/SAF helper for writing into Android/media/<xcsoar>/Tasks
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Chrome Custom Tabs for DustDevil.cloud sign-in - never a WebView, since pilot
    // credentials must never pass through a view our app code could inspect.
    implementation("androidx.browser:browser:1.8.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
