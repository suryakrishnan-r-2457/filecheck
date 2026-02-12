plugins {
    java
    id("com.google.protobuf") version "0.9.4" apply false
}

allprojects {
    group = "com.dlp.discovery"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
