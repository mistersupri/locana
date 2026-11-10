package app.locana.ui.battle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.locana.data.api.AttackResultDto
import app.locana.data.api.BattleStateDto
import app.locana.data.api.OwnedWeaponDto
import app.locana.data.api.ShopDto
import app.locana.data.api.ShopWeaponDto
import app.locana.data.repo.BattleRepository
import app.locana.data.repo.MeRepository
import app.locana.ui.components.ChunkyButton
import app.locana.ui.components.ChunkyStyle
import app.locana.ui.components.EmptyState
import app.locana.ui.components.ErrorState
import app.locana.ui.components.LoadingState
import app.locana.ui.components.LocanaCard
import app.locana.ui.components.UiState
import app.locana.ui.explore.BackScaffold
import app.locana.ui.nav.Routes
import app.locana.ui.theme.Display
import app.locana.ui.theme.LocalTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BattleViewModel @Inject constructor(private val battle: BattleRepository, private val me: MeRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<BattleStateDto>>(UiState.Loading)
    val state: StateFlow<UiState<BattleStateDto>> = _state.asStateFlow()
    private val _weapon = MutableStateFlow<String?>(null)
    /** The weapon chosen for the next attack; null means the strongest one owned. */
    val weapon: StateFlow<String?> = _weapon.asStateFlow()
    private val _log = MutableStateFlow<String?>(null)
    val log: StateFlow<String?> = _log.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _result = MutableStateFlow<AttackResultDto?>(null)
    val result: StateFlow<AttackResultDto?> = _result.asStateFlow()
    private val _hits = MutableStateFlow(0)
    /** Counts attacks, so the monster's shake plays once per hit. */
    val hits: StateFlow<Int> = _hits.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        battle.refresh().fold({ _state.value = UiState.Data(it) }, { _state.value = UiState.Error(it.message ?: "Couldn't load the fight") })
    }

    fun pick(code: String) { _weapon.value = code }

    private fun best(s: BattleStateDto) = s.weapons.maxByOrNull { it.power }?.code

    fun attack() {
        val s = (_state.value as? UiState.Data)?.value ?: return
        val e = s.encounter ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val code = _weapon.value ?: best(s)
            battle.attack(e.id, code).fold(
                { r ->
                    _hits.value += 1
                    _log.value = buildString {
                        append("You hit for ${r.hit.damage}")
                        if (r.hit.crit) append(" (critical!)")
                        r.counter?.let { append(" · ${r.encounter.monster.name} hit you for ${it.damage}") }
                    }
                    if (r.outcome == "CONTINUE") _state.value = UiState.Data(s.copy(encounter = r.encounter, coins = r.coins))
                    else { _state.value = UiState.Data(s.copy(encounter = null, coins = r.coins)); _result.value = r; if (r.outcome == "WON") me.refresh() }
                },
                { _log.value = it.message ?: "That did not work"; load() },
            )
            _busy.value = false
        }
    }

    fun flee() {
        val e = ((_state.value as? UiState.Data)?.value)?.encounter ?: return
        viewModelScope.launch { battle.flee(e.id).onSuccess { load() } }
    }

    fun dismissResult() { _result.value = null }
}

@Composable
private fun Bar(fraction: Float, color: Color, label: String, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(380), label = "bar")
    val t = LocalTokens.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = t.ink2)
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(t.line)) {
            Box(Modifier.fillMaxWidth(f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(color))
        }
    }
}

@Composable
fun BattleScreen(nav: NavController, vm: BattleViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val chosen by vm.weapon.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val hits by vm.hits.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    BackScaffold("Monster", nav) { pad ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, { vm.load() }, Modifier.padding(pad))
            is UiState.Data -> {
                val e = s.value.encounter
                if (e == null) Column(Modifier.padding(pad).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EmptyState("No monster right now", "Monsters step onto the road while you travel, at most one every few minutes. Keep exploring, then come back.", illustration = "compass")
                    ChunkyButton("Weapon shop", { nav.navigate(Routes.SHOP) }, Modifier.fillMaxWidth(), style = ChunkyStyle.Yellow)
                } else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                val shake = remember { Animatable(0f) }
                                LaunchedEffect(hits) {
                                    if (hits > 0) { shake.snapTo(1f); shake.animateTo(0f, spring(0.25f, 500f)) }
                                }
                                Text(
                                    e.monster.emoji, fontSize = 96.sp,
                                    modifier = Modifier.graphicsLayer { translationX = shake.value * 24f; rotationZ = shake.value * 8f }.semantics { contentDescription = e.monster.name },
                                )
                                Text(e.monster.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                                Bar(e.hp.toFloat() / e.maxHp, t.coral, "Monster ${e.hp}/${e.maxHp}", Modifier.fillMaxWidth())
                                Bar(e.playerHp.toFloat() / e.maxPlayerHp, t.mint, "You ${e.playerHp}/${e.maxPlayerHp}", Modifier.fillMaxWidth())
                                Text("Win: +${e.rewardCoins} coins, +${e.rewardXp} XP", style = MaterialTheme.typography.labelLarge, color = t.onAmber)
                                log?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { contentDescription = it }) }
                            }
                        }
                    }
                    item { Text("Weapon", style = MaterialTheme.typography.titleMedium) }
                    items(s.value.weapons.sortedBy { it.power }, key = { it.code }) { w -> WeaponRow(w, selected = (chosen ?: s.value.weapons.maxByOrNull { it.power }?.code) == w.code) { vm.pick(w.code) } }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            ChunkyButton("Attack", { vm.attack() }, Modifier.weight(1f), loading = busy)
                            ChunkyButton("Flee", { vm.flee() }, Modifier.weight(1f), style = ChunkyStyle.Secondary)
                        }
                    }
                    item { ChunkyButton("Weapon shop · ${s.value.coins} coins", { nav.navigate(Routes.SHOP) }, Modifier.fillMaxWidth(), style = ChunkyStyle.Yellow) }
                }
            }
        }
    }
    result?.let { r ->
        AlertDialog(
            onDismissRequest = { vm.dismissResult() },
            title = { Text(if (r.outcome == "WON") "Victory! 🎉" else "You were defeated") },
            text = {
                Text(
                    if (r.outcome == "WON") "+${r.rewards?.coins ?: 0} coins and +${r.rewards?.xp ?: 0} XP." + (r.rewards?.levelUps?.lastOrNull()?.let { " Level ${it.to}!" } ?: "")
                    else "${r.encounter.monster.name} got away. Nothing is lost: there will be another monster on the road. A stronger weapon from the shop helps.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.dismissResult() }) { Text("OK") } },
        )
    }
}

@Composable
private fun WeaponRow(w: OwnedWeaponDto, selected: Boolean, onClick: () -> Unit) {
    val t = LocalTokens.current
    Surface(
        onClick = onClick, shape = RoundedCornerShape(18.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else t.card,
        border = androidx.compose.foundation.BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else t.line),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${w.name}, attack power ${w.power}" + if (selected) ", selected" else "" },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(w.emoji, fontSize = 26.sp)
            Text(w.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.padding(start = 12.dp).weight(1f))
            Text("Power ${w.power}", style = MaterialTheme.typography.labelLarge, color = t.ink2)
        }
    }
}

@HiltViewModel
class ShopViewModel @Inject constructor(private val battle: BattleRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ShopDto>>(UiState.Loading)
    val state: StateFlow<UiState<ShopDto>> = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _buying = MutableStateFlow<String?>(null)
    val buying: StateFlow<String?> = _buying.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        battle.shop().fold({ _state.value = UiState.Data(it) }, { _state.value = UiState.Error(it.message ?: "Couldn't open the shop") })
    }

    fun buy(w: ShopWeaponDto) {
        viewModelScope.launch {
            _buying.value = w.code
            battle.buy(w.code).fold({ _state.value = UiState.Data(it); _message.value = "You bought the ${w.name}!" }, { _message.value = it.message ?: "Couldn't buy it" })
            _buying.value = null
        }
    }

    fun messageShown() { _message.value = null }
}

@Composable
fun ShopScreen(nav: NavController, vm: ShopViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val buying by vm.buying.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    BackScaffold("Weapon shop", nav) { pad ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, { vm.load() }, Modifier.padding(pad))
            is UiState.Data -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("💰 ${s.value.coins} coins", style = MaterialTheme.typography.headlineSmall)
                            Text("Coins come from winning fights and unlocking landmarks. Level ${s.value.level}: stronger weapons open as you level up.", style = MaterialTheme.typography.bodySmall, color = t.ink2)
                        }
                    }
                }
                message?.let { m -> item { LaunchedEffect(m) { kotlinx.coroutines.delay(2_500); vm.messageShown() }; Text(m, style = MaterialTheme.typography.labelLarge, color = t.onMint) } }
                items(s.value.weapons, key = { it.code }) { w ->
                    LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadow = 2.dp) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(w.emoji, fontSize = 30.sp)
                            Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(w.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                                Text("Power ${w.power}" + if (w.minLevel > 1) " · level ${w.minLevel}" else "", style = MaterialTheme.typography.bodySmall, color = t.ink2)
                            }
                            when {
                                w.costCoins == 0 -> Text("Always yours", style = MaterialTheme.typography.labelMedium, color = t.ink2)
                                w.owned -> Text("Owned", style = MaterialTheme.typography.labelLarge, color = t.onMint)
                                !w.unlocked -> Text("Level ${w.minLevel}", style = MaterialTheme.typography.labelLarge, color = t.ink3)
                                else -> ChunkyButton("💰 ${w.costCoins}", { vm.buy(w) }, compact = true, enabled = w.affordable, loading = buying == w.code)
                            }
                        }
                    }
                }
            }
        }
    }
}
