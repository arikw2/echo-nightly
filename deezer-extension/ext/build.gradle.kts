plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

// Compiled directly into the app as a built-in extension (registered in
// ExtensionLoader), so it only needs the common API at compile time;
// okhttp and kotlin-stdlib are provided by the app at runtime.
dependencies {
    implementation(project(":common"))
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    compileOnly(libs.okhttp)
}
