plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    // Match :common / :app (target 21). A JVM library requires compileJava
    // and compileKotlin to agree, and Kotlin can't inline common's target-21
    // bytecode at a lower target.
    jvmToolchain(21)
}

// Compiled directly into the app as a built-in extension (registered in
// ExtensionLoader), so it only needs the common API at compile time;
// okhttp and kotlin-stdlib are provided by the app at runtime.
dependencies {
    implementation(project(":common"))
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    compileOnly(libs.okhttp)
}
