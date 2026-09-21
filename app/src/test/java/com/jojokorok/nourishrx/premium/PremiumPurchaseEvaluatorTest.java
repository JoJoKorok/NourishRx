package com.jojokorok.nourishrx.premium;

import com.android.billingclient.api.Purchase;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PremiumPurchaseEvaluatorTest {
    @Test
    public void acknowledgedPurchasedPremiumProductGrantsAccess() {
        assertTrue(PremiumPurchaseEvaluator.grantsPremium(
                Collections.singletonList(PremiumManager.PREMIUM_PRODUCT_ID),
                Purchase.PurchaseState.PURCHASED,
                true
        ));
    }

    @Test
    public void pendingPurchaseDoesNotGrantAccess() {
        assertFalse(PremiumPurchaseEvaluator.grantsPremium(
                Collections.singletonList(PremiumManager.PREMIUM_PRODUCT_ID),
                Purchase.PurchaseState.PENDING,
                true
        ));
    }

    @Test
    public void unacknowledgedPurchaseDoesNotGrantAccess() {
        assertFalse(PremiumPurchaseEvaluator.grantsPremium(
                Collections.singletonList(PremiumManager.PREMIUM_PRODUCT_ID),
                Purchase.PurchaseState.PURCHASED,
                false
        ));
    }

    @Test
    public void unrelatedProductDoesNotGrantAccess() {
        assertFalse(PremiumPurchaseEvaluator.grantsPremium(
                Collections.singletonList("another_product"),
                Purchase.PurchaseState.PURCHASED,
                true
        ));
    }

    @Test
    public void completedPremiumPurchaseRequiresAcknowledgement() {
        assertTrue(PremiumPurchaseEvaluator.requiresAcknowledgement(
                Collections.singletonList(PremiumManager.PREMIUM_PRODUCT_ID),
                Purchase.PurchaseState.PURCHASED,
                false
        ));
    }

    @Test
    public void pendingPurchaseDoesNotRequireAcknowledgement() {
        assertFalse(PremiumPurchaseEvaluator.requiresAcknowledgement(
                Collections.singletonList(PremiumManager.PREMIUM_PRODUCT_ID),
                Purchase.PurchaseState.PENDING,
                false
        ));
    }

    @Test
    public void unrelatedPurchaseDoesNotRequireAcknowledgement() {
        assertFalse(PremiumPurchaseEvaluator.requiresAcknowledgement(
                Collections.singletonList("another_product"),
                Purchase.PurchaseState.PURCHASED,
                false
        ));
    }
}
