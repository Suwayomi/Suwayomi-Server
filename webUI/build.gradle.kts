plugins {
    id("com.github.node-gradle.node") version "3.0.1"
}

node {
    nodeProjectDir.set(file("${project.projectDir}/react/"))
}

tasks.named("yarn_build") {
    dependsOn("yarn") // install node_modules
}

tasks.register<Copy>("copyBuild") {
    from(file("$rootDir/webUI/react/build"))
    into(file("$rootDir/server/src/main/resources/react"))
}

tasks.named("copyBuild") {
    dependsOn("yarn_build")
}
