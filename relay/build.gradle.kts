plugins {
    application
}

application {
    mainClass.set("com.kaustubh.localservers.relay.RelayMain")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}
