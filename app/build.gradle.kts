plugins {
    id("com.android.application")
}

android {
    namespace = "com.currencyrecognition"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.currencyrecognition"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }


    // Allow the large ONNX model file to be packaged without compression
    androidResources {
        noCompress += listOf("onnx")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX
    val cameraXVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    // ONNX Runtime for Android (no TFLite)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")
}
