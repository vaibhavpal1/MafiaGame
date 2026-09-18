package com.example.mafiagame

/**
 * Visual identity for each role's reveal card: the emoji glyph, flavor
 * copy, and the gradient card background it's shown on. Kept separate from
 * [Role] so game logic never has to know about presentation.
 */
data class RoleCardInfo(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val tagline: String,
    val cardBackgroundRes: Int,
    val accentColorRes: Int
)

object RoleUi {

    private val info = mapOf(
        Role.DON to RoleCardInfo(
            emoji = "🔪",
            title = "YOU ARE THE DON",
            subtitle = "Head of the Mafia Family",
            tagline = "Rule the night. Trust no one.",
            cardBackgroundRes = R.drawable.card_role_don,
            accentColorRes = R.color.don_border
        ),
        Role.MUNNA_BHAI to RoleCardInfo(
            emoji = "💉",
            title = "YOU ARE THE DOCTOR",
            subtitle = "Healer of the Town",
            tagline = "One life to save. Choose wisely.",
            cardBackgroundRes = R.drawable.card_role_doctor,
            accentColorRes = R.color.doctor_border
        ),
        Role.CHULBUL_PANDEY to RoleCardInfo(
            emoji = "🛡️",
            title = "YOU ARE THE COP",
            subtitle = "Guardian of Justice",
            tagline = "Justice never sleeps.",
            cardBackgroundRes = R.drawable.card_role_cop,
            accentColorRes = R.color.cop_border
        ),
        Role.MAJDOOR to RoleCardInfo(
            emoji = "🧑‍🌾",
            title = "YOU ARE A VILLAGER",
            subtitle = "Ordinary Town Folk",
            tagline = "Survive the night. Outsmart the mafia.",
            cardBackgroundRes = R.drawable.card_role_villager,
            accentColorRes = R.color.villager_border
        )
    )

    fun forRoleName(roleName: String): RoleCardInfo =
        info[runCatching { Role.valueOf(roleName) }.getOrDefault(Role.MAJDOOR)]
            ?: info.getValue(Role.MAJDOOR)
}
