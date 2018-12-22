import com.google.protobuf.gradle.ProtobufPlugin
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import org.gradle.api.internal.HasConvention
import org.gradle.kotlin.dsl.provider.gradleKotlinDslOf
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

// TODO using gradle 4.10.2 since gradle 5.0 doesn't work with the prtobuf configuration dsl

val grpcVersion by extra { "1.16.1" }

plugins {
    `java-library`
    id("nebula.kotlin").version("1.3.11")
    id("com.google.protobuf").version("0.8.7")
}

repositories {
    jcenter()
}

dependencies {
    // Use the Kotlin JDK 8 standard library
    implementation("io.ratpack:ratpack-guice:1.6.0")
    implementation("io.grpc:grpc-netty:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.netty:netty-transport-native-epoll:4.1.32.Final")
    implementation("io.netty:netty-transport-native-kqueue:4.1.32.Final")
    // need this for JsonFormat
    implementation("com.google.protobuf:protobuf-java-util:3.6.1")

    testImplementation("io.grpc:grpc-testing:$grpcVersion")
    testImplementation("io.ratpack:ratpack-test:1.6.0")

    // TODO testing example in kotlin: https://github.com/junit-team/junit5-samples/blob/r5.3.2/junit5-jupiter-starter-gradle-kotlin/src/test/kotlin/com/example/project/CalculatorTests.kt

    // Use the Kotlin test library
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    testImplementation("ch.qos.logback:logback-classic:1.2.3")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.6.1"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.16.1"
        }
    }
    generateProtoTasks {
        ofSourceSet("test").forEach {
            it.plugins {
                id("grpc") {
                    outputSubDir = "java"
                }
            }
        }
    }
    // TODO hack so that tests can find generated class files
    generatedFilesBaseDir = "src"
}
