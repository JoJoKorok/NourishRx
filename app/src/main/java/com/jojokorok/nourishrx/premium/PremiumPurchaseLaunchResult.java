package com.jojokorok.nourishrx.premium;

import com.android.billingclient.api.BillingClient;

public enum PremiumPurchaseLaunchResult {
    STARTED,
    ALREADY_OWNED,
    OFFER_UNAVAILABLE,
    BILLING_UNAVAILABLE,
    FAILED;

    public static PremiumPurchaseLaunchResult fromBillingResponse(int responseCode) {
        if (responseCode == BillingClient.BillingResponseCode.OK) {
            return STARTED;
        }
        if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            return ALREADY_OWNED;
        }
        if (responseCode == BillingClient.BillingResponseCode.ITEM_UNAVAILABLE) {
            return OFFER_UNAVAILABLE;
        }
        if (responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
                || responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED
                || responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE) {
            return BILLING_UNAVAILABLE;
        }
        return FAILED;
    }
}
