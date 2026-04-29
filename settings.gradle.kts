rootProject.name = "kotlin-plus-plus"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    ":libs:kpp-core",
    ":libs:kpp-capability",
    ":libs:kpp-analyzer",
    ":libs:kpp-immutable",
    ":libs:kpp-concurrent",
    ":libs:kpp-derive",
    ":libs:kpp-test",
    ":libs:kpp-secret",
    ":libs:kpp-validation",
    ":libs:kpp-derive-ksp",
    ":libs:kpp-gradle-plugin",
    ":samples:payment",
    ":samples:http-server",
    ":samples:derive-ksp-demo",
)
