package com.example.data

import java.text.NumberFormat
import java.util.Locale

data class PriceTag(
    val sku: String,
    val title: String,
    val subtitle: String,
    val inrAmount: Int,
    val usdFallback: String,
    val bonusCoins: Int = 0,
    val bonusGems: Int = 0
) {
    fun formattedPrice(locale: Locale = Locale.getDefault()): String {
        return if (locale.country.equals("IN", ignoreCase = true) || locale.language.equals("hi", ignoreCase = true)) {
            "₹$inrAmount"
        } else {
            // Default to India-first ₹ display with USD subtitle per Part J3
            "₹$inrAmount ($usdFallback)"
        }
    }
}

enum class ShopItemCategory {
    BUNDLES,
    KARTS_AND_SKINS,
    TRAILS_AND_HORNS,
    CURRENCY
}

data class CosmeticOffer(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: ShopItemCategory,
    val coinPrice: Int = 0,
    val gemPrice: Int = 0,
    val unlocksBodyId: Int? = null,
    val unlocksLiveryIndex: Int? = null,
    val accentHex: Long = 0xFFFFB703L
)

data class DeskPassTier(
    val tier: Int,
    val requiredXp: Int,
    val freeRewardTitle: String,
    val freeCoins: Int = 0,
    val freeGems: Int = 0,
    val premiumRewardTitle: String,
    val premiumCoins: Int = 0,
    val premiumGems: Int = 0,
    val premiumCosmeticId: String? = null
)

interface BillingGateway {
    val isLiveBillingAvailable: Boolean
    fun queryProducts(): List<PriceTag>
    fun purchaseProduct(sku: String, prefs: Prefs, onResult: (Boolean, String) -> Unit)
    fun restorePurchases(prefs: Prefs, onResult: (Boolean, String) -> Unit)
}

class FakeBillingGateway : BillingGateway {
    override val isLiveBillingAvailable: Boolean = false

    override fun queryProducts(): List<PriceTag> = ShopCatalog.iapOffers

    override fun purchaseProduct(sku: String, prefs: Prefs, onResult: (Boolean, String) -> Unit) {
        when (sku) {
            ShopCatalog.SKU_STARTER_PACK -> {
                prefs.grantEntitlement(starterPack = true, bonusCoins = 1500, bonusGems = 80)
                prefs.unlockBody(5) // Classic Toy + Golden Ruler Livery
                prefs.unlockCosmetic("skin_golden_ruler")
                onResult(true, "Starter Pack unlocked! +1,500 Coins, +80 Gems & Classic Toy Kart!")
            }
            ShopCatalog.SKU_PRO_UPGRADE -> {
                prefs.grantEntitlement(proUnlocked = true, bonusCoins = 3000, bonusGems = 200)
                for (id in 0..5) prefs.unlockBody(id)
                onResult(true, "Desk Racer PRO unlocked! All karts, all cups & Host-Shares-the-Party active!")
            }
            ShopCatalog.SKU_TRACK_PACK -> {
                prefs.grantEntitlement(trackPackUnlocked = true, bonusCoins = 500)
                onResult(true, "Championship Track & Cup Pack unlocked for you and your LAN lobby!")
            }
            ShopCatalog.SKU_DESK_PASS_PREMIUM -> {
                prefs.grantEntitlement(deskPassPremium = true, bonusGems = 50)
                onResult(true, "Desk Pass Season 1 Premium Track activated!")
            }
            ShopCatalog.SKU_GEMS_POCKET -> {
                prefs.addGems(120)
                onResult(true, "+120 Gems added to your offline wallet!")
            }
            ShopCatalog.SKU_GEMS_CHEST -> {
                prefs.addGems(500)
                prefs.addCoins(2000)
                onResult(true, "+500 Gems & +2,000 Bonus Coins added!")
            }
            else -> onResult(false, "Unknown product SKU: $sku")
        }
    }

    override fun restorePurchases(prefs: Prefs, onResult: (Boolean, String) -> Unit) {
        val state = prefs.state.value
        val activeCount = listOf(
            state.starterPackOwned,
            state.proUnlocked,
            state.trackPackUnlocked,
            state.deskPassPremium
        ).count { it }
        onResult(
            true,
            if (activeCount > 0) "Restored $activeCount offline entitlement(s) from signed local vault."
            else "No previous purchases found in offline vault."
        )
    }
}

interface AdGateway {
    fun showRewardedAd(
        placement: String,
        onRewardEarned: () -> Unit,
        onDismissed: (String) -> Unit
    )
}

class FakeAdGateway : AdGateway {
    override fun showRewardedAd(
        placement: String,
        onRewardEarned: () -> Unit,
        onDismissed: (String) -> Unit
    ) {
        onRewardEarned()
        onDismissed("Simulated 5s Offline Sponsor Clip ($placement) — 2× Reward Granted!")
    }
}

object ShopCatalog {
    const val SKU_STARTER_PACK = "desk_racer_starter_29"
    const val SKU_TRACK_PACK = "desk_racer_tracks_79"
    const val SKU_DESK_PASS_PREMIUM = "desk_racer_pass_99"
    const val SKU_PRO_UPGRADE = "desk_racer_pro_149"
    const val SKU_GEMS_POCKET = "desk_racer_gems_49"
    const val SKU_GEMS_CHEST = "desk_racer_gems_199"

    val iapOffers: List<PriceTag> = listOf(
        PriceTag(
            sku = SKU_STARTER_PACK,
            title = "STARTER BUNDLE",
            subtitle = "Classic Toy Kart + Golden Ruler Livery + 1,500 Coins + 80 Gems",
            inrAmount = 29,
            usdFallback = "$0.99",
            bonusCoins = 1500,
            bonusGems = 80
        ),
        PriceTag(
            sku = SKU_PRO_UPGRADE,
            title = "DESK RACER PRO (FULL UNLOCK)",
            subtitle = "All 6 Karts, All 18 Liveries, All Cups + Host-Shares-the-Party + 3,000 Coins",
            inrAmount = 149,
            usdFallback = "$2.99",
            bonusCoins = 3000,
            bonusGems = 200
        ),
        PriceTag(
            sku = SKU_DESK_PASS_PREMIUM,
            title = "DESK PASS PREMIUM (SEASON 1)",
            subtitle = "Unlock all 40 Premium Season Tiers + Neon Highlighter Trail + 50 Gems",
            inrAmount = 99,
            usdFallback = "$1.99",
            bonusGems = 50
        ),
        PriceTag(
            sku = SKU_TRACK_PACK,
            title = "MIDNIGHT STUDIO CUP PACK",
            subtitle = "Unlock Bonus Mirror / Reverse Championship Rules + 500 Coins",
            inrAmount = 79,
            usdFallback = "$1.49",
            bonusCoins = 500
        ),
        PriceTag(
            sku = SKU_GEMS_POCKET,
            title = "PENCIL POUCH OF GEMS",
            subtitle = "+120 Shiny Gems for instant Livery & Horn unlocks",
            inrAmount = 49,
            usdFallback = "$0.99",
            bonusGems = 120
        ),
        PriceTag(
            sku = SKU_GEMS_CHEST,
            title = "ARCHITECT'S TREASURY",
            subtitle = "+500 Shiny Gems + 2,000 Desk Coins",
            inrAmount = 199,
            usdFallback = "$3.99",
            bonusCoins = 2000,
            bonusGems = 500
        )
    )

    val cosmeticOffers: List<CosmeticOffer> = listOf(
        CosmeticOffer(
            id = "body_classic_toy",
            title = "CLASSIC TOY KART",
            subtitle = "6th Chassis • Balanced vintage wind-up desk racer",
            category = ShopItemCategory.KARTS_AND_SKINS,
            coinPrice = 600,
            gemPrice = 40,
            unlocksBodyId = 5,
            accentHex = 0xFFFFB703L
        ),
        CosmeticOffer(
            id = "skin_cyber_mint",
            title = "CYBER MINT LIVERY PACK",
            subtitle = "Unlocks Livery #2 (Alt Spec) across all karts",
            category = ShopItemCategory.KARTS_AND_SKINS,
            coinPrice = 400,
            gemPrice = 25,
            unlocksLiveryIndex = 1,
            accentHex = 0xFF06D6A0L
        ),
        CosmeticOffer(
            id = "skin_royal_violet",
            title = "HYPER VIOLET LIVERY PACK",
            subtitle = "Unlocks Livery #3 (Championship Edition) across all karts",
            category = ShopItemCategory.KARTS_AND_SKINS,
            coinPrice = 750,
            gemPrice = 45,
            unlocksLiveryIndex = 2,
            accentHex = 0xFF8338ECL
        ),
        CosmeticOffer(
            id = "trail_graphite_sparks",
            title = "RAINBOW PENCIL DRIFT SPARKS",
            subtitle = "Prismatic stationery particles on Tier-3 Purple Mini-Turbos",
            category = ShopItemCategory.TRAILS_AND_HORNS,
            coinPrice = 500,
            gemPrice = 30,
            accentHex = 0xFF3A86FFL
        ),
        CosmeticOffer(
            id = "horn_desk_bell",
            title = "RECEPTION DESK BELL HORN",
            subtitle = "Crisp brass ring when firing items or crossing the finish",
            category = ShopItemCategory.TRAILS_AND_HORNS,
            coinPrice = 350,
            gemPrice = 20,
            accentHex = 0xFFFF7A00L
        ),
        CosmeticOffer(
            id = "wheel_eraser_slicks",
            title = "PINK ERASER SLICK TYRES",
            subtitle = "High-contrast studio rubber compound visual trim",
            category = ShopItemCategory.TRAILS_AND_HORNS,
            coinPrice = 450,
            gemPrice = 25,
            accentHex = 0xFFEF476FL
        )
    )

    val deskPassTiers: List<DeskPassTier> = (1..40).map { tier ->
        val xp = (tier - 1) * 200
        val freeCoins = if (tier % 2 == 1) 75 + (tier * 5) else 0
        val freeGems = if (tier % 5 == 0) 10 else 0
        val freeLabel = when {
            tier == 1 -> "100 Desk Coins"
            tier % 5 == 0 -> "+10 Gems & ${100 + tier * 5} Coins"
            tier % 2 == 1 -> "+$freeCoins Coins"
            else -> "Stationery Sticker #${tier / 2}"
        }
        val premCoins = 120 + tier * 10
        val premGems = if (tier % 4 == 0) 20 else 0
        val premCosmetic = when (tier) {
            5 -> "horn_desk_bell"
            10 -> "skin_cyber_mint"
            20 -> "body_classic_toy"
            30 -> "trail_graphite_sparks"
            40 -> "skin_royal_violet"
            else -> null
        }
        val premLabel = when (tier) {
            5 -> "Reception Bell Horn + $premCoins Coins"
            10 -> "Cyber Mint Livery + $premCoins Coins"
            20 -> "Classic Toy Chassis + 25 Gems"
            30 -> "Rainbow Drift Sparks + $premCoins Coins"
            40 -> "Hyper Violet Championship Livery + 50 Gems"
            else -> if (premGems > 0) "+$premGems Gems & +$premCoins Coins" else "+$premCoins Coins"
        }
        DeskPassTier(
            tier = tier,
            requiredXp = xp,
            freeRewardTitle = freeLabel,
            freeCoins = if (tier == 1) 100 else freeCoins,
            freeGems = freeGems,
            premiumRewardTitle = premLabel,
            premiumCoins = premCoins,
            premiumGems = when (tier) {
                20 -> 25
                40 -> 50
                else -> premGems
            },
            premiumCosmeticId = premCosmetic
        )
    }

    /**
     * Part J2: "Host-Shares-the-Party" rule — if the LAN Host owns Pro or Track Pack,
     * every friend connected to their Wi-Fi hotspot lobby gets full access for the session.
     */
    fun canAccessProFeaturesInSession(
        localProfile: UserProfileAndSettings,
        isMultiplayerClient: Boolean,
        hostHasProEntitlement: Boolean = true
    ): Boolean {
        return localProfile.proUnlocked ||
            localProfile.trackPackUnlocked ||
            (isMultiplayerClient && hostHasProEntitlement)
    }

    fun formatCount(value: Int): String =
        NumberFormat.getIntegerInstance(Locale.US).format(value)
}
