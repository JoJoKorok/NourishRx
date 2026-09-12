package com.jojokorok.nourishrx.premium;

import android.content.Context;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import java.util.List;

/** Reconciles locally cached Premium access with purchases reported by Google Play. */
public final class PremiumBillingCoordinator implements PlayBillingClient.Listener, AutoCloseable {
    private final PremiumManager premiumManager;
    private final Runnable entitlementChanged;
    private final PlayBillingClient billingClient;

    public PremiumBillingCoordinator(
            Context context,
            PremiumManager premiumManager,
            Runnable entitlementChanged
    ) {
        this.premiumManager = premiumManager;
        this.entitlementChanged = entitlementChanged;
        billingClient = new PlayBillingClient(context, this);
    }

    public void start() {
        billingClient.connect();
    }

    public void refresh() {
        billingClient.refreshPurchases();
    }

    @Override
    public void onConnectionStateChanged(
            PlayBillingClient.ConnectionState state,
            BillingResult result
    ) {
        // Connection errors leave the last successfully reconciled entitlement intact.
    }

    @Override
    public void onPremiumProductDetails(BillingResult result, ProductDetails productDetails) {
        // Product details will be used by the purchase-screen commit.
    }

    @Override
    public void onPremiumPurchasesQueried(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            return;
        }

        boolean wasPremium = premiumManager.isPremiumActive();
        boolean premiumOwned = false;
        for (Purchase purchase : purchases) {
            if (PremiumPurchaseEvaluator.grantsPremium(
                    purchase.getProducts(),
                    purchase.getPurchaseState(),
                    purchase.isAcknowledged()
            )) {
                premiumOwned = true;
                break;
            }
        }

        premiumManager.cachePremiumEntitlement(premiumOwned, System.currentTimeMillis());
        if (wasPremium != premiumManager.isPremiumActive()) {
            entitlementChanged.run();
        }
    }

    @Override
    public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            billingClient.refreshPurchases();
        }
    }

    @Override
    public void close() {
        billingClient.close();
    }
}
