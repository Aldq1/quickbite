import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import java.io.File
import java.io.FileInputStream

// ── Kitchen-optimised dark colour scheme ──────────────────────────────────────

val KitchenTheme = darkColorScheme(
    primary          = Color(0xFFE8430A),  // brand orange — new orders
    secondary        = Color(0xFF34C759),  // green — delivered
    tertiary         = Color(0xFF636366),  // grey — empty tables
    background       = Color(0xFF111111),
    surface          = Color(0xFF1C1C1E),
    surfaceVariant   = Color(0xFF2C2C2E),
    onBackground     = Color(0xFFFFFFFF),
    onSurface        = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline          = Color(0xFF3A3A3C),
    outlineVariant   = Color(0xFF2C2C2E),
)

// ── Entry point ───────────────────────────────────────────────────────────────

fun main() {
    val db = initFirestore()   // pure JVM call — happens once before the event loop

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "QuickBite — Waiter Command Center",
            state = WindowState(
                placement = WindowPlacement.Floating,
                size = DpSize(1360.dp, 860.dp)
            )
        ) {
            MaterialTheme(colorScheme = KitchenTheme) {
                WaiterApp(db = db)
            }
        }
    }
}

// ── Firebase Admin SDK initialisation ────────────────────────────────────────
//
// Credential lookup order (first existing file wins):
//   1. GOOGLE_APPLICATION_CREDENTIALS env var
//   2. ~/.config/quickbite/service-account.json
//   3. ./service-account.json  (working directory)
//
// To obtain a service-account.json:
//   Firebase Console → Project Settings → Service Accounts → Generate new private key
//
// Returns null when no credential file is found; the UI shows a setup screen.

fun initFirestore(): Firestore? {
    return try {
        val candidatePaths = listOfNotNull(
            System.getenv("GOOGLE_APPLICATION_CREDENTIALS"),
            "${System.getProperty("user.home")}/.config/quickbite/service-account.json",
            "./service-account.json"
        )
        val credFile = candidatePaths.firstOrNull { it.isNotBlank() && File(it).exists() }
            ?: return null

        val credentials = GoogleCredentials
            .fromStream(FileInputStream(credFile))
            .createScoped("https://www.googleapis.com/auth/cloud-platform")

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId("quickbite-7cc54")
                    .build()
            )
        }
        FirestoreClient.getFirestore()
    } catch (e: Exception) {
        System.err.println("[QuickBite] Firebase init failed: ${e.message}")
        null
    }
}
