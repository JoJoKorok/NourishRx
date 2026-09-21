package com.jojokorok.nourishrx.premium;

import java.util.Objects;

/** Snapshot of the one-time Premium product currently reported by Google Play. */
public final class PremiumOfferState {
    public enum Status {
        CHECKING,
        AVAILABLE,
        UNAVAILABLE
    }

    private final Status status;
    private final String formattedPrice;

    private PremiumOfferState(Status status, String formattedPrice) {
        this.status = status;
        this.formattedPrice = formattedPrice;
    }

    public static PremiumOfferState checking() {
        return new PremiumOfferState(Status.CHECKING, "");
    }

    public static PremiumOfferState available(String formattedPrice) {
        return new PremiumOfferState(Status.AVAILABLE, formattedPrice);
    }

    public static PremiumOfferState unavailable() {
        return new PremiumOfferState(Status.UNAVAILABLE, "");
    }

    public Status getStatus() {
        return status;
    }

    public String getFormattedPrice() {
        return formattedPrice;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PremiumOfferState)) {
            return false;
        }
        PremiumOfferState that = (PremiumOfferState) other;
        return status == that.status && formattedPrice.equals(that.formattedPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, formattedPrice);
    }
}
