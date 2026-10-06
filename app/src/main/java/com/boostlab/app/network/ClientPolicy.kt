package com.boostlab.app.network

import com.boostlab.app.model.PlanTier

data class TierPolicy(
    val adsEnabled: Boolean,
    val maxAutoCandidates: Int,
    val priorityRouting: Boolean,
)

data class ClientPolicy(
    val defaultTier: PlanTier,
    val free: TierPolicy,
    val premium: TierPolicy,
) {
    fun forTier(tier: PlanTier): TierPolicy = when (tier) {
        PlanTier.FREE -> free
        PlanTier.PREMIUM -> premium
    }
}
