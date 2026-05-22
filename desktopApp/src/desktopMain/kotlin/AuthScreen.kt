// ─────────────────────────────────────────────────────────────────────────────
// AuthScreen.kt — QuickBite Enterprise Suite
// Login + Register flows backed by Firebase Auth REST API + Admin SDK Firestore.
//
// Why REST API instead of Firebase Client SDK?
//   The Desktop app uses the Firebase Admin SDK (service-account / JVM), which
//   does not expose signInWithEmailAndPassword — that is a client-only operation.
//   The REST endpoint (identitytoolkit.googleapis.com) accepts the project's
//   Web API key and works on any HTTP client.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.cloud.firestore.Firestore
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

// ── Constants ─────────────────────────────────────────────────────────────────

// Web API key from google-services.json — not a secret in Firebase's model;
// access is gated by Firebase Security Rules, not by key secrecy.
private const val FIREBASE_WEB_API_KEY = "AIzaSyCkV2R3RRQ5KEmg3PzDfIf-NF93BOmmcAo"

private val ROLES = listOf("Restaurant", "Producător (B2B)", "Candidat HR", "Agent KYC")

// ── Auth state holder (ViewModel-style) ───────────────────────────────────────

class AuthStateHolder(private val db: Firestore?) {

    var isLoading    by mutableStateOf(false);    private set
    var errorMessage by mutableStateOf<String?>(null); private set
    var loggedInUid  by mutableStateOf(""); private set

    private val authClient = FirebaseAuthRestClient(FIREBASE_WEB_API_KEY)
    private val scope      = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Login ─────────────────────────────────────────────────────────────────

    fun login(email: String, password: String, onSuccess: (AppRole, String) -> Unit) {
        scope.launch {
            isLoading    = true
            errorMessage = null
            try {
                val uid = authClient.signIn(email.trim(), password)

                val userDoc = withContext(Dispatchers.IO) {
                    db?.collection("users")?.document(uid)?.get()?.get()
                } ?: run {
                    errorMessage = "Serviciul Firebase nu este disponibil."
                    return@launch
                }

                if (!userDoc.exists()) {
                    errorMessage = "Profilul contului nu a fost găsit în sistem."
                    return@launch
                }

                val role      = userDoc.getString("role")       ?: ""
                val kycStatus = userDoc.getString("kyc_status")
                loggedInUid   = uid

                when (role) {
                    "Restaurant" -> when (kycStatus) {
                        "PENDING"  -> errorMessage =
                            "Contul dumneavoastră este în curs de verificare KYC. " +
                            "Accesul va fi permis după aprobarea unui agent."
                        "APPROVED" -> onSuccess(AppRole.RESTAURANT_ADMIN, uid)
                        else       -> errorMessage = "Statut KYC nerecunoscut. Contactați suportul."
                    }
                    "Agent KYC"        -> onSuccess(AppRole.KYC_AGENT, uid)
                    "Producător (B2B)" -> onSuccess(AppRole.PRODUCER, uid)
                    "Candidat HR"      -> onSuccess(AppRole.CANDIDATE, uid)
                    else               -> errorMessage = "Rol de cont nerecunoscut: \"$role\"."
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "A apărut o eroare neașteptată."
            } finally {
                isLoading = false
            }
        }
    }

    // ── Register ──────────────────────────────────────────────────────────────

    fun register(
        email: String, password: String, name: String, role: String,
        companyName: String, cui: String,
        onSuccess: () -> Unit
    ) {
        scope.launch {
            isLoading    = true
            errorMessage = null
            try {
                val uid = authClient.signUp(email.trim(), password)

                val userDocData: MutableMap<String, Any> = mutableMapOf(
                    "uid"   to uid,
                    "email" to email.trim(),
                    "name"  to name.trim(),
                    "role"  to role
                )
                if (role == "Restaurant") {
                    userDocData["companyName"] = companyName.trim()
                    userDocData["cui"]         = cui.trim()
                    userDocData["kyc_status"]  = "PENDING"
                }

                withContext(Dispatchers.IO) {
                    db?.collection("users")?.document(uid)?.set(userDocData)?.get()
                }

                onSuccess()
            } catch (e: Exception) {
                errorMessage = e.message ?: "A apărut o eroare la înregistrare."
            } finally {
                isLoading = false
            }
        }
    }

    fun clearError() { errorMessage = null }

    fun dispose() { scope.cancel() }
}

// ── Firebase Auth REST client ─────────────────────────────────────────────────

private class FirebaseAuthRestClient(private val apiKey: String) {

    suspend fun signUp(email: String, password: String): String = withContext(Dispatchers.IO) {
        val response = post("accounts:signUp", buildBody(email, password))
        extractUidOrThrow(response)
    }

    suspend fun signIn(email: String, password: String): String = withContext(Dispatchers.IO) {
        val response = post("accounts:signInWithPassword", buildBody(email, password))
        extractUidOrThrow(response)
    }

    private fun buildBody(email: String, password: String): String =
        JsonObject().apply {
            addProperty("email", email)
            addProperty("password", password)
            addProperty("returnSecureToken", true)
        }.toString()

    private fun extractUidOrThrow(responseJson: String): String {
        val json = Gson().fromJson(responseJson, JsonObject::class.java)
        if (json.has("error")) {
            val code = json.getAsJsonObject("error").get("message")?.asString ?: "UNKNOWN"
            throw Exception(mapErrorCode(code))
        }
        return json.get("localId")?.asString
            ?: throw Exception("Răspuns invalid de la server.")
    }

    private fun post(endpoint: String, body: String): String {
        val url  = URL("https://identitytoolkit.googleapis.com/v1/$endpoint?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput      = true
            conn.connectTimeout = 15_000
            conn.readTimeout    = 15_000
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode == 200) conn.inputStream else conn.errorStream
            stream?.bufferedReader(Charsets.UTF_8)?.readText()
                ?: throw Exception("Niciun răspuns de la server.")
        } finally {
            conn.disconnect()
        }
    }

    private fun mapErrorCode(code: String): String = when {
        code.contains("EMAIL_EXISTS")                -> "Această adresă de email este deja utilizată."
        code.contains("INVALID_EMAIL")               -> "Adresă de email invalidă."
        code.contains("WEAK_PASSWORD")               -> "Parola este prea slabă — folosiți minim 6 caractere."
        code.contains("OPERATION_NOT_ALLOWED")       -> "Autentificarea prin email nu este activată în Firebase."
        code.contains("TOO_MANY_ATTEMPTS_TRY_LATER") -> "Prea multe încercări. Așteptați câteva minute."
        code.contains("EMAIL_NOT_FOUND")             -> "Adresa de email nu este înregistrată."
        code.contains("INVALID_PASSWORD")            -> "Parolă incorectă."
        code.contains("INVALID_LOGIN_CREDENTIALS")   -> "Email sau parolă incorectă."
        code.contains("USER_DISABLED")               -> "Contul a fost dezactivat. Contactați suportul."
        else                                         -> "Eroare autentificare: $code"
    }
}

// ── Auth screen ───────────────────────────────────────────────────────────────

private enum class AuthTab { LOGIN, REGISTER }

// Auth-screen local colour aliases (mirrors the Enterprise palette without
// forcing a file-level dependency on private vals in EnterpriseSuiteApp.kt).
private val ABg      = Color(0xFF080912)
private val ASurface = Color(0xFF10111E)
private val ABrand   = Color(0xFF6C7BFF)
private val AText    = Color(0xFFFFFFFF)
private val AMuted   = Color(0xFF9090A8)
private val AGlass   = Color(0x0DFFFFFF)
private val AGlassBorder = Color(0xFF2A2A40)
private val AOrange  = Color(0xFFE8430A)
private val AGreen   = Color(0xFF34C759)
private val ARed     = Color(0xFFFF3B30)

@Composable
fun AuthScreen(db: Firestore?, onAuthenticated: (AppRole, String) -> Unit, onWaiterAccess: () -> Unit = {}) {
    val holder = remember(db) { AuthStateHolder(db) }
    DisposableEffect(holder) { onDispose { holder.dispose() } }

    var activeTab       by remember { mutableStateOf(AuthTab.LOGIN) }
    var registerSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab) { holder.clearError() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0B18), ABg))),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            shape     = RoundedCornerShape(28.dp),
            colors    = CardDefaults.elevatedCardColors(containerColor = ASurface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 24.dp),
            modifier  = Modifier.width(520.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                AuthHeader()

                if (registerSuccess) {
                    RegisterSuccessPanel(onBack = {
                        registerSuccess = false
                        activeTab = AuthTab.LOGIN
                        holder.clearError()
                    })
                    return@Column
                }

                // Tab switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF181928))
                ) {
                    AuthTabButton(
                        label    = "Autentificare",
                        isActive = activeTab == AuthTab.LOGIN,
                        onClick  = { activeTab = AuthTab.LOGIN },
                        modifier = Modifier.weight(1f)
                    )
                    AuthTabButton(
                        label    = "Înregistrare",
                        isActive = activeTab == AuthTab.REGISTER,
                        onClick  = { activeTab = AuthTab.REGISTER },
                        modifier = Modifier.weight(1f)
                    )
                }

                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        val dir = if (targetState == AuthTab.REGISTER) 1 else -1
                        (slideInHorizontally(tween(220)) { it / 6 * dir } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(180)) { -it / 6 * dir } + fadeOut(tween(180)))
                    }
                ) { tab ->
                    when (tab) {
                        AuthTab.LOGIN    -> LoginForm(holder = holder, onAuthenticated = onAuthenticated)
                        AuthTab.REGISTER -> RegisterForm(holder = holder, onSuccess = { registerSuccess = true })
                    }
                }
            }
        }

        TextButton(
            onClick  = onWaiterAccess,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        ) {
            Icon(Icons.Rounded.TableRestaurant, null, tint = AMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text("Acces Ospătari", color = AMuted, fontSize = 13.sp)
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun AuthHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .background(AOrange, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("QB", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Column {
                Text(
                    "QuickBite",
                    fontSize      = 22.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = AText,
                    letterSpacing = (-0.4).sp
                )
                Text("Enterprise Suite", fontSize = 12.sp, color = ABrand, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Autentificați-vă sau creați un cont nou",
            fontSize  = 13.sp,
            color     = AMuted,
            textAlign = TextAlign.Center
        )
    }
}

// ── Tab button ────────────────────────────────────────────────────────────────

@Composable
private fun AuthTabButton(label: String, isActive: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val bg  by animateColorAsState(if (isActive) ABrand else Color.Transparent, tween(200))
    val txt by animateColorAsState(if (isActive) Color.White else AMuted, tween(200))
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize   = 14.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color      = txt
        )
    }
}

// ── Login form ────────────────────────────────────────────────────────────────

@Composable
private fun LoginForm(holder: AuthStateHolder, onAuthenticated: (AppRole, String) -> Unit) {
    var email   by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }

    val canSubmit = email.isNotBlank() && password.isNotBlank() && !holder.isLoading

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AuthTextField(
            value         = email,
            onValueChange = { email = it; holder.clearError() },
            label         = "Email",
            keyboardType  = KeyboardType.Email,
            leadingIcon   = Icons.Rounded.Email
        )
        AuthTextField(
            value           = password,
            onValueChange   = { password = it; holder.clearError() },
            label           = "Parolă",
            keyboardType    = KeyboardType.Password,
            leadingIcon     = Icons.Rounded.Lock,
            visualTransform = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon    = if (showPwd) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            onTrailingClick = { showPwd = !showPwd }
        )

        ErrorBanner(holder.errorMessage)

        Button(
            onClick  = { holder.login(email, password, onAuthenticated) },
            enabled  = canSubmit,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = ABrand)
        ) {
            if (holder.isLoading) {
                CircularProgressIndicator(
                    color       = Color.White,
                    modifier    = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(Icons.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Autentificare", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

// ── Register form ─────────────────────────────────────────────────────────────

@Composable
private fun RegisterForm(holder: AuthStateHolder, onSuccess: () -> Unit) {
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var showPwd     by remember { mutableStateOf(false) }
    var name        by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(ROLES[0]) }
    var companyName by remember { mutableStateOf("") }
    var cui         by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val isRestaurant = selectedRole == "Restaurant"
    val canSubmit    = email.isNotBlank() && password.isNotBlank() && name.isNotBlank() &&
        (!isRestaurant || (companyName.isNotBlank() && cui.isNotBlank())) &&
        !holder.isLoading

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AuthTextField(
            value         = name,
            onValueChange = { name = it; holder.clearError() },
            label         = "Nume Complet",
            keyboardType  = KeyboardType.Text,
            leadingIcon   = Icons.Rounded.Person
        )
        AuthTextField(
            value         = email,
            onValueChange = { email = it; holder.clearError() },
            label         = "Email",
            keyboardType  = KeyboardType.Email,
            leadingIcon   = Icons.Rounded.Email
        )
        AuthTextField(
            value           = password,
            onValueChange   = { password = it; holder.clearError() },
            label           = "Parolă (minim 6 caractere)",
            keyboardType    = KeyboardType.Password,
            leadingIcon     = Icons.Rounded.Lock,
            visualTransform = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon    = if (showPwd) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            onTrailingClick = { showPwd = !showPwd }
        )

        // Role selector
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tip cont", fontSize = 12.sp, color = AMuted, fontWeight = FontWeight.Medium)
            Box {
                OutlinedButton(
                    onClick  = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, AGlassBorder),
                    colors   = ButtonDefaults.outlinedButtonColors(containerColor = AGlass)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Badge, null, tint = AMuted, modifier = Modifier.size(18.dp))
                            Text(selectedRole, fontSize = 14.sp, color = AText)
                        }
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = AMuted, modifier = Modifier.size(18.dp))
                    }
                }
                DropdownMenu(
                    expanded         = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier         = Modifier.background(Color(0xFF181928))
                ) {
                    ROLES.forEach { r ->
                        DropdownMenuItem(
                            text = {
                                Text(r, color = if (r == selectedRole) ABrand else AText, fontSize = 14.sp)
                            },
                            onClick = {
                                selectedRole = r
                                dropdownExpanded = false
                                holder.clearError()
                            },
                            leadingIcon = {
                                if (r == selectedRole) {
                                    Icon(Icons.Rounded.Check, null, tint = ABrand, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
            }
        }

        // Restaurant-only fields (animated reveal)
        AnimatedVisibility(
            visible = isRestaurant,
            enter   = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit    = shrinkVertically(tween(180)) + fadeOut(tween(180))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // KYC info banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ABrand.copy(alpha = 0.10f))
                        .border(1.dp, ABrand.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Info, null, tint = ABrand, modifier = Modifier.size(15.dp))
                    Text(
                        "Contul va fi activat după verificarea KYC de un agent autorizat.",
                        fontSize   = 12.sp,
                        color      = AMuted,
                        lineHeight = 18.sp
                    )
                }

                AuthTextField(
                    value         = companyName,
                    onValueChange = { companyName = it; holder.clearError() },
                    label         = "Nume Companie",
                    keyboardType  = KeyboardType.Text,
                    leadingIcon   = Icons.Rounded.Business
                )
                AuthTextField(
                    value         = cui,
                    onValueChange = { cui = it; holder.clearError() },
                    label         = "CUI (Cod Unic de Identificare)",
                    keyboardType  = KeyboardType.Number,
                    leadingIcon   = Icons.Rounded.Tag
                )
            }
        }

        ErrorBanner(holder.errorMessage)

        Button(
            onClick  = { holder.register(email, password, name, selectedRole, companyName, cui, onSuccess) },
            enabled  = canSubmit,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = ABrand)
        ) {
            if (holder.isLoading) {
                CircularProgressIndicator(
                    color       = Color.White,
                    modifier    = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(Icons.Rounded.PersonAdd, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Creați Cont", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

// ── Register success panel ────────────────────────────────────────────────────

@Composable
private fun RegisterSuccessPanel(onBack: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(72.dp)
                .background(AGreen.copy(alpha = 0.12f), CircleShape)
                .border(1.dp, AGreen.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.CheckCircle, null, tint = AGreen, modifier = Modifier.size(40.dp))
        }
        Text(
            "Cont creat cu succes!",
            fontSize   = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = AText,
            textAlign  = TextAlign.Center
        )
        Text(
            "Puteți acum să vă autentificați cu credențialele înregistrate.\n" +
            "Dacă ați ales rolul Restaurant, așteptați aprobarea KYC.",
            fontSize   = 14.sp,
            color      = AMuted,
            textAlign  = TextAlign.Center,
            lineHeight = 21.sp
        )
        Button(
            onClick  = onBack,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = ABrand)
        ) {
            Icon(Icons.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Mergeți la Autentificare", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

// ── Error banner ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String?) {
    AnimatedVisibility(
        visible = message != null,
        enter   = expandVertically(tween(200)) + fadeIn(tween(200)),
        exit    = shrinkVertically(tween(150)) + fadeOut(tween(150))
    ) {
        if (message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ARed.copy(alpha = 0.10f))
                    .border(1.dp, ARed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = ARed, modifier = Modifier.size(17.dp))
                Text(message, fontSize = 13.sp, color = ARed, lineHeight = 19.sp)
            }
        }
    }
}

// ── Auth text field ───────────────────────────────────────────────────────────

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    leadingIcon: ImageVector,
    visualTransform: VisualTransformation = VisualTransformation.None,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    OutlinedTextField(
        value                = value,
        onValueChange        = onValueChange,
        label                = { Text(label, fontSize = 13.sp) },
        modifier             = Modifier.fillMaxWidth(),
        singleLine           = true,
        shape                = RoundedCornerShape(12.dp),
        keyboardOptions      = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransform,
        leadingIcon          = {
            Icon(leadingIcon, null, tint = AMuted, modifier = Modifier.size(18.dp))
        },
        trailingIcon = if (trailingIcon != null && onTrailingClick != null) {
            { IconButton(onClick = onTrailingClick) {
                Icon(trailingIcon, null, tint = AMuted, modifier = Modifier.size(18.dp))
            } }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = ABrand,
            unfocusedBorderColor    = AGlassBorder,
            focusedLabelColor       = ABrand,
            unfocusedLabelColor     = AMuted,
            focusedTextColor        = AText,
            unfocusedTextColor      = AText,
            cursorColor             = ABrand,
            focusedContainerColor   = AGlass,
            unfocusedContainerColor = AGlass
        )
    )
}
