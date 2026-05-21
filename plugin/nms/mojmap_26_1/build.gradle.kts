dependencies {
    compileOnly("org.spigotmc:spigot-api:26.1-R0.1-SNAPSHOT")
    compileOnly("org.spigotmc:spigot:26.1")
    compileOnly("com.mojang:datafixerupper:9.0.19")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
