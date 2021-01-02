plugins {
    kotlin("jvm") version "1.4.21"
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir("src/main/kotlin")
    }
}

gradlePlugin {
    plugins {
        create("c-to-obj") {
            id = "c-to-obj"
            implementationClass = "org.jetbrains.lang.LanguagePlugin"
        }

        create ("make") {
            id = "make"
            implementationClass = "org.github.vvlevchenko.gradle.make.MakePlugin"
        }
    }
}