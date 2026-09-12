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
        return productIds != null
                && productIds.contains(PremiumManager.PREMIUM_PRODUCT_ID)
                && purchaseState == Purchase.PurchaseState.PURCHASED
                && acknowledged;
    }
}
