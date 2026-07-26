package com.jojokorok.nourishrx.premium;

public enum PremiumTier {
    ONE_TIME_PREMIUM("One-time Premium"),
    SYNC_SUBSCRIPTION("Future Sync Subscription");

    public final String label;

    PremiumTier(String label) {
        this.label = label;
    }
}
