import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// 릴리스 서명 정보는 저장소에 커밋하지 않는 keystore.properties 에서만 읽는다 (UPDATE_GUIDE.md 참고).
val releaseKeyProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.cpaconceptlab.study"
    compileSdk = 35

    defaultConfig {
        // applicationId 는 절대 변경하지 않는다. 변경하면 기존 설치본과 다른 앱이 되어 학습기록이 이어지지 않는다.
        applicationId = "com.cpaconceptlab.study"
        minSdk = 26
        targetSdk = 35
        // 새 APK 를 배포할 때마다 versionCode 를 반드시 증가시킨다.
        versionCode = 3
        versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 저장소에 포함된 공용 디버그 키: 어떤 PC 에서 빌드해도 같은 서명이 되어 디버그 APK 덮어쓰기 설치가 가능하다.
        // 보안용 키가 아니다. 배포용(Release)은 개인 키를 사용한다.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug-shared.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeyProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(releaseKeyProps.getProperty("storeFile"))
                storePassword = releaseKeyProps.getProperty("storePassword")
                keyAlias = releaseKeyProps.getProperty("keyAlias")
                keyPassword = releaseKeyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("contentAssetsDir", file("src/main/assets/content").absolutePath)
            it.systemProperty("schemaDir", file("schemas").absolutePath)
            it.systemProperty("reportDir", layout.buildDirectory.dir("reports/content").get().asFile.absolutePath)
            it.maxHeapSize = "2g"
            // Robolectric 안드로이드 런타임 jar 다운로드 저장소(Maven Central 요청 제한 시 미러 사용)
            it.systemProperty(
                "robolectric.dependency.repo.url",
                (project.findProperty("robolectricRepo") as String?) ?: "https://maven-central.storage-download.googleapis.com/maven2",
            )
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
}
