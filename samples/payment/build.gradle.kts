plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("dev.kpp.samples.payment.MainKt")
}

dependencies {
    implementation(project(":libs:kpp-core"))
    implementation(project(":libs:kpp-capability"))
    testImplementation(kotlin("test"))
}
