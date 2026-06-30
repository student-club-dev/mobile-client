import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

val openApiOutput = layout.buildDirectory.dir("generated/openapi")

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "network" }
    }

    sourceSets {
        commonMain {
            // Generated API client from the OpenAPI JSON spec
            kotlin.srcDir(openApiOutput.map { it.dir("src/commonMain/kotlin") })
            dependencies {
                api(projects.dev.core.common)
                api(libs.ktor.client.core)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.serialization.json)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.auth)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "dev.core.network"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// ---- JSON (OpenAPI) -> Kotlin API generatsiyasi ----
openApiGenerate {
    generatorName.set("kotlin")
    library.set("multiplatform")
    inputSpec.set("$projectDir/openapi/student-clubs.json")
    outputDir.set(openApiOutput.get().asFile.path)
    packageName.set("dev.core.network.generated")
    apiPackage.set("dev.core.network.generated.api")
    modelPackage.set("dev.core.network.generated.model")
    configOptions.set(
        mapOf(
            "dateLibrary" to "kotlinx-datetime",
            "collectionType" to "list",
            "omitGradleWrapper" to "true",
            "generateApiTests" to "false",
            "generateModelTests" to "false",
            "generateApiDocumentation" to "false",
            "generateModelDocumentation" to "false",
        )
    )
}

// Kompilyatsiyadan oldin API generatsiyasi ishga tushsin
tasks.withType<AbstractKotlinCompileTool<*>>().configureEach {
    dependsOn(tasks.named("openApiGenerate"))
}
