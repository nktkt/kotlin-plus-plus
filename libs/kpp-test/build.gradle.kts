plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":libs:kpp-core"))
    api(project(":libs:kpp-capability"))
    api(kotlin("test"))
    testImplementation(kotlin("test"))
}
