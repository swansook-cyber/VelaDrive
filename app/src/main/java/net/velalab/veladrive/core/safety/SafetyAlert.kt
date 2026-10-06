package net.velalab.veladrive.core.safety

enum class SafetyAlertType {
    SPEED_CAMERA,
    RAILWAY_CROSSING,
    DANGER,
    SCHOOL_ZONE,
    SHARP_CURVE
}

data class SafetyAlert(
    val id: String,
    val type: SafetyAlertType,
    val latitude: Double,
    val longitude: Double,
    val directionDegrees: Double? = null,
    val speedLimitKmh: Int? = null,
    val warningDistanceMeters: Double = 700.0,
    val source: String,
    val confidence: Double = 1.0,
    val label: String? = null
)

data class ActiveSafetyAlert(
    val alert: SafetyAlert,
    val distanceMeters: Double
) {
    fun title(): String =
        when (alert.type) {
            SafetyAlertType.SPEED_CAMERA ->
                alert.speedLimitKmh?.let { "กล้องจับความเร็ว • $it กม./ชม." }
                    ?: "กล้องจับความเร็ว"
            SafetyAlertType.RAILWAY_CROSSING -> "ทางรถไฟข้างหน้า"
            SafetyAlertType.DANGER -> alert.label ?: "จุดอันตรายข้างหน้า"
            SafetyAlertType.SCHOOL_ZONE -> "เขตโรงเรียนข้างหน้า"
            SafetyAlertType.SHARP_CURVE -> "โค้งอันตรายข้างหน้า"
        }
}
