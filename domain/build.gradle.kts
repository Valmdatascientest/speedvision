plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    testImplementation(kotlin("test-junit"))
}

tasks.register<JavaExec>("benchmarkSpeed") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("fr.speedvision.domain.SpeedBenchmark")
    args(
        layout.buildDirectory
            .file("reports/speed/benchmark.csv")
            .get()
            .asFile.absolutePath,
    )
}
