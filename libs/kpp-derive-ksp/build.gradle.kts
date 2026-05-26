plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.9")
    implementation(project(":libs:kpp-derive"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
