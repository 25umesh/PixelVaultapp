plugins {
    alias(libs.plugins.android.application) // Keep this one
    alias(libs.plugins.kotlin.android)
    // id("com.android.application") // This line was correctly commented out or removed
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.pixelvault" // Changed to lowercase
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.pixelvault" // Changed to lowercase
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { // Added this block
        viewBinding = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    // Add this packagingOptions block
    packaging {
        resources {
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE.md" // Often good to exclude this too as it can also conflict
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    // Ensure you have Firebase dependencies you need, for example:
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.android.gms:play-services-location:21.3.0") // Added this line
    // Add other Firebase SDKs you need
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Added CircleImageView dependency
    implementation("de.hdodenhof:circleimageview:3.1.0")
}
