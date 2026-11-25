apply(from = rootProject.file("gradle/life-module.gradle"))

val jme = "3.9.0-stable"

dependencies {
    "api"(project(":life-api"))
    // Optional at run time: the view adapts to whichever of these plugins are installed.
    "compileOnly"(project(":plugins:food-aging"))
    "compileOnly"(project(":plugins:obstacle"))
    "compileOnly"(project(":plugins:toroidal"))
    "implementation"("org.jmonkeyengine:jme3-core:$jme")
    "implementation"("org.jmonkeyengine:jme3-effects:$jme")
    "implementation"("org.jmonkeyengine:jme3-desktop:$jme")
    "runtimeOnly"("org.jmonkeyengine:jme3-lwjgl3:$jme")

    "testImplementation"(project(":life-testkit"))
    "testImplementation"(project(":plugins:view2d"))
    "testImplementation"(project(":plugins:food-aging"))
    "testImplementation"(project(":plugins:obstacle"))
    "testImplementation"(project(":plugins:toroidal"))
}

// Opens the 2D and 3D views on a fake host for a manual look; not part of the test run.
tasks.register<JavaExec>("demo") {
    classpath = project.the<SourceSetContainer>()["test"].runtimeClasspath
    mainClass.set("com.holub.life.plugin.view3d.Demo3d")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperties(System.getProperties().filterKeys { it.toString().startsWith("demo.") }.mapKeys { it.key.toString() })
}
