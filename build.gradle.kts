plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.logify"
version = "1.0"

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
            • Intention: Log This — wrap any expression in a log call instantly
            • Bulk action: Normalize All Log Tags in File
            • Generator: Insert Kotlin Log Wrapper (Any.logd/e/i/w extensions)
            • Generator: Insert Java Log Wrapper (L.java utility class)
            • Inspection: Unguarded Log call — warns on Log.* not wrapped in BuildConfig.DEBUG (Java &amp; Kotlin)
            • Inspection: Sensitive data in log call — detects passwords/tokens/secrets in log arguments (Java &amp; Kotlin)
            • Inspection: Log call should use Timber — opt-in warning to migrate from Log to Timber (Java &amp; Kotlin)
            • Action: Wrap All Unguarded Logs in Project — bulk-fix every unguarded Log call
            • Action: Check Release Readiness — project-wide scan for debug logs, unguarded logs, and sensitive data leaks
            • Action: Convert All Log Calls to Timber — replaces every Log.d/e/i/w/v with the equivalent Timber call
            • Action: Remove Debug Log Calls — deletes all Log.d/i/v and Timber.d/i/v calls project-wide
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
