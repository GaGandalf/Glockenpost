plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Adresse der wöchentlich geprüften Liste. Der GitHub-Build setzt sie automatisch
// auf data/news.json im eigenen Repository; in der App lässt sie sich unter
// Einstellungen ändern.
val curatedUrl: String = (project.findProperty("curatedUrl") as String?) ?: ""
val buildNumber: Int = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "ch.glockenpost"
    compileSdk = 34

    defaultConfig {
        applicationId = "ch.glockenpost.app"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField("String", "CURATED_URL", "\"$curatedUrl\"")
    }

    // Fester Schlüssel im Projekt, damit sich neue Versionen über alte
    // installieren lassen. Für eine private App ausreichend.
    signingConfigs {
        create("shared") {
            storeFile = file("glockenpost.keystore")
            storePassword = "glockenpost"
            keyAlias = "glockenpost"
            keyPassword = "glockenpost"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
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
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
}
