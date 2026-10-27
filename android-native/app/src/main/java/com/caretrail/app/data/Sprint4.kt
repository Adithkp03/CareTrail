package com.caretrail.app.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Sprint 4 of the Health-a-thon clinical PRD: postpartum recovery content (3.2),
 * maternal mental health red-flag screening with SOS routing + post-discharge BP /
 * blood-glucose monitoring + high-risk follow-up + 42-day checkup + family
 * planning (3.3), and the mental wellness & mindfulness module (section 4).
 *
 * Same offline-first, sync-ready shape as Sprints 1-3 (updated_at + pending_sync).
 */

@Serializable
data class BpReading(
    val date: String = "",          // ISO date
    val systolic: Int = 0,
    val diastolic: Int = 0,
    val updated_at: String = "",
    val pending_sync: Boolean = true,
)

@Serializable
data class SugarReading(
    val date: String = "",          // ISO date
    val fasting: Int = 0,           // mg/dL, 0 = not recorded
    val post_meal: Int = 0,         // mg/dL, 0 = not recorded
    val updated_at: String = "",
    val pending_sync: Boolean = true,
)

@Serializable
data class MoodCheckin(
    val date: String = "",          // ISO date
    val flags: List<String> = emptyList(), // MentalHealth.SIGNS keys
    val self_harm: Boolean = false,
    val updated_at: String = "",
    val pending_sync: Boolean = true,
)

/** Locally recorded high-risk comorbidity flags for the 2-week follow-up rule. */
@Serializable
data class HighRiskRecord(
    val gest_hypertension: Boolean = false,
    val preeclampsia: Boolean = false,
    val gdm_insulin: Boolean = false,
    val updated_at: String = "",
    val pending_sync: Boolean = true,
) { fun any() = gest_hypertension || preeclampsia || gdm_insulin }

/** Completion flags for the two time-based postpartum follow-ups. */
@Serializable
data class FollowUpDone(
    val two_week_done: String = "",   // ISO date marked done, "" = open
    val checkup42_done: String = "",
    val updated_at: String = "",
    val pending_sync: Boolean = true,
)

/** PRD 3.3: daily home BP log to catch late-onset postpartum preeclampsia. */
object BpRules {
    enum class Level { NORMAL, RAISED, SEVERE }
    fun level(sys: Int, dia: Int): Level = when {
        sys >= 160 || dia >= 110 -> Level.SEVERE
        sys >= 140 || dia >= 90 -> Level.RAISED
        else -> Level.NORMAL
    }
    /** Any raised/severe reading inside the first 6 weeks is a late-onset preeclampsia warning. */
    fun latePreeclampsiaWarning(readings: List<BpReading>): Boolean =
        readings.any { level(it.systolic, it.diastolic) != Level.NORMAL }
}

/** PRD 3.3 red-flag detector + section 4 mindfulness module constants. */
object MentalHealth {
    // Persistent unprovoked crying, guilt/worthlessness, helplessness, aversion to
    // feeding/holding the baby - plus self-harm ideation handled separately with SOS.
    val SIGNS: List<Pair<String, String>> = listOf(
        "crying_spells" to "Crying spells that keep coming without a clear reason",
        "guilt" to "Strong feelings of guilt or worthlessness",
        "helpless" to "Feeling overwhelmed or helpless most of the day",
        "aversion_baby" to "Reluctance to feed, hold or be near the baby",
        "sleep_appetite" to "Cannot sleep or eat even when the baby lets you rest",
    )
    const val SELF_HARM = "self_harm"

    fun anyFlag(m: MoodCheckin) = m.self_harm || m.flags.isNotEmpty()

    /** Zero-click SOS routing: national helplines, no app account needed. */
    val SOS_LINES: List<Triple<String, String, String>> = listOf(
        Triple("Tele-MANAS (Govt mental health helpline)", "14416", "Free, 24x7, in your language"),
        Triple("KIRAN helpline", "18005990019", "Free psychological support, 24x7"),
        Triple("Emergency", "112", "If you or the baby are in immediate danger"),
    )
}

/** Time-based postpartum follow-ups: 2-week high-risk review and mandatory 42-day checkup. */
object FollowUps {
    enum class State { NOT_APPLICABLE, UPCOMING, DUE, DONE }
    fun daysSince(birthDate: String, today: LocalDate = LocalDate.now()): Long? = try {
        ChronoUnit.DAYS.between(LocalDate.parse(birthDate), today)
    } catch (_: Exception) { null }

    fun twoWeekState(birthDate: String, highRisk: Boolean, done: FollowUpDone, today: LocalDate = LocalDate.now()): State {
        if (!highRisk) return State.NOT_APPLICABLE
        if (done.two_week_done.isNotEmpty()) return State.DONE
        val d = daysSince(birthDate, today) ?: return State.NOT_APPLICABLE
        return if (d >= 14) State.DUE else State.UPCOMING
    }

    fun checkup42State(birthDate: String, done: FollowUpDone, today: LocalDate = LocalDate.now()): State {
        if (done.checkup42_done.isNotEmpty()) return State.DONE
        val d = daysSince(birthDate, today) ?: return State.UPCOMING
        return if (d >= 42) State.DUE else State.UPCOMING
    }

    fun checkup42DueDate(birthDate: String): String? = try {
        LocalDate.parse(birthDate).plusDays(42).toString()
    } catch (_: Exception) { null }
}

/** PRD 3.2 recovery content and 3.3 family-planning module. Static, works offline. */
object RecoveryContent {
    class Section(val title: String, val points: List<String>, val warning: String = "")
    val SECTIONS = listOf(
        Section(
            "Rest & daily personal hygiene",
            listOf(
                "Rest whenever the baby sleeps; your body heals fastest with sleep in the first 6 weeks.",
                "Bathe daily with gentle soap and warm water; keep the perineal area clean and dry.",
                "Wear loose, comfortable cotton clothing and change pads every 4-6 hours.",
                "Warm sitz baths (sitting in shallow warm water) 2-3 times a day ease perineal soreness.",
                "Wash hands before and after handling the baby, pads or any wound.",
            ),
        ),
        Section(
            "Lactation & breast care",
            listOf(
                "Feed on demand, at least 8-12 times in 24 hours; a good latch covers most of the areola, not just the nipple.",
                "If nipples are sore, air-dry after feeds and express a few drops of milk over them; avoid soap on nipples.",
                "A hard, painful, warm area on the breast with fever can be mastitis - keep feeding and see a doctor within a day.",
                "Drink a glass of water every time you feed; keep a bottle within reach of your feeding spot.",
            ),
        ),
        Section(
            "Surgical wound safety (cesarean / episiotomy)",
            listOf(
                "Keep the stitch line clean and dry; pat dry after bathing, never rub.",
                "Watch for spreading redness, pus, foul smell, opening of the wound or fever - any of these means same-day medical review.",
                "Avoid lifting anything heavier than the baby for the first 6 weeks after a cesarean.",
            ),
            warning = "Never apply oils, herbal pastes or any home remedy on stitches or the surgical wound unless your doctor has prescribed it.",
        ),
    )

    class Method(val name: String, val detail: String)
    val FAMILY_PLANNING = listOf(
        "Wait at least 24 months after delivery before the next pregnancy - spacing protects both your recovery and the next baby.",
        "Breastfeeding alone (LAM) prevents pregnancy only if your baby is under 6 months, feeds exclusively day and night, and your periods have not returned.",
    )
    val FP_METHODS = listOf(
        Method("IUCD / PPIUCD (Copper-T)", "Fitted right after delivery or at the 6-week checkup; works up to 10 years, no effect on breastfeeding."),
        Method("Condoms", "Safe from day one, also protect against infections; keep a supply at home."),
        Method("Progestin-only pill (mini pill)", "Safe while breastfeeding; take at the same time every day. Combined pills only after 6 months or when feeding reduces."),
        Method("Injectable (3-monthly)", "A depot injection every 3 months from 6 weeks after delivery; does not affect milk."),
        Method("Permanent methods", "Tubectomy or vasectomy are one-time decisions for couples who have completed their family."),
    )
}

/** Section 4 mindfulness module: daily affirmations + breathing + soothing audio. */
object Mindfulness {
    val AFFIRMATIONS = listOf(
        "My body grew and delivered a baby. That is extraordinary, and I am allowed to heal slowly.",
        "I do not have to do everything today. Feeding, resting and healing are enough.",
        "Asking for help is a sign of a good mother, not a weak one.",
        "My baby needs a healthy me - my rest is part of my baby's care.",
        "Hard days pass. I have already survived every hard day so far.",
        "I am learning my baby and my baby is learning me. We are growing together.",
        "Whatever I feel today is real, and it is okay to say it out loud.",
        "Small steps count. A shower, a meal, a nap - each one is care.",
        "I am more than enough for my baby, exactly as I am today.",
        "My worth is not measured by how much I got done today.",
        "This feeling is a wave. It rises, it peaks, and it passes.",
        "I deserve the same gentleness I give my baby.",
        "One feed, one breath, one hour at a time - I am doing this.",
        "It is okay to cry. Tears are not failure; they are release.",
    )
    fun affirmationOfTheDay(today: LocalDate = LocalDate.now()): String =
        AFFIRMATIONS[Math.floorMod(today.toEpochDay().toInt(), AFFIRMATIONS.size)]

    /** 4-4-6 paced breathing: inhale 4s, hold 4s, exhale 6s. */
    const val BREATH_IN = 4
    const val BREATH_HOLD = 4
    const val BREATH_OUT = 6
}

// ---- local persistence (SharedPreferences JSON, per journey) -------------------

private fun Store.getS4(key: String): String? = getCache("s4:$key")
private fun Store.putS4(key: String, json: String) = putCache("s4:$key", json)

fun Store.bpReadings(journeyId: String): List<BpReading> =
    getS4("bp:$journeyId")?.let { runCatching { AppJson.decodeFromString<List<BpReading>>(it) }.getOrNull() } ?: emptyList()
fun Store.saveBpReadings(journeyId: String, v: List<BpReading>) =
    putS4("bp:$journeyId", AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(BpReading.serializer()), v))

fun Store.sugarReadings(journeyId: String): List<SugarReading> =
    getS4("sugar:$journeyId")?.let { runCatching { AppJson.decodeFromString<List<SugarReading>>(it) }.getOrNull() } ?: emptyList()
fun Store.saveSugarReadings(journeyId: String, v: List<SugarReading>) =
    putS4("sugar:$journeyId", AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(SugarReading.serializer()), v))

fun Store.moodCheckins(journeyId: String): List<MoodCheckin> =
    getS4("mood:$journeyId")?.let { runCatching { AppJson.decodeFromString<List<MoodCheckin>>(it) }.getOrNull() } ?: emptyList()
fun Store.saveMoodCheckins(journeyId: String, v: List<MoodCheckin>) =
    putS4("mood:$journeyId", AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(MoodCheckin.serializer()), v))

fun Store.highRisk(journeyId: String): HighRiskRecord =
    getS4("highrisk:$journeyId")?.let { runCatching { AppJson.decodeFromString<HighRiskRecord>(it) }.getOrNull() } ?: HighRiskRecord()
fun Store.saveHighRisk(journeyId: String, v: HighRiskRecord) =
    putS4("highrisk:$journeyId", AppJson.encodeToString(HighRiskRecord.serializer(), v.copy(updated_at = nowIso())))

fun Store.followUps(journeyId: String): FollowUpDone =
    getS4("followups:$journeyId")?.let { runCatching { AppJson.decodeFromString<FollowUpDone>(it) }.getOrNull() } ?: FollowUpDone()
fun Store.saveFollowUps(journeyId: String, v: FollowUpDone) =
    putS4("followups:$journeyId", AppJson.encodeToString(FollowUpDone.serializer(), v.copy(updated_at = nowIso())))
