plugins {
    java
}

group = "com.minecraftraid"
version = "1.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    // Resolves to the latest Paper API for Minecraft 26.1.x (see https://docs.papermc.io/paper/dev/project-setup/)
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldguard:worldguard-core:7.0.13") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.9") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.9") {
        isTransitive = false
    }
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("MinecraftRaid")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}
