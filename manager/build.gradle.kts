plugins {
    application
}

application {
    mainClass.set("com.kaustubh.localservers.manager.ManagerMain")
}

tasks.processResources {
    dependsOn(":paper-plugin:jar")
    from(project(":paper-plugin").tasks.named("jar")) {
        into("bridge")
        rename { "localservers-bridge.jar" }
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}
