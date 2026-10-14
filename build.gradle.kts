plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    id("io.github.ttypic.swiftklib") apply false
}

tasks.register("koverReport") {
    group = "verification"
    description = "Generates ComposeApp Kover XML, HTML, and log coverage reports."
    dependsOn(":composeApp:koverXmlReport", ":composeApp:koverHtmlReport", ":composeApp:koverLog")
}

// Report generation alone does not enforce the workflow coverage threshold.
tasks.register<Exec>("koverVerify") {
    group = "verification"
    dependsOn("koverReport")
    commandLine("python3", ".ai/scripts/verify-coverage.py", "composeApp/build/reports/kover/report.xml")
}
