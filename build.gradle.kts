import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile

val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    application
    kotlin("jvm") version "1.5.10"
}

group = "me.cupertank"
version = "0.0.1"
application {
    mainClass.set("me.cupertank.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-network:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.2")
}

tasks.withType<KotlinJvmCompile> {
    kotlinOptions {
        jvmTarget = "11"
        languageVersion = "1.4"
        apiVersion = "1.4"
    }
}