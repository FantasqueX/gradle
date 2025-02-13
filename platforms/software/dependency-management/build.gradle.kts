plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = """This project contains most of the dependency management logic of Gradle:
    |* the resolution engine,
    |* how to retrieve and process dependencies and their metadata,
    |* the dependency locking and verification implementations.
    |
    |DSL facing APIs are to be found in 'core-api'""".trimMargin()

dependencies {
    api(project(":base-annotations"))
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":build-option"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":enterprise-logging"))
    api(project(":enterprise-operations"))
    api(project(":execution"))
    api(project(":file-collections"))
    api(project(":file-temp"))
    api(project(":files"))
    api(project(":functional"))
    api(project(":hashing"))
    api(project(":logging"))
    api(project(":messaging"))
    api(project(":model-core"))
    api(project(":persistent-cache"))
    api(project(":problems-api"))
    api(project(":resources"))
    api(project(":security"))
    api(project(":snapshots"))

    api(libs.bouncycastlePgp)
    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.ivy)
    api(libs.jsr305)
    api(libs.maven3Settings)
    api(libs.maven3SettingsBuilder)
    api(libs.slf4jApi)

    implementation(project(":base-services-groovy"))
    implementation(project(":logging-api"))
    implementation(project(":resources-http"))

    implementation(libs.ant)
    implementation(libs.asm)
    implementation(libs.asmCommons)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.fastutil)
    implementation(libs.gson)
    implementation(libs.httpcore)

    testImplementation(project(":build-cache-packaging"))
    testImplementation(project(":diagnostics"))
    testImplementation(project(":process-services"))
    testImplementation(libs.asmUtil)
    testImplementation(libs.commonsHttpclient)
    testImplementation(libs.groovyXml)
    testImplementation(libs.jsoup)
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":core-api")))
    testImplementation(testFixtures(project(":execution")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":resources-http")))
    testImplementation(testFixtures(project(":snapshots")))
    testImplementation(testFixtures(project(":version-control")))

    integTestImplementation(project(":build-option"))
    integTestImplementation(libs.jansi)
    integTestImplementation(libs.ansiControlSequenceUtil)
    integTestImplementation(libs.groovyJson)
    integTestImplementation(libs.socksProxy) {
        because("SOCKS proxy not part of internal-integ-testing api, since it has limited usefulness, so must be explicitly depended upon")
    }
    integTestImplementation(testFixtures(project(":security")))
    integTestImplementation(testFixtures(project(":model-core")))

    testFixturesApi(project(":base-services")) {
        because("Test fixtures export the Action class")
    }
    testFixturesApi(project(":persistent-cache")) {
        because("Test fixtures export the CacheAccess class")
    }

    testFixturesApi(libs.jetty)
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(testFixtures(project(":resources-http")))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":messaging"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.inject)
    testFixturesImplementation(libs.groovyJson)
    testFixturesImplementation(libs.guava) {
        because("Groovy compiler reflects on private field on TextUtil")
    }
    testFixturesImplementation(libs.bouncycastlePgp)
    testFixturesApi(libs.testcontainersSpock) {
        because("API because of Groovy compiler bug leaking internals")
    }
    testFixturesImplementation(project(":jvm-services")) {
        because("Groovy compiler bug leaks internals")
    }
    testFixturesImplementation(libs.jettyWebApp) {
        because("Groovy compiler bug leaks internals")
    }

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestImplementation(project(":launcher")) {
        because("Daemon fixtures need DaemonRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("Need access to java platforms")
    }
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
    crossVersionTestImplementation(libs.jettyWebApp)
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

testFilesCleanup.reportOnly = true

tasks.clean {
    val testFiles = layout.buildDirectory.dir("tmp/teŝt files")
    doFirst {
        // On daemon crash, read-only cache tests can leave read-only files around.
        // clean now takes care of those files as well
        testFiles.get().asFileTree.matching {
            include("**/read-only-cache/**")
        }.visit { this.file.setWritable(true) }
    }
}
