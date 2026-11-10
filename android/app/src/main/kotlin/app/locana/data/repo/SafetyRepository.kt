package app.locana.data.repo

import app.locana.data.api.EmergencyContactDto
import app.locana.data.api.LocanaApi
import app.locana.data.api.QuickMessageDefDto
import app.locana.data.api.QuickMessageRequest
import app.locana.data.api.QuickMessageResultDto
import app.locana.data.api.SetContactsRequest
import app.locana.data.api.SosRequest
import app.locana.data.api.SosResultDto
import app.locana.data.api.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Emergency contacts, SOS and quick messages. The server decides who is told; the phone only asks. */
@Singleton
class SafetyRepository @Inject constructor(private val api: LocanaApi) {
    suspend fun contacts(): Result<List<EmergencyContactDto>> = apiCall { api.emergencyContacts() }
    suspend fun setContacts(list: List<EmergencyContactDto>): Result<List<EmergencyContactDto>> = apiCall { api.setEmergencyContacts(SetContactsRequest(list)) }
    suspend fun sos(reason: String, lat: Double, lon: Double, accuracyM: Double?): Result<SosResultDto> = apiCall { api.sos(SosRequest(reason, lat, lon, accuracyM)) }
    suspend fun quickMessages(): Result<List<QuickMessageDefDto>> = apiCall { api.quickMessages() }
    suspend fun sendQuickMessage(circleId: String, userId: String, code: String): Result<QuickMessageResultDto> =
        apiCall { api.quickMessage(circleId, userId, QuickMessageRequest(code)) }
}
