package app.locana.data.system

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Which way the phone points, from the compass, as degrees clockwise from true north. GPS bearing
 * only exists while moving; this is what lets a still phone show its direction too.
 */
@Singleton
class HeadingSensor @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val rotation: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val available: Boolean get() = rotation != null

    /** Live heading while collected, in steps of 5 degrees so the map is not redrawn for every wobble. */
    fun live(near: () -> Location?): Flow<Float?> = callbackFlow {
        val sensor = rotation
        val sm = manager
        if (sensor == null || sm == null) { trySend(null); awaitClose { }; return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { trySend(azimuth(e.values, near())?.let { (it / 5f).roundToInt() * 5f % 360f }) }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sm.unregisterListener(listener) }
    }.distinctUntilChanged { a, b -> a != null && b != null && abs(a - b) < 5f }

    /** One reading, for stamping an uploaded fix. Listens for a moment and lets go again, so it costs almost nothing. */
    suspend fun sample(near: Location?): Float? {
        val sensor = rotation ?: return null
        val sm = manager ?: return null
        return withTimeoutOrNull(700) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resumeWith(Result.success(azimuth(e.values, near)))
                    }
                    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }
    }

    private fun azimuth(vector: FloatArray, near: Location?): Float? {
        val r = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(r, vector)
        // Held upright (the usual way to carry a phone) the camera side points ahead; lying flat, the top edge does.
        val upright = abs(r[8]) < 0.7f
        val out = FloatArray(9)
        if (upright) SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, out) else r.copyInto(out)
        val o = FloatArray(3)
        SensorManager.getOrientation(out, o)
        var deg = Math.toDegrees(o[0].toDouble()).toFloat()
        if (near != null) deg += GeomagneticField(near.latitude.toFloat(), near.longitude.toFloat(), near.altitude.toFloat(), System.currentTimeMillis()).declination
        return ((deg % 360f) + 360f) % 360f
    }
}
