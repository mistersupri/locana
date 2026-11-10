package app.locana.ui.safety

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CarCrash
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.locana.data.api.EmergencyContactDto
import app.locana.data.prefs.SettingsStore
import app.locana.data.repo.SafetyRepository
import app.locana.ui.auth.Field
import app.locana.ui.components.ChunkyButton
import app.locana.ui.components.ChunkyStyle
import app.locana.ui.components.ListRowCard
import app.locana.ui.components.LocanaCard
import app.locana.ui.components.LocanaSwitch
import app.locana.ui.components.SectionHeader
import app.locana.ui.explore.BackScaffold
import app.locana.ui.theme.LocalTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SafetyViewModel @Inject constructor(private val safety: SafetyRepository, private val settings: SettingsStore) : ViewModel() {
    private val _contacts = MutableStateFlow<List<EmergencyContactDto>>(emptyList())
    val contacts: StateFlow<List<EmergencyContactDto>> = _contacts.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    val crashDetection = settings.settings.map { it.crashDetection }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val autoCall = settings.settings.map { it.autoCall }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init { viewModelScope.launch { safety.contacts().onSuccess { _contacts.value = it }.onFailure { _error.value = it.message } } }

    private fun save(list: List<EmergencyContactDto>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _saving.value = true
            safety.setContacts(list).fold({ _contacts.value = it; _error.value = null; onDone() }, { _error.value = it.message ?: "Couldn't save" })
            _saving.value = false
        }
    }

    fun add(name: String, phone: String, email: String, onDone: () -> Unit) =
        save(_contacts.value + EmergencyContactDto(name = name.trim(), phone = phone.trim(), email = email.trim().ifEmpty { null }), onDone)

    fun remove(c: EmergencyContactDto) = save(_contacts.value.filter { it != c })
    fun setCrash(v: Boolean) { viewModelScope.launch { settings.setCrashDetection(v) } }
    fun setAutoCall(v: Boolean) { viewModelScope.launch { settings.setAutoCall(v) } }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        LocanaSwitch(checked, null)
    }
}

@Composable
fun SafetyScreen(nav: NavController, vm: SafetyViewModel = hiltViewModel()) {
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val crash by vm.crashDetection.collectAsStateWithLifecycle()
    val auto by vm.autoCall.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    val context = LocalContext.current
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setAutoCall(granted) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    BackScaffold("Safety", nav) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("SOS", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Hold the red SOS button on the map for 1.5 seconds. Everyone in your circles is notified with your position, they and your emergency contacts get an e-mail, and your phone calls the first emergency contact.",
                            style = MaterialTheme.typography.bodyMedium, color = t.ink2,
                        )
                    }
                }
            }
            item { SectionHeader("Emergency contacts") }
            if (contacts.isEmpty()) item { Text("No one yet. Add up to five people to call and e-mail when you send an SOS.", style = MaterialTheme.typography.bodyMedium, color = t.ink2) }
            items(contacts, key = { it.id ?: (it.name + it.phone) }) { c ->
                ListRowCard(
                    title = c.name,
                    subtitle = listOfNotNull(c.phone, c.email).joinToString(" · "),
                    icon = Icons.Outlined.Call, tileColor = t.coralSoft, tileTint = t.coralDark,
                    trailing = { Text("Remove", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = t.coralDark, modifier = Modifier.clickable(role = Role.Button) { vm.remove(c) }.padding(8.dp)) },
                )
            }
            if (contacts.size < 5) item {
                LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Add a contact", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                        Field(name, { name = it.take(60) }, "Name", capitalize = true)
                        Field(phone, { phone = it.take(20) }, "Phone number", keyboard = KeyboardType.Phone)
                        Field(email, { email = it.take(120) }, "E-mail (optional)", keyboard = KeyboardType.Email)
                        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = t.coralDark, modifier = Modifier.padding(top = 8.dp)) }
                        ChunkyButton(
                            "Add contact", { vm.add(name, phone, email) { name = ""; phone = ""; email = "" } },
                            Modifier.fillMaxWidth().padding(top = 14.dp), enabled = name.isNotBlank() && phone.length >= 6, loading = saving,
                        )
                    }
                }
            }
            item { SectionHeader("Crash detection") }
            item {
                LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), shadow = 3.dp) {
                    Column {
                        SwitchRow("Detect crashes", "After a hard impact while you are in a vehicle, Locana asks \"Are you okay?\". With no answer in 15 seconds it sends an SOS.", crash) { vm.setCrash(it) }
                        SwitchRow(
                            "Place the emergency call myself",
                            "Calls your first emergency contact straight away instead of opening the dialer. Needs the phone permission.",
                            auto,
                        ) { want ->
                            if (!want) vm.setAutoCall(false)
                            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) vm.setAutoCall(true)
                            else callPermission.launch(Manifest.permission.CALL_PHONE)
                        }
                    }
                }
            }
            item {
                Text(
                    "Crash detection listens to the motion sensor only while you seem to be driving, and nothing is recorded. It can be wrong: you can always answer \"I'm OK\".",
                    style = MaterialTheme.typography.bodySmall, color = t.ink2, modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}
