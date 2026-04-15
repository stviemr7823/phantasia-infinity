plugins {
    id("java")
}

description = "Shared JME infrastructure — no Lemur, no domain logic."

val jmeVersion: String by parent!!.extra

dependencies {
    implementation("org.jmonkeyengine:jme3-core:$jmeVersion")
    implementation(project(":phantasia-core"))
}