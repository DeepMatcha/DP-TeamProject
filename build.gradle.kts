import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.jvm.application.tasks.CreateStartScripts

plugins {
    application
}

application {
    mainClass = "com.holub.life.Life"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
}

// The host classpath stays small. Keep each provider and dependency as its own JAR
// so service descriptors and native resources remain intact.
val pluginProjects = findProject(":plugins")?.subprojects.orEmpty().sortedBy { it.path }
val pluginDistribution = configurations.create("pluginDistribution") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}

dependencies {
    implementation(project(":life-api"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    for (plugin in pluginProjects) {
        add(pluginDistribution.name, project(plugin.path))
        // Exercise regular service discovery in the host integration tests as well.
        testRuntimeOnly(project(plugin.path))
    }
}

val pluginJars = pluginDistribution.incoming.artifactView {
    // The app's parent classloader already owns the shared API.
    componentFilter { id -> id !is ProjectComponentIdentifier || id.projectPath != ":life-api" }
}.files

val standalonePluginDirectory = layout.buildDirectory.dir("libs/plugins")
val stageStandalonePlugins by tasks.registering(Sync::class) {
    from(pluginJars)
    into(standalonePluginDirectory)
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.named<JavaExec>("run") {
    dependsOn(stageStandalonePlugins)
    systemProperty("life.plugins.dir", providers.systemProperty("life.plugins.dir")
        .orElse(standalonePluginDirectory.map { it.asFile.absolutePath }).get())
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    // Preserve the single-file Phase 1 launch, with separately installed optional plug-ins.
    dependsOn(":life-api:jar", stageStandalonePlugins)
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { dependency -> if (dependency.isDirectory) dependency else zipTree(dependency) }
    }) {
        exclude("META-INF/MANIFEST.MF")
    }
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Enable-Native-Access"] = "ALL-UNNAMED"
    }
}

tasks.named<CreateStartScripts>("startScripts") {
    // The application JAR already contains life-api, so the launcher only needs that JAR.
    classpath = files(tasks.jar.flatMap { it.archiveFile })
}

distributions.named("main") {
    contents {
        exclude("lib/life-api.jar")
        from(pluginJars) {
            into("plugins")
            duplicatesStrategy = DuplicatesStrategy.FAIL
        }
    }
}
