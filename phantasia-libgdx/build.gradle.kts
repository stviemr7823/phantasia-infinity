plugins {
    id("java-library")
    application
}

application {
    mainClass.set("com.phantasia.libgdx.Main")
}

val gdxVersion = "1.12.1"

dependencies {
    api(project(":phantasia-core"))
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    api("com.kotcrab.vis:vis-ui:1.5.0")

    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

repositories {
    mavenCentral()
    maven { url = uri("https://libgdx.badlogicgames.com/releases") }
}

tasks.test {
    useJUnitPlatform()
}