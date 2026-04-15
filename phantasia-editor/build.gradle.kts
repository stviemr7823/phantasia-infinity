// phantasia-editor/build.gradle.kts

plugins {
    java
}

group = "com.phantasia"
version = "2.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Sister modules (Section 2 — Dependency Graph)
    implementation(project(":phantasia-core"))
    implementation(project(":phantasia-j2d"))

    // UI framework (Section 8.2, 8.9)
    implementation("com.formdev:flatlaf:3.5.4")
    implementation("com.formdev:flatlaf-extras:3.5.4")

    // Layout manager (Section 8.4)
    implementation("com.miglayout:miglayout-swing:11.4.2")
}

sourceSets {
    main {
        resources.srcDirs("src/main/resources")
    }
}