plugins { id("com.android.application") version "8.5.2" }
android {
    namespace = "port.sysui"
    compileSdk = 34
    defaultConfig { applicationId = "port.sysui"; minSdk = 29; targetSdk = 34; versionCode = 1; versionName = "0.1" }
    sourceSets { getByName("main") { manifest.srcFile("AndroidManifest.xml"); java.srcDirs("src"); assets.srcDirs("data", "meta") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    androidResources { noCompress += listOf("dex") }
}
dependencies { compileOnly("de.robv.android.xposed:api:82") }
