plugins { kotlin("jvm") }
dependencies {
    api(project(":libs:kpp-core"))
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
