plugins {
    kotlin("multiplatform")
}

// A minimal browser client for play-testing the combat prototype. The rules engine runs in the page itself
// (core-rules compiled to JavaScript), so the build is one self-contained HTML file.
kotlin {
    js {
        outputModuleName.set("dcbb-web")
        browser {
            testTask { enabled = false }
        }
        binaries.executable()
        useEsModules()
    }
    sourceSets {
        jsMain.dependencies {
            implementation(project(":core-rules"))
        }
    }
}

// Inlines the compiled client into one self-contained page, in two shapes:
// build/dist/index.html is a complete document to open locally; build/dist/artifact.html is the same page without the
// document skeleton, for publishing as a Claude artifact (the host adds the skeleton).
val webDist by tasks.registering {
    group = "distribution"
    description = "Builds the self-contained web client into build/dist."
    dependsOn("jsProductionExecutableCompileSync")
    val page = layout.projectDirectory.file("src/jsMain/resources/page.html")
    val js = layout.buildDirectory.file("compileSync/js/main/productionExecutable/kotlin/dcbb-web.mjs")
    val out = layout.buildDirectory.dir("dist")
    inputs.file(page)
    inputs.files(js)
    outputs.dir(out)
    doLast {
        val (head, body) = page.asFile.readText().split("<!--BODY-->")
        val code = js.get().asFile.readLines()
            .filterNot { it.startsWith("//# sourceMappingURL") }
            .joinToString("\n")
            .replace("</script", "<\\/script")
        val app = body.replace("<!--APP_JS-->", "<script type=\"module\">\n$code\n</script>").trim()
        val dir = out.get().asFile.apply { mkdirs() }
        dir.resolve("artifact.html").writeText(head.trim() + "\n" + app + "\n")
        dir.resolve("index.html").writeText(
            "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n" +
                head.trim() + "\n</head>\n<body>\n" + app + "\n</body>\n</html>\n",
        )
    }
}

// The page comes straight from the compiler's ES module (webDist), so the default webpack bundle and the npm install
// behind it aren't needed. Keep `assemble` and `check` to what this project uses, so builds need no Node.js.
tasks.named("assemble") { setDependsOn(listOf(webDist)) }
tasks.named("check") { setDependsOn(emptyList<Any>()) }
