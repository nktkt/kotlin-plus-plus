plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("dev.kpp.samples.http.MainKt")
}

dependencies {
    implementation(project(":libs:kpp-core"))
    implementation(project(":libs:kpp-capability"))
    implementation(project(":libs:kpp-immutable"))
    implementation(project(":libs:kpp-concurrent"))
    implementation(project(":libs:kpp-derive"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
