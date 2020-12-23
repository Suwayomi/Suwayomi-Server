plugins {
    id("com.moowork.node") version "1.3.1"
}

node {
    workDir = file("${project.projectDir}/webUI/TachiWeb-React")
    nodeModulesDir = file("${project.projectDir}/webUI/TachiWeb-React/node_modules")
}