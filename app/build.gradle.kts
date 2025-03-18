plugins {
    id("buildlogic.java-application-conventions")
}

application {
    // Define the main class for the application.
    mainClass = "com.github.muteebaa.app.StartVoting"
}

tasks.jar {
    manifest.attributes["Main-Class"] = "com.github.muteebaa.app.StartVoting"
    val dependencies = configurations
        .runtimeClasspath
        .get()
        .map(::zipTree)
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// NOTE: Pass --console=plain to prevent Gradle progress bar from interfering with stdout when using the CLI
tasks.named<JavaExec>("run") {
    standardInput = System.`in` // Gradle sends empty stream by default
}

dependencies {
    implementation("com.google.code.gson:gson:2.12.1")
}
