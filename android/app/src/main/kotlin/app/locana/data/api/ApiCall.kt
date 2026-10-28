package app.locana.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Everything that can go wrong talking to the backend, in terms the UI can act on. */
sealed class AppError(message: String) : Exception(message) {
    /** No connection, timeout, DNS: worth retrying later. */
    class Offline : AppError("You're offline. We'll retry when the connection is back.")
    class Unauthorized(val code: String, message: String) : AppError(message)
    class Server(val status: Int, val code: String, message: String) : AppError(message)
    class Unknown(message: String) : AppError(message)

    val codeOrNull: String? get() = when (this) {
        is Unauthorized -> code
        is Server -> code
        else -> null
    }
}

val ErrorJson = Json { ignoreUnknownKeys = true }

fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is IOException -> AppError.Offline()
    is HttpException -> httpError(code(), response()?.errorBody()?.string())
    else -> AppError.Unknown(message ?: "Something went wrong")
}

fun httpError(status: Int, body: String?): AppError {
    val parsed = body?.let { runCatching { ErrorJson.decodeFromString(ErrorEnvelope.serializer(), it).error }.getOrNull() }
    val code = parsed?.code ?: "HTTP_$status"
    val message = parsed?.message ?: if (status >= 500) "The server had a problem. Try again in a moment." else "Request failed ($status)"
    return if (status == 401) AppError.Unauthorized(code, message) else AppError.Server(status, code, message)
}

/** Runs an API call and converts failures to [AppError], keeping coroutine cancellation intact. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e.toAppError())
}

/** For endpoints that answer 204 with no body. */
suspend fun apiUnit(block: suspend () -> Response<Unit>): Result<Unit> = apiCall {
    val r = block()
    if (!r.isSuccessful) throw httpError(r.code(), r.errorBody()?.string())
}
