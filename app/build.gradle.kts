import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.happyc0der.forgelog"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.happyc0der.forgelog"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * A signing config only if `keystore.properties` exists.
     *
     * The file is gitignored along with the keystores themselves, so a checkout without it still
     * builds -- the release variant just comes out unsigned, which is the right failure for anyone
     * who is not the person publishing.
     */
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 on. kotlinx-serialization finds serializers reflectively and so looks like dead
            // code to the shrinker; proguard-rules.pro keeps them, and a release build must be
            // exercised by hand -- export, import, restart -- before it is published.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        /*
         * The debug build under another app id, so it installs beside the real app with its own
         * database: for testing on the phone the app is actually used on without test workouts
         * landing in real history. Named "ForgeLog QA", with an amber icon, so the two cannot be
         * mistaken for each other.
         */
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            matchingFallbacks += listOf("debug")
        }
        /*
         * The release build, shrunk and obfuscated exactly as the published one, but signed with the
         * debug key so it can actually be installed and used.
         *
         * R8 is the one part of the build that cannot be checked by the tests: they run against
         * unshrunk classes, so a missing keep rule shows up only when a release build is run --
         * typically as a backup that will not parse, or a screen that crashes on open. Without this
         * there was no way to exercise it before publishing, since an unsigned APK will not install
         * and the real keystore belongs to whoever publishes.
         *
         * Never publish this variant: it carries the debug signature.
         */
        create("releaseCheck") {
            initWith(getByName("release"))
            /*
             * The QA app's id, not one of its own.
             *
             * MIUI refuses to install a package it has not seen before over USB, so a fresh id
             * cannot be put on the phone at all; an update to one already installed is allowed.
             * Sharing the QA id -- and its debug signature -- is what makes this build reachable on
             * the device it needs to be checked on. `installQa` puts the ordinary QA build back, and
             * the two never coexist, which is the point: whichever is installed says so on its icon.
             */
            applicationIdSuffix = ".qa"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    // Migration tests read the committed schema JSONs to build an older database and let Room
    // perform the real upgrade, so the schema directory is mounted as a test asset source. (The JVM
    // tests use LegacySchemaBuilder rather than MigrationTestHelper, which cannot read assets under
    // Robolectric; androidTest keeps the mount for when instrumented migration tests are added.)
    sourceSets {
        // Compose UI tests and their fixtures live in one place and run twice: on the JVM under
        // Robolectric with `testDebugUnitTest`, and on a real device with
        // `connectedDebugAndroidTest`. They use AndroidJUnit4, which resolves to whichever runner
        // is present, so nothing in them is specific to either.
        getByName("test") {
            assets.directories.add("$projectDir/schemas")
            kotlin.directories.add("src/sharedTest/java")
        }
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
            kotlin.directories.add("src/sharedTest/java")
        }
        // releaseCheck is the release build in everything but its signature, so it takes the
        // release source set -- including the no-op DebugTools, which is what keeps the sample-data
        // seeder out of anything that could be published.
        getByName("releaseCheck") {
            kotlin.directories.add("src/release/java")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
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
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.core.ktx)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
