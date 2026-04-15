// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // JME artifacts not on Maven Central use jitpack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "phantasia"

gradle.allprojects {
    extra["jmeVersion"]        = "3.6.1-stable"
    extra["lemurVersion"]      = "1.16.0"
    extra["lemurProtoVersion"] = "1.13.0"
}

// ── Core game logic (engine-agnostic) ────────────────────────────────────────
include("phantasia-core")

// ── JME frontend ─────────────────────────────────────────────────────────────
include("phantasia-jme")

// ── JME sub-modules ───────────────────────────────────────────────────────────
include("phantasia-jme:phantasia-jme-core")
include("phantasia-jme:phantasia-jme-world")
include("phantasia-jme:phantasia-jme-combat")
include("phantasia-jme:phantasia-jme-ui")
include("phantasia-jme:phantasia-jme-app")
include("phantasia-j2d")
include("phantasia-libgdx")
include("phantasia-editor")