apply(from = rootProject.file("gradle/life-module.gradle"))

dependencies {
    "api"(project(":life-api"))
    "testImplementation"(project(":life-testkit"))
}

tasks.named<Test>("test") {
    // Tests check drawing and wiring without opening a window.
    systemProperty("java.awt.headless", "true")
}
