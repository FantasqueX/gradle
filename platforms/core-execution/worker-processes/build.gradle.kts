plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Infrastructure that bootstraps a worker process"

gradlebuildJava.usedInWorkers()

dependencies {
    api(project(":base-annotations"))
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":logging"))
    api(project(":logging-api"))
    api(project(":messaging"))
    api(project(":problems-api"))
    api(project(":process-services"))

    implementation(project(":enterprise-logging"))
    implementation(project(":native"))

    implementation(libs.slf4jApi)

    testImplementation(testFixtures(project(":core")))
}
