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

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.squareup.okhttp3:logging-interceptor:3.8.0")

    // BOM manages all version splits automatically
    implementation(platform("dev.langchain4j:langchain4j-bom:1.12.2"))

    implementation("dev.langchain4j:langchain4j")
    implementation("dev.langchain4j:langchain4j-ollama")
    implementation("dev.langchain4j:langchain4j-qdrant")
    implementation("dev.langchain4j:langchain4j-open-ai")
    implementation("dev.langchain4j:langchain4j-easy-rag")
    implementation("dev.langchain4j:langchain4j-chroma")

    implementation("org.apache.pdfbox:pdfbox:3.0.6")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("com.github.jai-imageio:jai-imageio-jpeg2000:1.4.0")
    implementation("com.github.jai-imageio:jai-imageio-core:1.4.0")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.lucene") {
            useVersion("9.12.1")
            because("Prevent Lucene version mismatch between core and sandbox")
        }
    }
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