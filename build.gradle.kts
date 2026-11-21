plugins {
    application
}

application {
    mainClass = "com.holub.life.Life"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":life-api"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    // Preserve the original single-file java -jar entry point.
    dependsOn(":life-api:jar")
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { dependency -> if (dependency.isDirectory) dependency else zipTree(dependency) }
    }) {
        exclude("META-INF/MANIFEST.MF")
    }
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}
