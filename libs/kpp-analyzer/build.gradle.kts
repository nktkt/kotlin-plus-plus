plugins {
    kotlin("jvm")
    application
}

dependencies {
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("dev.kpp.analyzer.KppCheckKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("kppCheck") {
    group = "verification"
    description = "Runs the kpp-analyzer line-based scanner over the project root."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.kpp.analyzer.KppCheckKt")
    args = listOf(rootProject.projectDir.absolutePath)
}
