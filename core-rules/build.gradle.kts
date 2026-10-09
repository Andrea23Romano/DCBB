plugins {
    kotlin("multiplatform")
}

// The rules engine is plain Kotlin: the JVM build feeds the CLI, the simulator and (later) Android;
// the JS build feeds the web test client.
kotlin {
    jvm {
        compilerOptions {
            // Java 17 bytecode keeps core-rules usable from Android later.
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    js {
        browser {
            // No JS-specific tests yet: the JVM suite covers the shared code.
            testTask { enabled = false }
        }
    }
    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

// The JVM suite covers the shared code; skip the JS test pipeline (and its npm install) in `check`.
tasks.named("check") { setDependsOn(listOf("jvmTest")) }
