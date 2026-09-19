plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

application {
    // Manual reproduction target for the concurrency problem -- deliberately NOT a
    // JUnit test, see README ("why isn't the concurrency bug a unit test?").
    mainClass.set("shiftswap.tools.ConcurrencyStressCheckKt")
}

tasks.test {
    useJUnitPlatform()
}
