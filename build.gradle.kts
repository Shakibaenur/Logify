plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.loglens"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Target IntelliJ IDEA Community 2025.1 — compatible with Android Studio Narwhal+
        create("IC", "2025.1.4.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Java PSI APIs (PsiClass, PsiMethod, PsiElementFactory, …)
        bundledPlugin("com.intellij.java")

        // Kotlin PSI APIs (KtClass, KtNamedFunction, KtPsiFactory, …)
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }   // no upper bound — stays compatible with future builds
        }

        changeNotes = """
            1.0 – Initial release
            • Live templates: logd / loge / logi / logw / logt (Java &amp; Kotlin)
            • Intention: Normalize log tag
            • Bulk action: Normalize All Log Tags in File
            • Generator: Insert Kotlin Log Wrapper
            • Generator: Insert Java Log Wrapper (L.java)
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
