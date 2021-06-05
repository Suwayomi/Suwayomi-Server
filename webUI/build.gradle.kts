plugins {
    id("com.github.node-gradle.node") version "3.0.1"
}

val nodeRoot = "${project.projectDir}/src"
node {
    nodeProjectDir.set(file(nodeRoot))
}

tasks {
    register<Copy>("copyBuild") {
        from(file("$nodeRoot/build"))
        into(file("$rootDir/server/src/main/resources/webUI"))

        dependsOn("yarn_build")
    }

    named("yarn_build") {
        dependsOn("yarn") // install node_modules
    }
}