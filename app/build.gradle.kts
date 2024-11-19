plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "1.9.10" // You can keep this Kotlin version
}

android {
    namespace = "co.gomarketme.kotlinsampleapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.kotlinSdkSampleApp"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3" // Update Compose compiler to a compatible version
    }

    buildFeatures {
        compose = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.compose.ui:ui:1.5.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.compose.ui:ui-tooling:1.5.2")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.ui:ui-test-manifest:1.5.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.7.1")

    implementation("com.github.GoMarketMe:gomarketme-kotlin:1.0.5")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
