package com.caretrail.app.data

/** Read-only official guidance. Never infer eligibility from a patient's records. */
data class SupportResource(val id: String, val title: String, val summary: String, val access: String, val url: String)

object OfficialSupport {
    const val reviewedOn = "23 November 2026"
    val resources = listOf(
        SupportResource("pmmvy", "PMMVY: maternity benefit",
            "Financial support for eligible pregnant and lactating women. Conditions apply; not every pregnancy qualifies.",
            "Ask your nearest Anganwadi or ASHA worker to check eligibility and help with the application. Eligible women can also use the official PMMVY citizen portal. Check the current document list and deadlines with the worker or scheme, not this app.",
            "https://www.wcd.gov.in/offerings/pradhan-mantri-matru-vandana-yojana"),
        SupportResource("jsy", "JSY: institutional-delivery support",
            "Cash assistance linked to institutional delivery. Eligibility and assistance vary by state, category and facility.",
            "Ask your ASHA or the maternity desk at your government health facility whether JSY applies to you, which facility is covered and what documents are needed. CareTrail cannot confirm eligibility or an amount.",
            "https://www.nhm.gov.in/index1.php?lang=1&level=3&lid=309&sublinkid=841"),
        SupportResource("jssk", "JSSK: public-facility maternity and infant care",
            "The official programme describes no-expense maternity services, including delivery and caesarean section, and care for sick newborns and infants in public health institutions.",
            "Ask the government facility or your ASHA about covered medicines, tests, diet and transport, and how to arrange them locally. Confirm the local process with the facility; this app does not book transport or guarantee service availability.",
            "https://www.nhm.gov.in/index1.php?lang=1&level=3&lid=308&sublinkid=842")
    )
}
