apply(from = rootProject.file("gradle/life-module.gradle"))

dependencies {
    "api"(project(":life-api"))
    "testImplementation"(project(":life-testkit"))
}
