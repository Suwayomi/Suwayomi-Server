plugins {
    id("com.github.node-gradle.node") version "3.0.1"
}

val nodeRoot = "${project.projectDir}/react"
node {
    nodeProjectDir.set(file(nodeRoot))
}

tasks {
    register<Copy>("copyBuild") {
        from(file("$nodeRoot/build"))
        into(file("$rootDir/server/src/main/resources/react"))

        dependsOn("yarn_build")
    }

    named("yarn_build") {
        dependsOn("yarn") // install node_modules
    }
}