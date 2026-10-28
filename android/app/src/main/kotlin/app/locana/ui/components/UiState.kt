package app.locana.ui.components

/** Every data screen renders one of these. Cached data carries its age so offline is honest. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
    data class Data<T>(val value: T, val cachedAtMs: Long? = null) : UiState<T>
}

val <T> UiState<T>.valueOrNull: T? get() = (this as? UiState.Data<T>)?.value
