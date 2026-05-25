// Build configuration for the TapTap-Pay Kotlin SDK.
//
// Layout:
//   src/main/kotlin/rs/taptap/sdk/   — hand-written ergonomics
//   gen/                              — generated Java protos + Kotlin
//                                       Connect stubs (overwritten by
//                                       the upstream api repo's
//                                       release-sdks workflow)
//
// The `version` here is a placeholder; the release workflow rewrites
// it via sed before publishing.

plugins {
    kotlin("jvm") version "2.0.21"
    `java-library`
    `maven-publish`
}

group = "rs.taptap"
version = "0.0.2"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

// Connect-Kotlin generates Kotlin stubs that consume Java proto
// messages, so both the gen/ tree (Java + Kotlin) and src/main/kotlin
// must be on the main source set.
sourceSets {
    main {
        java.srcDir("gen")
        kotlin.srcDir("gen")
    }
}

val connectKotlinVersion = "0.7.2"
// Generated Java protos pin themselves to the version that produced
// them (4.35.x). RuntimeVersion.validateProtobufGencodeVersion at
// startup hard-fails if the runtime is older, so this stays in
// lockstep with the buf.build/protocolbuffers/java plugin version.
val protobufJavaVersion = "4.35.0"
val coroutinesVersion = "1.9.0"
val protovalidateVersion = "0.7.0"

dependencies {
    api("com.google.protobuf:protobuf-java:$protobufJavaVersion")
    api("com.connectrpc:connect-kotlin:$connectKotlinVersion")
    api("com.connectrpc:connect-kotlin-okhttp:$connectKotlinVersion")
    api("com.connectrpc:connect-kotlin-google-java-ext:$connectKotlinVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    // Generated protos reference build.buf.validate.* types via
    // protovalidate field options; needed on the classpath for
    // compilation even though we don't run validators client-side.
    api("build.buf:protovalidate:$protovalidateVersion")

    // OkHttp transport pulled in by connect-kotlin-okhttp; pin
    // explicitly so consumers don't get an older version via
    // resolution surprises.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "sdk"
            from(components["java"])
            pom {
                name.set("TapTap-Pay Kotlin SDK")
                description.set("Official Kotlin SDK for the TapTap-Pay API")
                url.set("https://github.com/TapTap-Pay/sdk-kotlin")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    url.set("https://github.com/TapTap-Pay/sdk-kotlin")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/TapTap-Pay/sdk-kotlin")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
