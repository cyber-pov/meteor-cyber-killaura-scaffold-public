plugins {
    id("fabric-loom") version "1.14-SNAPSHOT"
    id("maven-publish")
}

group = property("group") as String
version = property("version") as String

base {
    archivesName = property("archives_base_name") as String
}

repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        name = "Terraformers"
        url = uri("https://maven.terraformersmc.com")
    }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modCompileOnly("meteordevelopment:meteor-client:${property("meteor_version")}")
    modCompileOnly("meteordevelopment:baritone:${property("baritone_version")}-SNAPSHOT")
    compileOnly("meteordevelopment:orbit:${property("orbit_version")}")
    runtimeOnly("meteordevelopment:orbit:${property("orbit_version")}")
    runtimeOnly("org.meteordev:starscript:${property("starscript_version")}")
    runtimeOnly("meteordevelopment:discord-ipc:${property("discordipc_version")}")
    runtimeOnly("org.reflections:reflections:${property("reflections_version")}")
    runtimeOnly("io.netty:netty-handler-proxy:${property("netty_version")}") { isTransitive = false }
    runtimeOnly("io.netty:netty-codec-socks:${property("netty_version")}") { isTransitive = false }
    runtimeOnly("de.florianmichael:WaybackAuthLib:${property("waybackauthlib_version")}")
    modLocalRuntime(fabricApi.module("fabric-api-base", property("fabric_api_version") as String))
    modLocalRuntime(fabricApi.module("fabric-resource-loader-v1", property("fabric_api_version") as String))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

loom {
    runConfigs.named("client") {
        programArgs.addAll(listOf("--username", "Dev"))
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to project.property("minecraft_version"),
        "loader_version" to project.property("loader_version")
    )
    inputs.properties(props)

    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}
