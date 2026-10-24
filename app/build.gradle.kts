plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
base { archivesName.set("microbes") }
android {
    namespace = "com.retro.microbes"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.retro.microbes"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "4.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
