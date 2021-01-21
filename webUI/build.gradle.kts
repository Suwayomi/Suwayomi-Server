plugins {
    id("com.moowork.node") version "1.3.1"
}

node {
    workDir = file("${project.projectDir}/react/")
    nodeModulesDir = file("${project.projectDir}/react/")
}

tasks.named("yarn_build") {
    dependsOn("yarn") // install node_moduels
}

tasks.register<Copy>("copyBuild") {
    from(file("$rootDir/webUI/react/build"))
    into(file("$rootDir/server/src/main/resources/react"))
}

tasks.named("copyBuild") {
    dependsOn("yarn_build")
}
