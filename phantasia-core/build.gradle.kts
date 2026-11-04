plugins {
    `java-library`
}

group = "com.phantasia"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.register<JavaExec>("bakeMap") {
    group       = "phantasia"
    description = "Bakes PendragonWorldMap → data/world.dat"

    classpath   = sourceSets["main"].runtimeClasspath
    mainClass   = "com.phantasia.core.world.PendragonWorldMap"
    workingDir  = projectDir

    doFirst {
        mkdir("$projectDir/data")
    }
}

tasks.register<Copy>("copyMapToJme") {
    group       = "phantasia"
    description = "Copies baked world.dat → phantasia-jme/data/"
    dependsOn("bakeMap")

    from("$projectDir/data/world.dat")
    into("$rootDir/phantasia-jme/data")

    doFirst {
        mkdir("$rootDir/phantasia-jme/data")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Zero engine dependencies here to maintain modularity
}

tasks.test {
    useJUnitPlatform()
}





dependencies {
    // Zero engine dependencies here to maintain modularity
}
// Increment 02: v5 binary-layout regression tests (separate from legacy v4 runtime).
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
