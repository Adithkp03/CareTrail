package com.caretrail.app.ui

/** Navigation callbacks handed to screens so they stay decoupled from NavController. */
class Nav(
    val home: () -> Unit,
    val timeline: () -> Unit,
    val reminders: () -> Unit,
    val movements: () -> Unit,
    val care: () -> Unit,
    val prep: () -> Unit,
    val doctor: () -> Unit,
    val account: () -> Unit,
    val dataSafety: () -> Unit,
    val intake: () -> Unit,
    val registration: () -> Unit,
    val rh: () -> Unit,
    val danger: () -> Unit,
    val birth: () -> Unit,
    val growth: () -> Unit,
    val vaccines: () -> Unit,
    val vault: () -> Unit,
    val facilities: () -> Unit,
    val newJourney: () -> Unit,
    val back: () -> Unit,
    val milestone: (String) -> Unit,
    val upload: (String?) -> Unit,
)

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val NEW_JOURNEY = "newjourney"
    const val HOME = "home"
    const val TIMELINE = "timeline"
    const val REMINDERS = "reminders"
    const val MOVEMENTS = "movements"
    const val CARE = "care"
    const val PREP = "prep"
    const val DOCTOR = "doctor"
    const val ACCOUNT = "account"
    const val DATA_SAFETY = "datasafety"
    const val INTAKE = "intake"
    const val REGISTRATION = "registration"
    const val RH = "rh"
    const val DANGER = "danger"
    const val BIRTH = "birth"
    const val GROWTH = "growth"
    const val VACCINES = "vaccines"
    const val VAULT = "vault"
    const val FACILITIES = "facilities"
    const val MILESTONE = "milestone/{id}"
    const val UPLOAD = "upload?milestone={milestone}"
    val TABS = listOf(HOME, TIMELINE, REMINDERS, MOVEMENTS)
}
