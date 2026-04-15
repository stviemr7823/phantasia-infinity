plugins {
    id("java")
}

description = "All Lemur GUI states — the only module that may import Lemur."

val jmeVersion: String        by parent!!.extra
val lemurVersion: String      by parent!!.extra
val lemurProtoVersion: String by parent!!.extra

dependencies {
    implementation("org.jmonkeyengine:jme3-core:$jmeVersion")
    implementation("com.simsilica:lemur:$lemurVersion")
    implementation("com.simsilica:lemur-proto:$lemurProtoVersion")

    implementation(project(":phantasia-core"))
    implementation(project(":phantasia-jme:phantasia-jme-core"))
    implementation(project(":phantasia-jme:phantasia-jme-world"))
    implementation(project(":phantasia-jme:phantasia-jme-combat"))
}