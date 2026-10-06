package net.velalab.veladrive.core.poi

data class PoiSearchResult(
    val id: String?,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val distanceText: String?,
    val alternateNames: List<String> = emptyList(),
    val category: PoiCategory? = null,
    val phone: String? = null,
    val province: String? = null,
    val district: String? = null,
    val source: PoiSource = PoiSource.LEGACY_UNKNOWN,
    val sourceReference: String? = null,
    val sourceUrl: String? = null,
    val sourceTags: Map<String, String> = emptyMap(),
    val verified: Boolean = false,
    val updatedAt: String? = null,
    val datasetVersion: String? = null
)

enum class PoiSource(val displayName: String) {
    USER_PLACE("บันทึกไว้"),
    VELA_CURATED("Vela POI"),
    OPENSTREETMAP("OpenStreetMap"),
    LONGDO("Longdo"),
    GOOGLE_MAPS("Google Maps"),
    LEGACY_UNKNOWN("บันทึกไว้")
}

enum class PoiCategory(val displayName: String) {
    FUEL("ปั๊มน้ำมัน"),
    HOSPITAL("โรงพยาบาล"),
    RESTAURANT("ร้านอาหาร"),
    HALAL_RESTAURANT("ร้านอาหารฮาลาล"),
    HOTEL("โรงแรม"),
    MARKET("ตลาด"),
    SHOPPING("ศูนย์การค้า"),
    POLICE("ตำรวจ"),
    AIRPORT("สนามบิน"),
    BUS_TERMINAL("สถานีขนส่ง"),
    TOURISM("สถานที่ท่องเที่ยว"),
    CONVENIENCE_STORE("ร้านสะดวกซื้อ"),
    AUTO_SERVICE("บริการรถยนต์"),
    TIRE_SERVICE("บริการยางรถยนต์"),
    BANK("ธนาคาร"),
    ATM("เอทีเอ็ม"),
    EV_CHARGER("สถานีชาร์จรถไฟฟ้า"),
    PIER("ท่าเรือ"),
    FERRY("เรือเฟอร์รี่"),
    MOSQUE("มัสยิด"),
    GOVERNMENT("หน่วยงานราชการ"),
    OTHER("อื่น ๆ")
}
