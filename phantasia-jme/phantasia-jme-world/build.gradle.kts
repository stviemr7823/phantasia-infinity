plugins {
    id("java")
}

description = "World exploration — terrain, camera, navigation. No Lemur."

val jmeVersion: String by parent!!.extra

dependencies {
    implementation("org.jmonkeyengine:jme3-core:$jmeVersion")
    implementation(project(":phantasia-core"))
    implementation(project(":phantasia-jme:phantasia-jme-core"))
    implementation("org.jmonkeyengine:jme3-effects:3.6.1-stable")
}