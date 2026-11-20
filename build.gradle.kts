plugins {
    application
}

application {
    mainClass = "com.holub.life.Life"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    implementation(project(":life-api"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}
