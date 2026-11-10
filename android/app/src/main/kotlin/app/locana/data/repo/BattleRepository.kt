package app.locana.data.repo

import app.locana.data.api.AttackRequest
import app.locana.data.api.AttackResultDto
import app.locana.data.api.BattleStateDto
import app.locana.data.api.BuyRequest
import app.locana.data.api.EncounterDto
import app.locana.data.api.LocanaApi
import app.locana.data.api.ShopDto
import app.locana.data.api.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Monsters met on the road, the weapons that fight them and the shop that sells the weapons. The server rolls every hit. */
@Singleton
class BattleRepository @Inject constructor(private val api: LocanaApi) {
    private val _waiting = MutableStateFlow<EncounterDto?>(null)
    /** The monster waiting for the player right now, if any. The map shows a banner for it. */
    val waiting: StateFlow<EncounterDto?> = _waiting.asStateFlow()
    private val _coins = MutableStateFlow(0)
    val coins: StateFlow<Int> = _coins.asStateFlow()

    suspend fun refresh(): Result<BattleStateDto> = apiCall { api.battleActive() }.onSuccess { _waiting.value = it.encounter; _coins.value = it.coins }

    suspend fun attack(id: String, weaponCode: String?): Result<AttackResultDto> =
        apiCall { api.attack(id, AttackRequest(weaponCode)) }.onSuccess { r ->
            _coins.value = r.coins
            _waiting.value = if (r.outcome == "CONTINUE") r.encounter else null
        }

    suspend fun flee(id: String): Result<Unit> = apiCall { api.flee(id) }.map { _waiting.value = null }
    suspend fun shop(): Result<ShopDto> = apiCall { api.shop() }.onSuccess { _coins.value = it.coins }
    suspend fun buy(code: String): Result<ShopDto> = apiCall { api.buy(BuyRequest(code)) }.onSuccess { _coins.value = it.coins }
}
