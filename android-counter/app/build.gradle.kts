plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val supabaseUrl = providers.gradleProperty("SUPABASE_URL")
    .orElse(providers.environmentVariable("SUPABASE_URL"))
    .orElse("https://bbnvqktefmdpgzlqlzml.supabase.co")
val supabaseKey = providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY")
    .orElse(providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY"))
    .orElse("")

// These values are supplied only by the release workflow when GitHub secrets exist.
val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val keystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val keyAliasValue = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val releaseSigningReady = !keystorePath.isNullOrBlank() &&
    !keystorePassword.isNullOrBlank() &&
    !keyAliasValue.isNullOrBlank() &&
    !keyPassword.isNullOrBlank()

android {
    namespace = "com.dodla.honestmilk.counter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dodla.honestmilk.counter"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.1"
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.get()}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${supabaseKey.get()}\"")
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword!!
                keyAlias = keyAliasValue!!
                this.keyPassword = keyPassword!!
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
