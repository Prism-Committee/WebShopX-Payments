plugins {
    id("com.github.gmazzo.buildconfig")
}
java.withJavadocJar()
val base: top.mrxiaom.gradle.LibraryHelper by project.extra
dependencies {
    val dependencies: Map<String, Boolean> by project.extra
    for ((dependency, _) in dependencies) {
        compileOnly(dependency)
    }
    val libraries: List<String> by project.extra
    for (lib in libraries) {
        base.library(lib)
    }
    base.collectPluginHolders()
    compileOnly(project(":packets"))
}

buildConfig {
    className("BuildConstants")
    packageName("com.webshopx.payments")

    base.doResolveLibraries()

    buildConfigField("String", "VERSION", "\"${project.version}\"")
    buildConfigField("java.time.Instant", "BUILD_TIME", "java.time.Instant.ofEpochSecond(${System.currentTimeMillis() / 1000L}L)")
    buildConfigField("String[]", "RESOLVED_LIBRARIES", base.join())
}
