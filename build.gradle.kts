plugins {
    java
}

group = "com.minecraftraid"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Resolves to the latest Paper API for Minecraft 26.1.x (see https://docs.papermc.io/paper/dev/project-setup/)
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
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
