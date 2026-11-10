package app.locana.ui.timeline

/** Where on the route the person was at time [t], and how far along it that is, by interpolating between recorded points. */
data class RoutePoint(val lat: Double, val lon: Double, val index: Int, val metresFromStart: Double)

/**
 * [pathAt] holds the time of each point of [path]; for old data without times the route is spread evenly over
 * [startMs, endMs]. Times outside the trip clamp to its two ends.
 */
fun routeAt(path: List<Pair<Double, Double>>, pathAt: List<Long>, startMs: Long, endMs: Long, t: Long): RoutePoint? {
    if (path.isEmpty()) return null
    if (path.size == 1) return RoutePoint(path[0].first, path[0].second, 0, 0.0)
    val times = if (pathAt.size == path.size) pathAt else List(path.size) { i -> startMs + (endMs - startMs) * i / (path.size - 1) }
    val tt = t.coerceIn(times.first(), times.last())
    var i = 1
    while (i < times.size - 1 && times[i] < tt) i++
    val t0 = times[i - 1]
    val t1 = times[i]
    val f = if (t1 > t0) ((tt - t0).toDouble() / (t1 - t0)).coerceIn(0.0, 1.0) else 0.0
    val a = path[i - 1]
    val b = path[i]
    var metres = 0.0
    for (k in 1 until i) metres += distance(path[k - 1], path[k])
    metres += distance(a, b) * f
    return RoutePoint(a.first + (b.first - a.first) * f, a.second + (b.second - a.second) * f, i - 1, metres)
}

private fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>): Double = app.locana.domain.Geo.distanceM(
    app.locana.domain.LatLon(a.first, a.second), app.locana.domain.LatLon(b.first, b.second),
)
