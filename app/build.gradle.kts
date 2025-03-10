plugins {
    id("buildlogic.java-application-conventions")
}

application {
    // Define the main class for the application.
    mainClass = "com.github.muteebaa.app.StartVoting"
}

// NOTE: Pass --console=plain to prevent Gradle progress bar from interfering with stdout when using the CLI
tasks.named<JavaExec>("run") {
    standardInput = System.`in` // Gradle sends empty stream by default
}
