plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.linktotv"
    compileSdk = 34
    defaultConfig { applicationId = "dev.linktotv"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("org.nanohttpd:nanohttpd:2.3.1"); implementation("com.google.zxing:core:3.5.3") }
