plugins {
    id("com.moowork.node") version "1.3.1"
}

node {
    workDir = file("${project.projectDir}/react/")
    nodeModulesDir = file("${project.projectDir}/react/node_modules")
}