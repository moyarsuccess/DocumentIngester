plugins {
    id("java")
    kotlin("jvm")
}

group = "ca.flutra"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // BOM manages all version splits automatically
    implementation(platform("dev.langchain4j:langchain4j-bom:1.12.2"))

    implementation("dev.langchain4j:langchain4j")
    implementation("dev.langchain4j:langchain4j-ollama")
    implementation("dev.langchain4j:langchain4j-qdrant")

    implementation("org.apache.pdfbox:pdfbox:3.0.6")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    implementation("com.google.cloud:google-cloud-vision:3.30.0")

    // Tesseract OCR (local, no cloud, no LLM)
    implementation("net.sourceforge.tess4j:tess4j:5.13.0")

    // JSON serialization — used by the eval suite for dataset save/load
    implementation("com.google.code.gson:gson:2.13.2")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
tasks.withType<JavaExec> {
    jvmArgs("-Dorg.apache.lucene.store.MMapDirectory.enableMemorySegments=false")
}

// Force all grpc modules to the same version
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.grpc") {
            useVersion("1.77.0")
            because("Force consistent gRPC version across all dependencies")
        }
    }
}