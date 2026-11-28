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

    // Exercise the shipped plug-in service providers on the real host.
    testRuntimeOnly(project(":plugins:codec"))
    testRuntimeOnly(project(":plugins:food-aging"))
    testRuntimeOnly(project(":plugins:obstacle"))
    testRuntimeOnly(project(":plugins:toroidal"))
    testRuntimeOnly(project(":plugins:view2d"))
    testRuntimeOnly(project(":plugins:view3d"))
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
