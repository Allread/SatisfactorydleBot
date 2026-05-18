plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "fr.maxlego08.satisfactorydle"
version = "1.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass.set("fr.maxlego08.satisfactorydle.Main")
}

repositories {
    mavenCentral()
    maven {
        name = "groupezReleases"
        url = uri("https://repo.groupez.dev/releases")
    }
}

dependencies {
    implementation("net.dv8tion:JDA:5.2.2") {
        exclude(module = "opus-java")
    }
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("fr.maxlego08.sarah:sarah:1.23")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("Satisfactorydle.jar")
    destinationDirectory.set(file("target"))
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
