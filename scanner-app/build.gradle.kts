plugins {
    java
    application
    id("com.google.protobuf") version "0.9.4"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // Parsing
    implementation("org.apache.tika:tika-core:2.9.2")
    implementation("org.apache.tika:tika-parsers-standard-package:2.9.2")

    // Detection
    implementation("com.google.re2j:re2j:1.7")

    // Storage & Caching
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("org.lmdbjava:lmdbjava:0.9.0")

    // Platform integration
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Serialization
    implementation("com.google.protobuf:protobuf-java:3.25.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")

    // Crypto
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.slf4j:slf4j-api:2.0.11")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.testcontainers:testcontainers:1.19.5")
}

application {
    mainClass.set("com.dlp.discovery.ScannerApplication")
    applicationDefaultJvmArgs = listOf(
        "-Xmx512m",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=100",
        "-Djava.io.tmpdir=\${java.io.tmpdir}/dlp-scanner"
    )
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.2"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("../proto")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
