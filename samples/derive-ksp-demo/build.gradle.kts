plugins {
    kotlin("jvm")
    application
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
}

application {
    mainClass.set("dev.kpp.samples.ksp.MainKt")
}

dependencies {
    implementation(project(":libs:kpp-derive"))
    ksp(project(":libs:kpp-derive-ksp"))
    testImplementation(kotlin("test"))
    testImplementation(project(":libs:kpp-derive"))
}

tasks.test {
    useJUnitPlatform()
}
