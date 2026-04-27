plugins {
    kotlin("jvm")
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":libs:kpp-analyzer"))
    implementation(gradleApi())
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("kpp") {
            id = "dev.kotlinplusplus.kpp"
            implementationClass = "dev.kpp.gradle.KppPlugin"
            displayName = "Kotlin++ Analyzer Plugin"
            description = "Adds the kppCheck task that runs the Kotlin++ regex analyzer over the project's Kotlin sources."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
