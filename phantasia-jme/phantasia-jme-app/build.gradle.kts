plugins {
    id("java")
    id("application")
}

description = "Application entry point — depends on all sub-modules."

val jmeVersion: String by parent!!.extra

// In phantasia-jme/phantasia-jme-app/build.gradle.kts

application {
    mainClass.set("com.phantasia.jme.Main")  // existing — the real game
}

// Additional run task for the graphics tour
tasks.register<JavaExec>("runTour") {
    group = "application"
    description = "Launch the world graphics tour (no encounters)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.phantasia.jme.TourMain")
    // Inherit the same JVM args as the main run task if you have any
    jvmArgs = (tasks.named<JavaExec>("run").get().jvmArgs ?: emptyList())
}

dependencies {
    implementation("org.jmonkeyengine:jme3-core:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-desktop:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-lwjgl3:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-testdata:$jmeVersion")  // Jaime model + terrain textures

    implementation(project(":phantasia-core"))
    implementation(project(":phantasia-jme:phantasia-jme-core"))
    implementation(project(":phantasia-jme:phantasia-jme-world"))
    implementation(project(":phantasia-jme:phantasia-jme-combat"))
    implementation(project(":phantasia-jme:phantasia-jme-ui"))
}