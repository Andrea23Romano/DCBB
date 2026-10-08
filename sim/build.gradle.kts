plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core-rules"))
}

application {
    mainClass.set("dcbb.sim.MainKt")
    applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
