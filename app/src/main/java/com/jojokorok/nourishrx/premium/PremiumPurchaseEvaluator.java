package com.jojokorok.nourishrx.premium;

import com.android.billingclient.api.Purchase;

import java.util.Collection;

/** Applies the minimum local checks before a Play purchase can unlock Premium. */
public final class PremiumPurchaseEvaluator {
    private PremiumPurchaseEvaluator() {
    }

    public static boolean grantsPremium(
            Collection<String> productIds,
            int purchaseState,
            boolean acknowledged
    ) {
        return isCompletedPremiumPurchase(productIds, purchaseState) && acknowledged;
    }

    public static boolean requiresAcknowledgement(
            Collection<String> productIds,
            int purchaseState,
            boolean acknowledged
    ) {
        return isCompletedPremiumPurchase(productIds, purchaseState) && !acknowledged;
    }

    private static boolean isCompletedPremiumPurchase(
            Collection<String> productIds,
            int purchaseState
    ) {
        return productIds != null
                && productIds.contains(PremiumManager.PREMIUM_PRODUCT_ID)
                && purchaseState == Purchase.PurchaseState.PURCHASED;
    }
}
