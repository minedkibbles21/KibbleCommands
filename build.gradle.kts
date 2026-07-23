plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.minedkibbles21"
version = "1.1.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveClassifier.set("")
        // Minimizes jar size by excluding unnecessary files
        exclude("META-INF/maven/**")
        exclude("META-INF/dependency-reduced-pom.xml")
    }

    processResources {
        inputs.property("version", project.version)
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
