import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// app/google-services.json 파일이 존재하면 자동으로 Google Services 플러그인 활성화
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// 출시 서명 정보 (없으면 null)
val releaseKeystoreProps: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { stream -> load(stream) } } }

android {
    namespace = "com.budget.householdbook"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.budget.householdbook"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 출시 서명 키 정보는 저장소에 올리지 않는 keystore.properties(.gitignore)에서 읽음
        // storeFile=..., storePassword=..., keyAlias=..., keyPassword=...
        if (releaseKeystoreProps != null) {
            create("release") {
                storeFile = rootProject.file(releaseKeystoreProps.getProperty("storeFile"))
                storePassword = releaseKeystoreProps.getProperty("storePassword")
                keyAlias = releaseKeystoreProps.getProperty("keyAlias")
                keyPassword = releaseKeystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 코드 축소·난독화 (프로젝트 규칙: src/main/keepRules/*.keep)
            optimization {
                enable = true
            }
            signingConfig = if (releaseKeystoreProps != null) {
                signingConfigs.getByName("release")
            } else {
                // 서명 키가 없으면 로컬 확인용으로 디버그 키 서명 (Play 업로드 불가)
                logger.warn("keystore.properties가 없어 release 빌드를 디버그 키로 서명합니다. (스토어 업로드용 아님)")
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // 마이그레이션 테스트(MigrationTestHelper)가 버전별 스키마 JSON을 읽을 수 있도록 androidTest 에셋에 포함
    sourceSets {
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }
}

// Room 스키마 JSON을 버전별로 저장소에 남겨 마이그레이션 검증에 사용
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// 새 스키마 JSON이 생성된 뒤에 androidTest 에셋을 묶도록 순서 보장 (마이그레이션 테스트가 최신 스키마를 읽게)
tasks.matching { it.name == "mergeDebugAndroidTestAssets" }.configureEach {
    dependsOn("kspDebugKotlin")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation & Serialization
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Room Database (Offline-First SSOT)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Dependency Injection (Koin)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Coroutines & WorkManager
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.work.runtime.ktx)

    // Firebase (Auth + Cloud Firestore 실시간 공유 장부 연동)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Network & Scraping (KB 빠른조회)
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}