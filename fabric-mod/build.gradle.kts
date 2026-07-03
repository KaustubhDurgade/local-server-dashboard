plugins {
    id("fabric-loom") version "1.14.8"
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.11")
    mappings("net.fabricmc:yarn:1.21.11+build.6:v2")
    modImplementation("net.fabricmc:fabric-loader:0.19.3")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.141.4+1.21.11")
}

tasks.processResources {
    dependsOn(":manager:jar")
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
    from(project(":manager").tasks.named("jar")) {
        into("manager")
        rename { "manager.jar" }
    }
}
