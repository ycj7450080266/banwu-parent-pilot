plugins {
    id("com.android.application")
}

android {
    namespace = "cn.banwu.parentpilot"
    compileSdk = 37

    defaultConfig {
        applicationId = "cn.banwu.parentpilot"
        minSdk = 26
        targetSdk = 35
        versionCode = 37
        versionName = "0.5.20"
    }
}

dependencies {
    // Bundled Chinese OCR runs on-device; images are not uploaded to the model API.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
