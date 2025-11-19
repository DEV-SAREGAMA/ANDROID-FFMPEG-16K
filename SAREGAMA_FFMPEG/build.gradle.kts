plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.saregama.android.ffmpeg"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 29
        ndkVersion = "29.0.14206865"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a")
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
    kotlinOptions {
        jvmTarget = "11"
    }
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
        }
    }
    publishing {
        singleVariant("release")
    }
}
group = "com.github.DEV-SAREGAMA"
version = "1.1.0"
afterEvaluate {
    println("Publishing to GitHub as user=" +
            ((project.findProperty("gpr.user") as String?)
                ?: System.getenv("GITHUB_USER")
                ?: "NONE")
    )
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.saregama.android"
                artifactId = "ffmpeg-16k"
                version = "1.1.0"

                pom {
                    name.set("Saregama Android FFmpeg 16K")
                    url.set("https://github.com/DEV-SAREGAMA/ANDROID-FFMPEG-16K")
                    scm {
                        url.set("https://github.com/DEV-SAREGAMA/ANDROID-FFMPEG-16K")
                        connection.set("scm:git:git://github.com/DEV-SAREGAMA/ANDROID-FFMPEG-16K.git")
                        developerConnection.set("scm:git:ssh://git@github.com/DEV-SAREGAMA/ANDROID-FFMPEG-16K.git")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/DEV-SAREGAMA/ANDROID-FFMPEG-16K")

                credentials {
                    username = (project.findProperty("gpr.user") as String?)
                        ?: System.getenv("GITHUB_USER")
                    password = (project.findProperty("gpr.key") as String?)
                        ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}


dependencies {
    implementation(kotlin("stdlib"))
    // Coroutines for async FFmpeg execution
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

//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
