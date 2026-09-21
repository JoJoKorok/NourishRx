package com.jojokorok.nourishrx.premium;

import com.android.billingclient.api.BillingClient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PremiumPurchaseLaunchResultTest {
    @Test
    public void mapsSuccessfulLaunch() {
        assertEquals(
                PremiumPurchaseLaunchResult.STARTED,
                PremiumPurchaseLaunchResult.fromBillingResponse(BillingClient.BillingResponseCode.OK)
        );
    }

    @Test
    public void mapsAlreadyOwnedPurchase() {
        assertEquals(
                PremiumPurchaseLaunchResult.ALREADY_OWNED,
                PremiumPurchaseLaunchResult.fromBillingResponse(
                        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED
                )
        );
    }

    @Test
    public void mapsUnavailableOffer() {
        assertEquals(
                PremiumPurchaseLaunchResult.OFFER_UNAVAILABLE,
                PremiumPurchaseLaunchResult.fromBillingResponse(
                        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE
                )
        );
    }

    @Test
    public void mapsUnavailableBillingService() {
        assertEquals(
                PremiumPurchaseLaunchResult.BILLING_UNAVAILABLE,
                PremiumPurchaseLaunchResult.fromBillingResponse(
                        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED
                )
        );
    }

    @Test
    public void mapsOtherFailures() {
        assertEquals(
                PremiumPurchaseLaunchResult.FAILED,
                PremiumPurchaseLaunchResult.fromBillingResponse(BillingClient.BillingResponseCode.ERROR)
        );
    }
}
