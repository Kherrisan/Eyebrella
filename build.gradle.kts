object Vers {
    object Deps {
        const val log4jVersion = "2.13.0"
        const val vertxVersion = "3.8.4"
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.70"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.3.61"
    id("org.jetbrains.dokka") version "0.10.0"
    id("application")
    id("java-library")
    id("maven-publish")
    id("org.springframework.boot") version "2.2.2.RELEASE"
    id("io.spring.dependency-management") version "1.0.8.RELEASE"
    id("org.jetbrains.kotlin.plugin.spring") version "1.3.61"
    id("maven")
}

group = "cn.kherrisan.eyebrella"
version = "1.5.0"

repositories {
    mavenCentral()
}

configurations {
    all {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.4")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.netty:netty-all:4.1.46.Final")
    implementation("org.apache.logging.log4j", "log4j-api", Vers.Deps.log4jVersion)
    implementation("org.apache.logging.log4j", "log4j-core", Vers.Deps.log4jVersion)
    implementation("com.google.code.gson", "gson", "2.8.6")
    implementation("commons-codec", "commons-codec", "1.13")
    implementation("commons-collections", "commons-collections", "3.2.2")
    implementation("org.apache.commons:commons-lang3:3.9")
    implementation("com.benasher44:uuid:0.0.7")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:${Vers.Deps.vertxVersion}")
    implementation("io.vertx:vertx-lang-kotlin:${Vers.Deps.vertxVersion}")
    implementation("io.vertx:vertx-web-client:${Vers.Deps.vertxVersion}")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("com.lmax:disruptor:3.4.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.0")
}

publishing {
    publications {
        create("default", MavenPublication::class) {
            from(components["java"])
        }
    }
    repositories {
        maven {
            credentials {
                username = "kherrisan-mvn"
                password = "zou970514"
            }
            url = uri("http://118.25.74.63:8081/repository/maven-releases/")
        }
    }
}

tasks {
    jar {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        }
    }

    dokka {
        outputFormat = "html"
        outputDirectory = "$buildDir/dokka"
    }

    test {
        testLogging {
            outputs.upToDateWhen { false }
            showStandardStreams = true
            events("failed", "passed", "skipped")
        }
        useJUnitPlatform()
    }
}

allOpen {
    annotation("cn.kherrisan.eyebrella.core.common.Open")
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class).all {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
