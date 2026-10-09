import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    id("museumly.android.application")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.appdistribution)
}

android {
    namespace = "com.kg.museumly"

    defaultConfig {
        applicationId = "com.kg.museumly"
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        getByName("debug")
        {
            val sharedKeystore = rootProject.file("debug.keystore")
            if (sharedKeystore.exists()) storeFile = sharedKeystore
        }
    }
    buildTypes {
        release {
            optimization {
                enable = false
            }
        }

        debug {
            firebaseAppDistribution {
                appId = "1:1057344497240:android:95aaa1b8f31ebaaba44c2d"
                groups = "first" // it may be First.
            }
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(project(":domain"))
    // No code here imports :data. Hilt needs it on the classpath to find the bindings.
    implementation(project(":data"))
    implementation(project(":presentation"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.coil)

    // Firebase
    implementation(platform(libs.firebase.bom))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // AdMob
    //implementation(libs.play.services.ads)
}