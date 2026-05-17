import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(projects.shared)

                // Compose Desktop runtime + Material 3
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)

                // Coroutines with Swing dispatcher (required for Compose Desktop)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

                // Firebase Admin SDK — provides Firestore with real-time gRPC listeners on JVM.
                // Requires a service-account.json (see Main.kt for the lookup order).
                implementation("com.google.firebase:firebase-admin:9.3.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "QuickBite Waiter"
            packageVersion = "1.0.0"
            description = "QuickBite Waiter Command Center"
        }

        // Allow gRPC / Netty to access internal JVM APIs on Java 11+
        jvmArgs += listOf(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED"
        )
    }
}
