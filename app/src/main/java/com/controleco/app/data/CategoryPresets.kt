package com.controleco.app.data

/** Approved supplemental categories, each with its own 24 × 24 line icon. */
object CategoryPresets {
    val pinnedExpenseNames = listOf("餐饮", "购物", "日常")
    data class Preset(val name: String, val glyphName: String, val path: String)
    private val iconPaths = linkedMapOf(
    "交通" to "M5 16H3V9l2-5h14l2 5v7h-2M5 16h14M5 16v3M19 16v3M3 10h18M7 13h.01M17 13h.01",
    "医疗" to "M9 3h6v6h6v6h-6v6H9v-6H3V9h6Z",
    "住房" to "M3 11 12 3l9 8M5 10v11h14V10M10 21v-7h4v7",
    "通讯" to "M7 2h10a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1ZM10 5h4M11 19h2",
    "学习" to "M12 5v16M3 4c4-1 6-1 9 1 3-2 5-2 9-1v15c-4-1-6-1-9 2-3-3-5-3-9-2ZM6 8h3M15 8h3M6 12h3M15 12h3",
    "娱乐" to "M7 6h10c3 0 4 3 5 10 0 3-3 4-5 1l-2-2H9l-2 2c-2 3-5 2-5-1 1-7 2-10 5-10ZM6 9v5M3.5 11.5h5M16 10h.01M19 13h.01",
    "旅行" to "m22 2-7 20-4-9-9-4ZM11 13 22 2",
    "运动" to "M2 9v6M5 6v12M8 8v8M8 12h8M16 8v8M19 6v12M22 9v6",
    "人情" to "M3 8h18v4H3ZM5 12v9h14v-9M12 8v13M12 8H8a3 3 0 1 1 3-3ZM12 8h4a3 3 0 1 0-3-3Z",
    "宠物" to "M8 13c2-3 6-3 8 0l3 4c1 4-2 5-5 3l-2-1-2 1c-3 2-6 1-5-3ZM5 5a2 3 0 1 0 0 6a2 3 0 1 0 0-6M10 2a2 3 0 1 0 0 6a2 3 0 1 0 0-6M16 3a2 3 0 1 0 0 6a2 3 0 1 0 0-6M21 7a2 3 0 1 0 0 6a2 3 0 1 0 0-6",
    "借款" to "M2 13h4v8H2ZM6 15l4-3h4c3 0 3 4 0 4h-3M6 19l7 2 9-7c1-2-1-4-3-2l-3 2M12 2a3 3 0 1 0 0 6a3 3 0 1 0 0-6",
    "还款" to "M4 7a8 8 0 1 1-1 9M4 2v5h5M10 11h8v6h-8ZM13 14h2",
    "保险" to "M12 2 3 6v6c0 6 9 10 9 10s9-4 9-10V6ZM8 12l3 3 5-6",
    "育儿" to "M3 11h17c0 6-4 8-8 8s-8-2-8-8ZM12 11V3c5 0 8 3 8 8M3 11 1 6M6 21h.01M18 21h.01",
    "公益" to "M12 21 3 12C-2 5 6-1 12 6 18-1 26 5 21 12Z",
    "其他" to "M4 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4M20 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4",
)
    private val glyphNames = listOf("PRESET_TRANSPORT", "PRESET_MEDICAL", "PRESET_HOUSING", "PRESET_PHONE", "PRESET_STUDY", "PRESET_ENTERTAINMENT", "PRESET_TRAVEL", "PRESET_SPORT", "PRESET_GIFT", "PRESET_PET", "PRESET_BORROW", "PRESET_REPAY", "PRESET_INSURANCE", "PRESET_BABY", "PRESET_CHARITY", "PRESET_OTHER")
    val all: List<Preset> = iconPaths.entries.mapIndexed { index, entry -> Preset(entry.key, glyphNames[index], entry.value) }
    fun forName(name: String): Preset? = all.firstOrNull { it.name == name.trim() }
    fun pathForGlyph(glyphName: String): String? = all.firstOrNull { it.glyphName == glyphName }?.path
}
