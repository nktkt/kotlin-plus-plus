plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.2.20-2.0.4")
    implementation(project(":libs:kpp-derive"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
