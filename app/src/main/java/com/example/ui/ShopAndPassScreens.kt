package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AdGateway
import com.example.data.BillingGateway
import com.example.data.CosmeticOffer
import com.example.data.DeskPassTier
import com.example.data.Prefs
import com.example.data.PriceTag
import com.example.data.ShopCatalog
import com.example.data.UserProfileAndSettings
import com.example.ui.components.DRButton
import com.example.ui.components.DRButtonVariant
import com.example.ui.components.DRCard
import com.example.ui.components.DRChip
import com.example.ui.components.DRTopBar
import com.example.ui.theme.DRColors
import com.example.ui.theme.DRRadius
import com.example.ui.theme.DRType

@Composable
fun ShopScreen(
    profile: UserProfileAndSettings,
    prefs: Prefs,
    billingGateway: BillingGateway,
    adGateway: AdGateway,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Featured & Bundles, 1 = Karts & Cosmetics, 2 = Free Rewards
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DRColors.NightNavy.copy(alpha = 0.94f))
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("shop_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DRTopBar(
                title = "STATIONERY SHOP",
                onBack = onBack,
                backTestTag = "btn_shop_back",
                rightContent = {
                    CurrencyHeaderPills(
                        coins = profile.coinsBalance,
                        gems = profile.gemsBalance,
                        onRestore = {
                            billingGateway.restorePurchases(prefs) { _, msg ->
                                bannerMessage = msg
                            }
                        }
                    )
                }
            )

            // Category Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DRChip(
                    text = "FEATURED & PRO BUNDLES (₹)",
                    selected = selectedTab == 0,
                    accentColor = DRColors.KartGold,
                    onClick = { selectedTab = 0 },
                    testTag = "shop_tab_bundles"
                )
                DRChip(
                    text = "COSMETICS & LIVERIES (COINS)",
                    selected = selectedTab == 1,
                    accentColor = DRColors.ElectricBlue,
                    onClick = { selectedTab = 1 },
                    testTag = "shop_tab_cosmetics"
                )
                DRChip(
                    text = "DAILY GIFT & OPT-IN BONUS",
                    selected = selectedTab == 2,
                    accentColor = DRColors.MintFinish,
                    onClick = { selectedTab = 2 },
                    testTag = "shop_tab_free"
                )

                val msg = bannerMessage
                if (msg != null) {
                    Text(
                        text = msg,
                        style = DRType.Caption,
                        color = DRColors.KartGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(billingGateway.queryProducts()) { offer ->
                                IapBundleCard(
                                    offer = offer,
                                    profile = profile,
                                    onBuy = {
                                        billingGateway.purchaseProduct(offer.sku, prefs) { _, msg ->
                                            bannerMessage = msg
                                        }
                                    }
                                )
                            }
                        }
                    }

                    1 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ShopCatalog.cosmeticOffers) { item ->
                                CosmeticOfferRow(
                                    offer = item,
                                    profile = profile,
                                    onBuyWithCoins = {
                                        if (prefs.spendCoins(item.coinPrice)) {
                                            unlockCosmeticOffer(item, prefs)
                                            bannerMessage = "Unlocked ${item.title} with ${item.coinPrice} Coins!"
                                        } else {
                                            bannerMessage = "Not enough Coins! Race or claim Desk Pass tiers."
                                        }
                                    },
                                    onBuyWithGems = {
                                        if (prefs.spendGems(item.gemPrice)) {
                                            unlockCosmeticOffer(item, prefs)
                                            bannerMessage = "Unlocked ${item.title} with ${item.gemPrice} Gems!"
                                        } else {
                                            bannerMessage = "Not enough Gems!"
                                        }
                                    }
                                )
                            }
                        }
                    }

                    else -> {
                        FreeRewardsPane(
                            prefs = prefs,
                            adGateway = adGateway,
                            onStatusMessage = { bannerMessage = it }
                        )
                    }
                }
            }
        }
    }
}

private fun unlockCosmeticOffer(offer: CosmeticOffer, prefs: Prefs) {
    prefs.unlockCosmetic(offer.id)
    offer.unlocksBodyId?.let { prefs.unlockBody(it) }
    offer.unlocksLiveryIndex?.let { prefs.updateKartLivery(it) }
}

@Composable
private fun IapBundleCard(
    offer: PriceTag,
    profile: UserProfileAndSettings,
    onBuy: () -> Unit
) {
    val alreadyOwned = when (offer.sku) {
        ShopCatalog.SKU_STARTER_PACK -> profile.starterPackOwned
        ShopCatalog.SKU_PRO_UPGRADE -> profile.proUnlocked
        ShopCatalog.SKU_TRACK_PACK -> profile.trackPackUnlocked
        ShopCatalog.SKU_DESK_PASS_PREMIUM -> profile.deskPassPremium
        else -> false
    }

    DRCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (alreadyOwned) DRColors.MintFinish else DRColors.KartGold.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = offer.title,
                    style = DRType.CardTitle,
                    color = DRColors.CrispWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = offer.subtitle,
                    style = DRType.Caption,
                    color = DRColors.MutedSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DRButton(
                text = if (alreadyOwned) "OWNED ✓" else offer.formattedPrice(),
                onClick = { if (!alreadyOwned) onBuy() },
                enabled = !alreadyOwned,
                variant = if (alreadyOwned) DRButtonVariant.SECONDARY else DRButtonVariant.PRIMARY,
                testTag = "btn_buy_${offer.sku}"
            )
        }
    }
}

@Composable
private fun CosmeticOfferRow(
    offer: CosmeticOffer,
    profile: UserProfileAndSettings,
    onBuyWithCoins: () -> Unit,
    onBuyWithGems: () -> Unit
) {
    val owned = profile.unlockedCosmetics.contains(offer.id) ||
        (offer.unlocksBodyId != null && profile.unlockedBodies.contains(offer.unlocksBodyId))

    DRCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (owned) DRColors.MintFinish else Color(offer.accentHex)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = offer.title,
                    style = DRType.CardTitle,
                    color = DRColors.CrispWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = offer.subtitle,
                    style = DRType.Caption,
                    color = DRColors.MutedSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (owned) {
                DRChip(
                    text = "UNLOCKED ✓",
                    selected = true,
                    accentColor = DRColors.MintFinish,
                    onClick = {}
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DRButton(
                        text = "${offer.coinPrice} COINS",
                        onClick = onBuyWithCoins,
                        variant = DRButtonVariant.PRIMARY,
                        testTag = "btn_coin_${offer.id}"
                    )
                    DRButton(
                        text = "${offer.gemPrice} GEMS",
                        onClick = onBuyWithGems,
                        variant = DRButtonVariant.SECONDARY,
                        testTag = "btn_gem_${offer.id}"
                    )
                }
            }
        }
    }
}

@Composable
private fun FreeRewardsPane(
    prefs: Prefs,
    adGateway: AdGateway,
    onStatusMessage: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DRCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            borderColor = DRColors.KartGold
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "OPT-IN SPONSOR CLIP (+150 COINS)",
                        style = DRType.CardTitle,
                        color = DRColors.KartGold
                    )
                    Text(
                        text = "Zero forced interstitials ever. Watch a short opt-in clip only when you want extra Desk Coins.",
                        style = DRType.Body,
                        color = DRColors.MutedSlate
                    )
                }
                DRButton(
                    text = "WATCH CLIP (+150 COINS)",
                    onClick = {
                        adGateway.showRewardedAd(
                            placement = "shop_free_coins",
                            onRewardEarned = { prefs.addCoins(150) },
                            onDismissed = onStatusMessage
                        )
                    },
                    variant = DRButtonVariant.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "btn_shop_rewarded_ad"
                )
            }
        }

        DRCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            borderColor = DRColors.MintFinish
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "HOST-SHARES-THE-PARTY GUARANTEE",
                        style = DRType.CardTitle,
                        color = DRColors.MintFinish
                    )
                    Text(
                        text = "In LAN Wi-Fi Hotspot races, if the Host owns any Track Pack or Pro upgrade, all 2–8 friends in the room play every track together for free.",
                        style = DRType.Body,
                        color = DRColors.MutedSlate
                    )
                }
                DRChip(
                    text = "100% FAIR PLAY • ZERO PAY-TO-WIN",
                    selected = true,
                    accentColor = DRColors.MintFinish,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
fun DeskPassScreen(
    profile: UserProfileAndSettings,
    prefs: Prefs,
    billingGateway: BillingGateway,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var statusText by remember { mutableStateOf<String?>(null) }
    val currentTier = prefs.currentDeskPassTier()
    val progressToNext = ((profile.deskPassXp % 200) / 200f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DRColors.NightNavy.copy(alpha = 0.95f))
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.displayCutout))
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("desk_pass_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            DRTopBar(
                title = "DESK PASS • SEASON 1",
                onBack = onBack,
                backTestTag = "btn_desk_pass_back",
                rightContent = {
                    if (!profile.deskPassPremium) {
                        DRButton(
                            text = "UNLOCK PREMIUM ₹99",
                            onClick = {
                                billingGateway.purchaseProduct(ShopCatalog.SKU_DESK_PASS_PREMIUM, prefs) { _, msg ->
                                    statusText = msg
                                }
                            },
                            variant = DRButtonVariant.PRIMARY,
                            testTag = "btn_buy_desk_pass_premium"
                        )
                    } else {
                        DRChip(
                            text = "PREMIUM PASS ACTIVE ★",
                            selected = true,
                            accentColor = DRColors.KartGold,
                            onClick = {}
                        )
                    }
                }
            )

            // Tier XP Progress Bar + Status Message
            DRCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = DRColors.ElectricBlue.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TIER $currentTier / 40",
                        style = DRType.CardTitle,
                        color = DRColors.KartGold
                    )
                    LinearProgressIndicator(
                        progress = { progressToNext },
                        color = DRColors.ElectricBlue,
                        trackColor = DRColors.StrokeHairline,
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                    )
                    Text(
                        text = statusText ?: "${200 - (profile.deskPassXp % 200)} XP to next tier",
                        style = DRType.Caption,
                        color = DRColors.CrispWhite,
                        maxLines = 1
                    )
                }
            }

            // 40-Tier Horizontal Track
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(ShopCatalog.deskPassTiers) { tierItem ->
                    DeskPassTierCard(
                        tier = tierItem,
                        unlocked = currentTier >= tierItem.tier,
                        claimed = profile.claimedPassTiers.contains(tierItem.tier),
                        isPremiumActive = profile.deskPassPremium,
                        onClaim = {
                            if (prefs.claimDeskPassTier(tierItem.tier)) {
                                var totalCoins = tierItem.freeCoins
                                var totalGems = tierItem.freeGems
                                if (profile.deskPassPremium) {
                                    totalCoins += tierItem.premiumCoins
                                    totalGems += tierItem.premiumGems
                                    tierItem.premiumCosmeticId?.let { id ->
                                        prefs.unlockCosmetic(id)
                                        if (id == "body_classic_toy") prefs.unlockBody(5)
                                    }
                                }
                                if (totalCoins > 0) prefs.addCoins(totalCoins)
                                if (totalGems > 0) prefs.addGems(totalGems)
                                statusText = "Claimed Tier ${tierItem.tier}: +$totalCoins Coins" +
                                    if (totalGems > 0) " & +$totalGems Gems!" else "!"
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeskPassTierCard(
    tier: DeskPassTier,
    unlocked: Boolean,
    claimed: Boolean,
    isPremiumActive: Boolean,
    onClaim: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(DRRadius.Card),
        color = DRColors.SlateCard,
        border = androidx.compose.foundation.BorderStroke(
            width = if (unlocked && !claimed) 2.dp else 1.dp,
            color = when {
                claimed -> DRColors.MintFinish
                unlocked -> DRColors.KartGold
                else -> DRColors.StrokeHairline
            }
        ),
        modifier = Modifier
            .width(172.dp)
            .fillMaxHeight()
            .testTag("desk_pass_tier_${tier.tier}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TIER ${tier.tier}",
                    style = DRType.CardTitle,
                    color = if (unlocked) DRColors.KartGold else DRColors.MutedSlate
                )
                Text(
                    text = "${tier.requiredXp} XP",
                    style = DRType.MicroTag,
                    color = DRColors.ElectricBlue
                )
            }

            // Free Track Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DRColors.ElevatedCard, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "FREE TRACK",
                    style = DRType.MicroTag,
                    color = DRColors.MintFinish
                )
                Text(
                    text = tier.freeRewardTitle,
                    style = DRType.Caption,
                    color = DRColors.CrispWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Premium Track Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DRColors.ElevatedCard, RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        if (isPremiumActive) DRColors.KartGold else DRColors.StrokeHairline,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "PREMIUM ★",
                    style = DRType.MicroTag,
                    color = DRColors.KartGold
                )
                Text(
                    text = tier.premiumRewardTitle,
                    style = DRType.Caption,
                    color = DRColors.CrispWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DRButton(
                text = when {
                    claimed -> "CLAIMED ✓"
                    unlocked -> "CLAIM"
                    else -> "LOCKED"
                },
                onClick = { if (unlocked && !claimed) onClaim() },
                enabled = unlocked && !claimed,
                variant = if (unlocked && !claimed) DRButtonVariant.PRIMARY else DRButtonVariant.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
                testTag = "btn_claim_tier_${tier.tier}"
            )
        }
    }
}

@Composable
fun CurrencyHeaderPills(
    coins: Int,
    gems: Int,
    onRestore: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(DRRadius.Pill),
            color = DRColors.ElevatedCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, DRColors.KartGold)
        ) {
            Text(
                text = "🪙 ${ShopCatalog.formatCount(coins)}",
                style = DRType.TabularTimer,
                color = DRColors.KartGold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(DRRadius.Pill),
            color = DRColors.ElevatedCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, DRColors.ElectricBlue)
        ) {
            Text(
                text = "💎 ${ShopCatalog.formatCount(gems)}",
                style = DRType.TabularTimer,
                color = DRColors.ElectricBlue,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        if (onRestore != null) {
            Surface(
                shape = RoundedCornerShape(DRRadius.Pill),
                color = DRColors.SlateCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, DRColors.StrokeHairline),
                modifier = Modifier
                    .height(36.dp)
                    .clickable(onClick = onRestore)
                    .testTag("btn_restore_purchases")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                    Text(
                        text = "RESTORE",
                        style = DRType.MicroTag,
                        color = DRColors.CrispWhite
                    )
                }
            }
        }
    }
}
