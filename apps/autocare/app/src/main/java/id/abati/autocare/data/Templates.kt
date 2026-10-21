package id.abati.autocare.data

/**
 * Katalog bawaan pekerjaan/komponen. ID stabil (bukan UUID acak) supaya restore gabung
 * antar-HP tidak menggandakan katalog. Interval hanyalah titik awal yang umum dan
 * SELALU bisa diubah — ikuti buku manual pabrikan dan kondisi kendaraan.
 */
object Templates {
    private class Cat(val id: String, val name: String, val items: List<MaintenanceItem>)

    private fun item(cat: String, id: String, name: String, km: Long? = null, months: Int? = null, component: Boolean = false, order: Int) =
        MaintenanceItem(id = id, categoryId = cat, name = name, intervalKm = km, intervalMonths = months, isComponent = component, builtIn = true, sortOrder = order)

    private val catalog: List<Cat> = listOf(
        Cat(
            "cat-oil", "Oli dan filter",
            listOf(
                item("cat-oil", "item-engine-oil", "Oli mesin", 5_000, 6, order = 1),
                item("cat-oil", "item-oil-filter", "Filter oli", 10_000, 12, component = true, order = 2),
                item("cat-oil", "item-air-filter", "Filter udara", 20_000, 12, component = true, order = 3),
                item("cat-oil", "item-cabin-filter", "Filter kabin/AC", 15_000, 12, component = true, order = 4),
                item("cat-oil", "item-fuel-filter", "Filter bahan bakar", 40_000, 24, component = true, order = 5),
            ),
        ),
        Cat(
            "cat-tire", "Ban dan kaki-kaki",
            listOf(
                item("cat-tire", "item-tire-rotation", "Rotasi ban", 10_000, 6, order = 1),
                item("cat-tire", "item-tire", "Ban", 40_000, 48, component = true, order = 2),
                item("cat-tire", "item-alignment", "Spooring", 10_000, 6, order = 3),
                item("cat-tire", "item-balancing", "Balancing", 10_000, 6, order = 4),
                item("cat-tire", "item-shock", "Shock absorber", 60_000, null, component = true, order = 5),
                item("cat-tire", "item-ball-joint", "Ball joint", component = true, order = 6),
                item("cat-tire", "item-bushing", "Bushing", component = true, order = 7),
                item("cat-tire", "item-suspension-check", "Pemeriksaan kaki-kaki", 20_000, 12, order = 8),
            ),
        ),
        Cat(
            "cat-electric", "Kelistrikan",
            listOf(
                item("cat-electric", "item-battery", "Aki", null, 24, component = true, order = 1),
                item("cat-electric", "item-alternator", "Alternator", component = true, order = 2),
                item("cat-electric", "item-lamp", "Lampu", component = true, order = 3),
                item("cat-electric", "item-fuse", "Sekring", order = 4),
                item("cat-electric", "item-sensor", "Sensor", component = true, order = 5),
                item("cat-electric", "item-starter", "Sistem starter", component = true, order = 6),
            ),
        ),
        Cat(
            "cat-engine", "Mesin dan pendinginan",
            listOf(
                item("cat-engine", "item-coolant", "Coolant/air radiator", 40_000, 24, order = 1),
                item("cat-engine", "item-radiator", "Servis radiator", 40_000, 24, order = 2),
                item("cat-engine", "item-hose", "Selang radiator", component = true, order = 3),
                item("cat-engine", "item-spark-plug", "Busi", 20_000, 24, component = true, order = 4),
                item("cat-engine", "item-timing-belt", "Timing belt/chain", 100_000, 60, component = true, order = 5),
                item("cat-engine", "item-leak-check", "Pemeriksaan kebocoran", 10_000, 6, order = 6),
                item("cat-engine", "item-throttle", "Bersihkan throttle body", 20_000, 12, order = 7),
            ),
        ),
        Cat(
            "cat-brake", "Rem dan keselamatan",
            listOf(
                item("cat-brake", "item-brake-pad", "Kampas rem", 20_000, 12, component = true, order = 1),
                item("cat-brake", "item-brake-disc", "Cakram rem", component = true, order = 2),
                item("cat-brake", "item-brake-fluid", "Minyak rem", 40_000, 24, order = 3),
                item("cat-brake", "item-brake-service", "Servis rem", 10_000, 6, order = 4),
                item("cat-brake", "item-wiper", "Karet wiper", null, 12, component = true, order = 5),
                item("cat-brake", "item-seatbelt", "Sabuk pengaman", null, 12, order = 6),
            ),
        ),
        Cat(
            "cat-transmission", "Transmisi",
            listOf(
                item("cat-transmission", "item-transmission-oil", "Oli transmisi", 40_000, 24, order = 1),
                item("cat-transmission", "item-differential-oil", "Oli gardan", 40_000, 24, order = 2),
                item("cat-transmission", "item-clutch", "Kopling", component = true, order = 3),
            ),
        ),
        Cat(
            "cat-ac", "AC",
            listOf(
                item("cat-ac", "item-ac-service", "Servis AC", 20_000, 12, order = 1),
                item("cat-ac", "item-ac-compressor", "Kompresor AC", component = true, order = 2),
                item("cat-ac", "item-ac-freon", "Isi freon", order = 3),
            ),
        ),
        Cat(
            "cat-body", "Bodi dan interior",
            listOf(
                item("cat-body", "item-body-repair", "Perbaikan bodi/cat", order = 1),
                item("cat-body", "item-window-film", "Kaca film", component = true, order = 2),
                item("cat-body", "item-detailing", "Cuci & detailing", null, 3, order = 3),
            ),
        ),
        Cat(
            "cat-mod", "Modifikasi dan aksesori",
            listOf(
                item("cat-mod", "item-head-unit", "Head unit/audio", component = true, order = 1),
                item("cat-mod", "item-dashcam", "Dashcam", component = true, order = 2),
                item("cat-mod", "item-accessory", "Aksesori lain", component = true, order = 3),
            ),
        ),
        Cat(
            "cat-admin", "Administrasi",
            listOf(
                item("cat-admin", "item-tax", "Pajak tahunan STNK", null, 12, order = 1),
                item("cat-admin", "item-plate", "Ganti plat & STNK 5 tahunan", null, 60, order = 2),
                item("cat-admin", "item-insurance", "Perpanjang asuransi", null, 12, order = 3),
            ),
        ),
    )

    val categories: List<MaintenanceCategory> =
        catalog.mapIndexed { i, c -> MaintenanceCategory(id = c.id, name = c.name, builtIn = true, sortOrder = i + 1) }

    val items: List<MaintenanceItem> = catalog.flatMap { it.items }

    /** Daftar periksa inspeksi mandiri (PRD: ban, lampu, rem, cairan, wiper, kebocoran). */
    val inspectionChecklist: List<String> = listOf(
        "Tekanan & kondisi tapak ban",
        "Ban cadangan",
        "Lampu utama, sein & rem",
        "Rem & rem tangan",
        "Level oli mesin",
        "Level coolant/air radiator",
        "Level minyak rem",
        "Air wiper & karet wiper",
        "Aki & terminal",
        "Kebocoran oli/air di bawah mobil",
        "Klakson",
        "AC & blower",
        "Sabuk pengaman",
        "Kelengkapan (dongkrak, segitiga, APAR)",
    )
}
