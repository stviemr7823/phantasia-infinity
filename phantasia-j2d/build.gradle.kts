plugins {
    java
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("com.phantasia.j2d.tour.TourMain")
}

dependencies {
    implementation(project(":phantasia-core"))
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
// Isolated, windowed authoring diagnostic; normal launch/save behavior is unchanged.
tasks.register<JavaExec>("runDiagnostic") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.phantasia.j2d.diagnostic.DiagnosticMain")
    workingDir = rootProject.projectDir
}
