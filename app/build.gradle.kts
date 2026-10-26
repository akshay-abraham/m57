plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lyra.blocker"
    compileSdk = 35
    buildFeatures { buildConfig = true }
    val sourceRef = providers.environmentVariable("M57_SOURCE_REF").orElse("").get()
    require(sourceRef.isEmpty() || sourceRef.matches(Regex("[a-f0-9]{40}"))) { "M57_SOURCE_REF must be a full commit SHA" }
    defaultConfig {
        applicationId = "com.lyra.blocker"
        minSdk = 34
        targetSdk = 35
        versionCode = 7
        versionName = "0.6.1"
        buildConfigField("String", "SOURCE_REF", "\"$sourceRef\"")
        buildConfigField("String", "SOURCE_URL", "\"https://github.com/akshay-abraham/m57${if (sourceRef.isEmpty()) "" else "/tree/$sourceRef"}\"")
    }
    val releaseStore = providers.environmentVariable("M57_KEYSTORE").orNull
    if (!releaseStore.isNullOrBlank()) signingConfigs.create("distribution") {
        storeFile = file(releaseStore)
        storePassword = providers.environmentVariable("M57_STORE_PASSWORD").get()
        keyAlias = providers.environmentVariable("M57_KEY_ALIAS").get()
        keyPassword = providers.environmentVariable("M57_KEY_PASSWORD").get()
    }
    buildTypes {
        release {
            if (!releaseStore.isNullOrBlank()) signingConfig = signingConfigs.getByName("distribution")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies { testImplementation("junit:junit:4.13.2") }
