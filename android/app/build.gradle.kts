import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Secrets and per-developer settings come from local.properties or the environment, never from git.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun setting(name: String, default: String = ""): String = localProps.getProperty(name) ?: System.getenv(name) ?: default

val debugApi = setting("LOCANA_API_URL_DEBUG", "http://10.0.2.2:3000/")
val stagingApi = setting("LOCANA_API_URL_STAGING", "https://staging-api.locana.invalid/")
val releaseApi = setting("LOCANA_API_URL_RELEASE", "https://api.locana.invalid/")
// CI passes the version from the git tag and the run number; local builds keep the defaults.
val appVersionName = (findProperty("locanaVersionName") as String?) ?: "0.1.0"
val appVersionCode = (findProperty("locanaVersionCode") as String?)?.toInt() ?: 1
// Explicit opt-in for a release build signed with the debug key (installable for testing, never for Play).
val debugSignedRelease = (findProperty("locanaDebugSignedRelease") as String?) == "true"

listOf("staging" to stagingApi, "release" to releaseApi).forEach { (name, url) ->
    require(url.startsWith("https://")) { "$name API URL must use https: $url" }
}

android {
    namespace = "app.locana"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.locana"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = setting("MAPS_API_KEY")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${setting("GOOGLE_SERVER_CLIENT_ID")}\"")
        buildConfigField("boolean", "HAS_MAPS_KEY", setting("MAPS_API_KEY").isNotBlank().toString())
    }

    signingConfigs {
        // Release key: environment variables (CI secrets) first, then android/keystore.properties.
        val envStore = System.getenv("LOCANA_KEYSTORE_FILE")
        val ks = rootProject.file("keystore.properties")
        if (!envStore.isNullOrBlank()) {
            create("release") {
                storeFile = file(envStore)
                storePassword = System.getenv("LOCANA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LOCANA_KEY_ALIAS")
                keyPassword = System.getenv("LOCANA_KEY_PASSWORD")
            }
        } else if (ks.exists()) {
            val p = Properties().apply { ks.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "API_BASE_URL", "\"$debugApi\"")
            buildConfigField("boolean", "ALLOW_CLEARTEXT", "true")
        }
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            isDebuggable = false
            buildConfigField("String", "API_BASE_URL", "\"$stagingApi\"")
            buildConfigField("boolean", "ALLOW_CLEARTEXT", "false")
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"$releaseApi\"")
            buildConfigField("boolean", "ALLOW_CLEARTEXT", "false")
            signingConfig = if (debugSignedRelease) signingConfigs.getByName("debug") else signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Lets Robolectric's native graphics capture Compose screenshots in the full-app flow test.
        unitTests.all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.work)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play)

    implementation(libs.play.location)
    implementation(libs.play.maps)
    implementation(libs.maps.compose)
    implementation(libs.maps.compose.utils)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
