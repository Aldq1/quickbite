package com.example.quickbite.android.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Palette ───────────────────────────────────────────────────────────────────

private val PBg          = Color(0xFF0C0C0C)
private val PSurface     = Color(0xFF161616)
private val PGlass       = Color(0x14FFFFFF)
private val PGlassBorder = Color(0x1AFFFFFF)
private val PBrand       = Color(0xFFE8430A)
private val PTextPrimary = Color(0xFFFFFFFF)
private val PTextMuted   = Color(0xFF9A9A9A)
private val PRed         = Color(0xFFFF3B30)

private val LANGUAGES = listOf("Română", "English")

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ProfileSettingsViewModel : ViewModel() {

    private val db  = FirebaseFirestore.getInstance()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid

    private val _userName             = MutableStateFlow("")
    val userName: StateFlow<String>   = _userName.asStateFlow()

    private val _language             = MutableStateFlow("Română")
    val language: StateFlow<String>   = _language.asStateFlow()

    private val _notificationsEnabled            = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _isSaving            = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess            = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _errorMessage             = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?>  = _errorMessage.asStateFlow()

    private var listener: ListenerRegistration? = null

    init {
        uid?.let { id ->
            listener = db.collection("users").document(id)
                .addSnapshotListener { snap, _ ->
                    if (snap == null) return@addSnapshotListener
                    _userName.value             = snap.getString("name") ?: ""
                    _language.value             = snap.getString("language") ?: "Română"
                    _notificationsEnabled.value = snap.getBoolean("notificationsEnabled") ?: true
                }
        }
    }

    // Firestore callbacks — no coroutines-play-services dependency needed.
    fun saveProfile(name: String, language: String, notificationsEnabled: Boolean) {
        val id = uid ?: return
        _isSaving.value     = true
        _saveSuccess.value  = false
        _errorMessage.value = null
        db.collection("users").document(id)
            .update(
                mapOf(
                    "name"                 to name.trim(),
                    "language"             to language,
                    "notificationsEnabled" to notificationsEnabled
                )
            )
            .addOnSuccessListener {
                _isSaving.value    = false
                _saveSuccess.value = true
            }
            .addOnFailureListener { e ->
                _isSaving.value     = false
                _errorMessage.value = "Nu s-au putut salva modificările: ${e.message}"
            }
    }

    fun clearSuccess() { _saveSuccess.value = false }
    fun clearError()   { _errorMessage.value = null }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    onSignOut : () -> Unit,
    onBack    : () -> Unit,
    vm        : ProfileSettingsViewModel = viewModel()
) {
    val userName             by vm.userName.collectAsStateWithLifecycle()
    val language             by vm.language.collectAsStateWithLifecycle()
    val notificationsEnabled by vm.notificationsEnabled.collectAsStateWithLifecycle()
    val isSaving             by vm.isSaving.collectAsStateWithLifecycle()
    val saveSuccess          by vm.saveSuccess.collectAsStateWithLifecycle()
    val errorMessage         by vm.errorMessage.collectAsStateWithLifecycle()

    // Local editable state — seeded from remote once the listener fires
    var nameInput        by remember(userName) { mutableStateOf(userName) }
    var selectedLanguage by remember(language) { mutableStateOf(language) }
    var notifToggle      by remember(notificationsEnabled) { mutableStateOf(notificationsEnabled) }
    var languageExpanded by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            snackbarHostState.showSnackbar("Profil actualizat cu succes!")
            vm.clearSuccess()
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profil & Setări",
                        fontWeight    = FontWeight.ExtraBold,
                        color         = PTextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Înapoi", tint = PTextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PBg)
            )
        },
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = PBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Avatar ─────────────────────────────────────────────────────────
            Column(
                modifier            = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier         = Modifier
                        .size(84.dp)
                        .background(PBrand.copy(alpha = 0.14f), CircleShape)
                        .border(2.dp, PBrand.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        nameInput.take(2).uppercase().ifBlank { "QB" },
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = PBrand
                    )
                }
                Text(
                    FirebaseAuth.getInstance().currentUser?.email ?: "",
                    fontSize = 13.sp,
                    color    = PTextMuted
                )
            }

            // ── Section: Cont ──────────────────────────────────────────────────
            ProfileSectionLabel("CONT")
            SettingsCard {
                // Name field
                OutlinedTextField(
                    value         = nameInput,
                    onValueChange = { nameInput = it },
                    label         = { Text("Nume complet") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    leadingIcon   = {
                        Icon(Icons.Rounded.Person, null, tint = PTextMuted, modifier = Modifier.size(18.dp))
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = settingsFieldColors()
                )

                // Language selector
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Limbă interfață", fontSize = 12.sp, color = PTextMuted, fontWeight = FontWeight.Medium)
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PGlass)
                                .border(1.dp, PGlassBorder, RoundedCornerShape(12.dp))
                                .clickable { languageExpanded = true }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.Language, null, tint = PTextMuted, modifier = Modifier.size(18.dp))
                                Text(selectedLanguage, fontSize = 14.sp, color = PTextPrimary)
                            }
                            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = PTextMuted, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded         = languageExpanded,
                            onDismissRequest = { languageExpanded = false },
                            modifier         = Modifier.background(PSurface)
                        ) {
                            LANGUAGES.forEach { lang ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            lang,
                                            color    = if (lang == selectedLanguage) PBrand else PTextPrimary,
                                            fontSize = 14.sp
                                        )
                                    },
                                    leadingIcon = if (lang == selectedLanguage) {
                                        {
                                            Icon(
                                                Icons.Rounded.Check,
                                                null,
                                                tint     = PBrand,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = { selectedLanguage = lang; languageExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            // ── Section: Notificări ────────────────────────────────────────────
            ProfileSectionLabel("NOTIFICĂRI")
            SettingsCard {
                SettingsToggleRow(
                    icon            = Icons.Rounded.Notifications,
                    title           = "Notificări Push",
                    subtitle        = "Actualizări comenzi și oferte",
                    checked         = notifToggle,
                    onCheckedChange = { notifToggle = it }
                )
            }

            // ── Save ───────────────────────────────────────────────────────────
            val isDirty = nameInput != userName ||
                selectedLanguage != language ||
                notifToggle != notificationsEnabled

            Button(
                onClick  = { vm.saveProfile(nameInput, selectedLanguage, notifToggle) },
                enabled  = isDirty && nameInput.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PBrand)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color       = Color.White,
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Rounded.Save, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Salvează modificările",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White
                    )
                }
            }

            // ── Section: Sesiune ───────────────────────────────────────────────
            ProfileSectionLabel("SESIUNE")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showLogoutDialog = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier         = Modifier
                            .size(38.dp)
                            .background(PRed.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Logout, null, tint = PRed, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Deconectare",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = PRed
                        )
                        Text("Ieșiți din sesiunea curentă", fontSize = 12.sp, color = PTextMuted)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = PTextMuted, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Logout confirmation dialog ─────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest  = { showLogoutDialog = false },
            containerColor    = PSurface,
            titleContentColor = PTextPrimary,
            textContentColor  = PTextMuted,
            title = { Text("Deconectare", fontWeight = FontWeight.ExtraBold) },
            text  = { Text("Sigur doriți să vă deconectați din cont?") },
            confirmButton = {
                Button(
                    onClick  = {
                        showLogoutDialog = false
                        FirebaseAuth.getInstance().signOut()
                        onSignOut()
                    },
                    shape  = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PRed)
                ) {
                    Text("Deconectare", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Anulează", color = PTextMuted)
                }
            }
        )
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun ProfileSectionLabel(label: String) {
    Text(
        label,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.ExtraBold,
        color         = PTextMuted,
        letterSpacing = 1.2.sp
    )
}

// ── Settings card container ───────────────────────────────────────────────────

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = PSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content             = content
        )
    }
}

// ── Toggle row ────────────────────────────────────────────────────────────────

@Composable
private fun SettingsToggleRow(
    icon            : ImageVector,
    title           : String,
    subtitle        : String,
    checked         : Boolean,
    onCheckedChange : (Boolean) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(38.dp)
                    .background(PBrand.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = PBrand, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PTextPrimary)
                Text(subtitle, fontSize = 12.sp, color = PTextMuted)
            }
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor  = Color.White,
                checkedTrackColor  = PBrand
            )
        )
    }
}

// ── Text field colors ─────────────────────────────────────────────────────────

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = PBrand,
    unfocusedBorderColor    = PGlassBorder,
    focusedLabelColor       = PBrand,
    unfocusedLabelColor     = PTextMuted,
    focusedTextColor        = PTextPrimary,
    unfocusedTextColor      = PTextPrimary,
    cursorColor             = PBrand,
    focusedContainerColor   = PGlass,
    unfocusedContainerColor = PGlass
)
