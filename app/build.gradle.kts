plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "bin.mt2.plus"
    compileSdk = 35

    defaultConfig {
        applicationId = "bin.mt2.plus"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 配置签名信息
    signingConfigs {
        create("release") {
            storeFile = file("../key/AutFeng.jks")
            storePassword = "123456789"  // 替换为实际的密码
            keyAlias = "AutFeng"  // 替换为实际的别名
            keyPassword = "123456789"  // 替换为实际的密码
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false

            // 调试模式下也启用R8（可调整优化级别）
            isMinifyEnabled = true  // 启用代码压缩
            isShrinkResources = true  // 启用资源压缩
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        release {
            // 启用R8优化
            isMinifyEnabled = true  // 必须为true才能启用R8
            isShrinkResources = true  // 压缩资源文件
            isCrunchPngs = true  // 压缩PNG图片（默认true）

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildTypes.all {
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )

        // 签名配置
        signingConfig = signingConfigs.getByName("release")
    }

    // 配置R8优化模式
    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=kotlin.Experimental"
        )
    }
}

dependencies {
    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation(libs.io.coil.kt.coil)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}