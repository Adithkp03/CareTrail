package com.caretrail.app.data

data class LangOption(val code: String, val label: String)

object Langs {
    val all = listOf(
        LangOption("en", "English"),
        LangOption("ml", "മലയാളം"),
        LangOption("hi", "हिन्दी"),
        LangOption("ta", "தமிழ்"),
        LangOption("te", "తెలుగు"),
        LangOption("kn", "ಕನ್ನಡ"),
        LangOption("bn", "বাংলা"),
        LangOption("mr", "मराठी"),
    )
    /** Languages whose clinical text was reviewed by hand; others carry the translation note. */
    val reviewed = setOf("en", "ml", "hi")

    fun valid(code: String?) = all.any { it.code == code }

    /** Static UI label (port of t() in the web app). Falls back to English, then to the key. */
    fun label(key: String, lang: String): String {
        val m = I18nData.labels[key] ?: return key
        return m[lang] ?: m["en"] ?: key
    }

    fun care(text: Map<String, String>, lang: String): String = text[lang] ?: text["en"] ?: ""

    val testLabels = mapOf(
        "hb" to "Haemoglobin",
        "bp_sys" to "Systolic BP",
        "bp_dia" to "Diastolic BP",
        "glucose_fasting" to "Fasting glucose",
        "glucose_ogtt_1h" to "OGTT 1-hour glucose",
        "glucose_ogtt_2h" to "OGTT 2-hour glucose",
    )
    fun testLabel(code: String): String = testLabels[code] ?: code.replace('_', ' ')
}
