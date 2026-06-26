package com.kayser.areascan.core.model

/**
 * Belirtilen grid alanı ve adım aralığına göre sıralı durak (waypoint) listesi üretir.
 *
 * Desen, GPR/manyetometre tarama cihazlarında yaygın olan "yılankavi" (boustrophedon)
 * desenidir: ilk satır soldan sağa, ikinci satır sağdan sola, vb. — bu sayede kullanıcı
 * her satır sonunda 180° dönüp bir adım ileri gitmek yerine, doğal bir U-dönüşü yapar.
 */
object SteppedScanPathPlanner {

    /**
     * @return sırayla ziyaret edilecek (worldX, worldZ) çiftleri.
     * Grid genişliği/yüksekliği adım aralığına tam bölünmüyorsa, son durak sınıra
     * en yakın noktada olacak şekilde yuvarlanır (taşma olmaz).
     */
    fun buildWaypoints(grid: GridConfig, stepIntervalMeters: Float): List<Waypoint> {
        if (stepIntervalMeters <= 0f) return emptyList()

        val columns = (grid.widthMeters / stepIntervalMeters).toInt() + 1
        val rows = (grid.heightMeters / stepIntervalMeters).toInt() + 1

        val waypoints = mutableListOf<Waypoint>()
        var index = 0
        for (row in 0 until rows) {
            val z = (row * stepIntervalMeters).coerceAtMost(grid.heightMeters)
            val columnRange = if (row % 2 == 0) 0 until columns else (columns - 1) downTo 0
            for (col in columnRange) {
                val x = (col * stepIntervalMeters).coerceAtMost(grid.widthMeters)
                waypoints.add(Waypoint(index = index, worldX = x, worldZ = z))
                index++
            }
        }
        return waypoints
    }
}

/** Adımlı tarama akışındaki tek bir durak noktası. */
data class Waypoint(
    val index: Int,
    val worldX: Float,
    val worldZ: Float
)
