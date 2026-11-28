apply(from = rootProject.file("gradle/life-module.gradle"))

dependencies {
    "api"(project(":life-api"))
    "implementation"(project(":plugins:view2d"))
    "testImplementation"(project(":life-testkit"))
}
