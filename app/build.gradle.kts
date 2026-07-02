plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.doanmb"
    compileSdk = 36


    defaultConfig {
        applicationId = "com.example.doanmb"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Gom layout theo vai trò vào các thư mục res riêng. Tất cả vẫn dùng chung 1
    // R namespace nên KHÔNG phải sửa R.layout/findViewById; chỉ là tổ chức file.
    //  - res/layout          : màn auth (login/register/...) + layout dùng chung
    //  - res-admin/layout    : màn của admin
    //  - res-driver/layout   : màn của tài xế
    //  - res-customer/layout : màn của khách (home, thuê/mua xe, ví, chat, profile)
    sourceSets {
        getByName("main") {
            res.setSrcDirs(
                listOf(
                    "src/main/res",
                    "src/main/res-admin",
                    "src/main/res-driver",
                    "src/main/res-customer"
                )
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.cloudinary:cloudinary-android:2.5.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    // Liquid Glass: blur thật nội dung phía sau (frosted glass) cho thanh menu + card
    implementation("com.github.Dimezis:BlurView:version-2.0.6")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.19.1")
    implementation("com.google.android.exoplayer:extension-okhttp:2.19.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // Bản đồ (chọn điểm đón/đến để tính quãng đường): OpenStreetMap qua osmdroid —
    // miễn phí, KHÔNG cần API key/billing như Google Maps. Vị trí "của tôi" (chấm xanh)
    // dùng GPS/Network Provider của chính osmdroid nên không cần Play Services Location.
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.preference:preference:1.2.1")
    implementation ("com.google.firebase:firebase-messaging")
    // viewmodel
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
}