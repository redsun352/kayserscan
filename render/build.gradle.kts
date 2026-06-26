plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kayser.areascan.render"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation("androidx.core:core-ktx:1.13.1")

    // ARCore — cihaz desteklemiyorsa uygulama bu özelliği gizleyip OpenGL-only modda çalışmalı.
    // build.gradle'da "required" olarak işaretlenmedi; runtime'da ArCoreAvailability kontrolü yapılır.
    implementation("com.google.ar:core:1.43.0")
}
