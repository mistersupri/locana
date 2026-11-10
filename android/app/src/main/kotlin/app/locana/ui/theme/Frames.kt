package app.locana.ui.theme

/**
 * Pin and avatar frames from the collection: the colours of the ring drawn around a person. Each frame is a list
 * of ARGB stops swept around the ring, so one frame is a flat colour, another a gradient. Unknown keys fall back to
 * the plain reward ring. [glows] frames also get a soft halo of their first colour.
 */
object FrameStyles {
    private val styles: Map<String, List<Int>> = mapOf(
        "frame_trailhead" to listOf(0xFF2E7D4F.toInt(), 0xFF7BD389.toInt(), 0xFF2E7D4F.toInt()),
        "frame_summit" to listOf(0xFFFFC93C.toInt(), 0xFFFFF1C9.toInt(), 0xFFFFC93C.toInt()),
        "frame_neon" to listOf(0xFF00E5FF.toInt(), 0xFFFF2BD6.toInt(), 0xFF00E5FF.toInt()),
        "frame_sakura" to listOf(0xFFFFB7C5.toInt(), 0xFFFF7AA2.toInt(), 0xFFFFE0E8.toInt(), 0xFFFFB7C5.toInt()),
        "frame_ice" to listOf(0xFFBDEBFF.toInt(), 0xFF5AB8FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFBDEBFF.toInt()),
        "frame_flame" to listOf(0xFFFF3D00.toInt(), 0xFFFFB300.toInt(), 0xFFFF3D00.toInt()),
        "frame_aurora" to listOf(0xFF22C489.toInt(), 0xFF45AFFF.toInt(), 0xFF9B6BFF.toInt(), 0xFF22C489.toInt()),
        "frame_gold" to listOf(0xFFFFD54A.toInt(), 0xFFB8860B.toInt(), 0xFFFFF3B0.toInt(), 0xFFFFD54A.toInt()),
    )
    private val glowing = setOf("frame_neon", "frame_flame", "frame_aurora", "frame_gold", "frame_ice")

    fun colors(key: String?): List<Int>? = key?.let { styles[it] }
    fun glows(key: String?): Boolean = key in glowing
    val all: List<String> get() = styles.keys.toList()
}
