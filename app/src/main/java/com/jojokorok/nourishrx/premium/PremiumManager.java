package com.jojokorok.nourishrx.premium;

import android.content.Context;
import android.content.SharedPreferences;

public class PremiumManager {
    public static final String PREMIUM_PRODUCT_ID = "nourishrx_premium";
    public static final String PREMIUM_PRODUCT_NAME = "NourishRx Premium";
    public static final String PREMIUM_PURCHASE_MODEL = "One-time purchase";

    private static final String PREFS_NAME = "premium_access";
    private static final String KEY_CACHED_PREMIUM_ACTIVE = "cached_premium_active";
    private static final String KEY_CACHED_VERIFIED_AT = "cached_premium_verified_at";

    private final SharedPreferences preferences;

    public PremiumManager(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isPremiumActive() {
        return preferences.getBoolean(KEY_CACHED_PREMIUM_ACTIVE, false);
    }

    public boolean canUse(PremiumFeature feature) {
        if (feature.tier != PremiumTier.ONE_TIME_PREMIUM) {
            return false;
        }

        return isPremiumActive();
    }

    public String planLabel() {
        return isPremiumActive() ? "Premium" : "Free";
    }

    public String premiumProductLabel() {
        return PREMIUM_PRODUCT_NAME;
    }

    public String purchaseModelLabel() {
        return PREMIUM_PURCHASE_MODEL;
    }

    public String purchaseUnavailableMessage() {
        return PREMIUM_PRODUCT_NAME
                + " is planned as a one-time purchase through Google Play Billing. "
                + "Purchase handling is not connected in this build yet.";
    }

    public long premiumVerifiedAt() {
        return preferences.getLong(KEY_CACHED_VERIFIED_AT, 0);
    }

    // This cache should only be written after Play Billing or server-side purchase verification.
    public void cachePremiumEntitlement(boolean active, long verifiedAt) {
        preferences.edit()
                .putBoolean(KEY_CACHED_PREMIUM_ACTIVE, active)
                .putLong(KEY_CACHED_VERIFIED_AT, verifiedAt)
                .apply();
    }

    public void clearCachedEntitlement() {
        preferences.edit()
                .remove(KEY_CACHED_PREMIUM_ACTIVE)
                .remove(KEY_CACHED_VERIFIED_AT)
                .apply();
    }
}
