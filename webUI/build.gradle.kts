plugins {
    id("com.github.node-gradle.node") version "3.0.1"
}

node {
    nodeProjectDir.set(file("${project.projectDir}/react/"))
}

tasks {
    register<Copy>("copyBuild") {
        from(file("${node.nodeProjectDir}/build"))
        into(file("$rootDir/server/src/main/resources/react"))

        dependsOn("yarn_build")
    }

    named("yarn_build") {
        dependsOn("yarn") // install node_modules
    }
}