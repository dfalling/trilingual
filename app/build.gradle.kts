plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing comes from env vars (CI decrypts them from release-secrets.enc.yaml)
// or the matching Gradle properties for a local signed build.
fun releaseProp(name: String): String? = System.getenv(name) ?: providers.gradleProperty(name).orNull

android {
    namespace = "com.dfalling.trilingual"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dfalling.trilingual"
        minSdk = 26
        targetSdk = 37
        // CI injects these: versionCode = commit count, versionName = tag or 0.0.<count>.
        versionCode = releaseProp("RELEASE_VERSION_CODE")?.toInt() ?: 1
        versionName = releaseProp("RELEASE_VERSION_NAME") ?: "0.0.1-dev"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = releaseProp("RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = releaseProp("RELEASE_STORE_PASSWORD")
                keyAlias = releaseProp("RELEASE_KEY_ALIAS")
                keyPassword = releaseProp("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Installs beside the release app instead of over it (and its saved key and history).
            applicationIdSuffix = ".debug"
        }
        release {
            // Falls back to the debug key so `assembleRelease` still works locally
            // as a smoke test without the release keystore.
            signingConfig =
                if (releaseProp("RELEASE_STORE_FILE") != null) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/*.version")
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        // New dependency versions are Renovate's job, not lint's.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // aapt2 only resolves the adaptive launcher icon from mipmap-anydpi-v26, even at minSdk 26.
        disable += "ObsoleteSdkInt"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.anthropic.java)

    testImplementation(libs.junit)
    // Android's org.json is a stub in local unit tests; use the real implementation.
    testImplementation(libs.org.json)
}
